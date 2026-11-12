#include "Emulator.h"

#include <android/log.h>
#include <unistd.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstring>

#include <mgba/core/cheats.h>
#include <mgba/core/config.h>
#include <mgba/core/log.h>
#include <mgba/core/serialize.h>
#include <mgba/gb/interface.h>
#include <mgba/gba/interface.h>
#include <mgba-util/vfs.h>

namespace {

constexpr const char* kTag = "aGBA";
constexpr unsigned kVideoStride = 256;
constexpr unsigned kVideoRows = 256;
constexpr uint32_t kOpaqueAlpha = 0xFF000000u;
constexpr size_t kOutputCapacity = 8192;
constexpr float kTiltScale = 3.0e8f;
constexpr float kGyroScale = 5.5e8f;
constexpr float kGravity = 9.80665f;

void logToAndroid(struct mLogger*, int category, enum mLogLevel level, const char* format, va_list args) {
    int priority;
    switch (level) {
    case mLOG_FATAL:
        priority = ANDROID_LOG_FATAL;
        break;
    case mLOG_ERROR:
        priority = ANDROID_LOG_ERROR;
        break;
    case mLOG_WARN:
        priority = ANDROID_LOG_WARN;
        break;
    default:
        return;
    }
    char message[512];
    vsnprintf(message, sizeof(message), format, args);
    __android_log_print(priority, kTag, "%s: %s", mLogCategoryName(category), message);
}

struct mLogger gLogger = {logToAndroid, nullptr};

int64_t nowNanos() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now().time_since_epoch()).count();
}

float clampf(float v, float lo, float hi) {
    return std::min(hi, std::max(lo, v));
}

}  // namespace

Emulator& Emulator::instance() {
    static Emulator emulator;
    return emulator;
}

Emulator::Emulator() {
    memset(&outputBuffer, 0, sizeof(outputBuffer));
    memset(&resampler, 0, sizeof(resampler));
    memset(&rewindContext, 0, sizeof(rewindContext));
    mLogSetDefaultLogger(&gLogger);
}

void Emulator::setOption(const std::string& key, const std::string& value) {
    options[key] = value;
}

int Emulator::intOption(const char* key, int fallback) const {
    auto it = options.find(key);
    if (it == options.end() || it->second.empty()) {
        return fallback;
    }
    return atoi(it->second.c_str());
}

std::string Emulator::stringOption(const char* key) const {
    auto it = options.find(key);
    return it == options.end() ? std::string() : it->second;
}

bool Emulator::isLoaded() const {
    return core != nullptr;
}

int Emulator::platform() const {
    if (!core) {
        return -1;
    }
    return core->platform(core) == mPLATFORM_GBA ? kPlatformGba : kPlatformGb;
}

int Emulator::load(int romFd, int patchFd, const std::string& savePath) {
    shutdown();

    struct VFile* rom = VFileFromFD(romFd);
    if (!rom) {
        close(romFd);
        if (patchFd >= 0) {
            close(patchFd);
        }
        return kRomInvalid;
    }
    core = mCoreFindVF(rom);
    if (!core) {
        rom->close(rom);
        if (patchFd >= 0) {
            close(patchFd);
        }
        return kRomInvalid;
    }

    mCoreInitConfig(core, "agba");
    if (!core->init(core)) {
        rom->close(rom);
        if (patchFd >= 0) {
            close(patchFd);
        }
        core = nullptr;
        return kCoreFailed;
    }

    videoBuffer.assign(static_cast<size_t>(kVideoStride) * kVideoRows, 0xFF000000u);
    frontBuffer.assign(static_cast<size_t>(kVideoStride) * kVideoRows * 4, 0);
    core->setVideoBuffer(core, videoBuffer.data(), kVideoStride);
    core->setAudioBufferSize(core, 2048);

    configureCore();

    if (!core->loadROM(core, rom)) {
        rom->close(rom);
        if (patchFd >= 0) {
            close(patchFd);
        }
        mCoreConfigDeinit(&core->config);
        core->deinit(core);
        core = nullptr;
        return kRomInvalid;
    }

    if (!savePath.empty()) {
        struct VFile* save = VFileOpen(savePath.c_str(), O_CREAT | O_RDWR);
        if (save) {
            core->loadSave(core, save);
        }
    }
    if (patchFd >= 0) {
        struct VFile* patch = VFileFromFD(patchFd);
        if (patch) {
            if (!core->loadPatch(core, patch)) {
                __android_log_print(ANDROID_LOG_WARN, kTag, "Patch rejected");
            }
            patch->close(patch);
        }
    }

    attachPeripherals();
    applyGbPalette(stringOption("gbPalette"));
    core->reset(core);

    mAudioBufferInit(&outputBuffer, kOutputCapacity, 2);
    mAudioResamplerInit(&resampler, mINTERPOLATOR_SINC);
    mAudioResamplerSetDestination(&resampler, &outputBuffer, AudioOutput::kSampleRate);
    audioScratch.assign(kOutputCapacity * 2, 0);
    audioPrepared = true;

    int rewindSeconds = intOption("rewindSeconds", 0);
    rewindActive = rewindSeconds > 0;
    rewindInterval = std::max(1, intOption("rewindInterval", 1));
    if (rewindActive) {
        size_t entries = static_cast<size_t>(rewindSeconds) * 60 / rewindInterval;
        mCoreRewindContextInit(&rewindContext, std::max<size_t>(entries, 1), false);
        rewindContext.rewindFrameCounter = 0;
    }
    rewindEmpty = false;

    frameId = 0;
    consumedFrameId = 0;
    frameWidth = 0;
    frameHeight = 0;
    keys = 0;
    fastForward = false;
    rewinding = false;
    rumble = 0.f;
    return kOk;
}

void Emulator::configureCore() {
    struct mCoreOptions opts = {};
    opts.useBios = intOption("useBios", 1) != 0;
    opts.skipBios = intOption("skipBios", 0) != 0;
    opts.frameskip = intOption("frameskip", 0);
    opts.volume = 0x100;
    opts.audioBuffers = 2048;
    opts.rewindEnable = false;

    const char* idle = "remove";
    std::string idleOption = stringOption("idleOptimization");
    if (idleOption == "ignore" || idleOption == "detect" || idleOption == "remove") {
        idle = idleOption.c_str();
    }
    mCoreConfigSetDefaultValue(&core->config, "idleOptimization", idle);
    mCoreConfigSetDefaultIntValue(&core->config, "sgb.borders", intOption("sgbBorders", 0));
    mCoreConfigSetDefaultIntValue(&core->config, "allowOpposingDirections", intOption("opposingDirections", 0));

    const char* biosKeys[][2] = {{"biosGba", "gba.bios"}, {"biosGb", "gb.bios"}, {"biosGbc", "gbc.bios"}};
    for (const auto& pair : biosKeys) {
        std::string path = stringOption(pair[0]);
        if (!path.empty()) {
            mCoreConfigSetDefaultValue(&core->config, pair[1], path.c_str());
        }
    }

    std::string model = stringOption("gbModel");
    if (!model.empty()) {
        mCoreConfigSetDefaultValue(&core->config, "gb.model", model.c_str());
        mCoreConfigSetDefaultValue(&core->config, "cgb.model", model.c_str());
    }

    mCoreConfigLoadDefaults(&core->config, &opts);
    mCoreLoadForeignConfig(core, &core->config);
}

void Emulator::attachPeripherals() {
    mRumbleIntegratorInit(&rumbleIntegrator);
    rumbleIntegrator.setRumble = onRumble;
    core->setPeripheral(core, mPERIPH_RUMBLE, &rumbleIntegrator);

    memset(&rotationSource, 0, sizeof(rotationSource));
    rotationSource.sample = onRotationSample;
    rotationSource.readTiltX = onTiltX;
    rotationSource.readTiltY = onTiltY;
    rotationSource.readGyroZ = onGyroZ;
    core->setPeripheral(core, mPERIPH_ROTATION, &rotationSource);

    if (core->platform(core) == mPLATFORM_GBA) {
        luminanceSource.sample = onLuxSample;
        luminanceSource.readLuminance = onLuxRead;
        core->setPeripheral(core, mPERIPH_GBA_LUMINANCE, &luminanceSource);
    }
}

void Emulator::start() {
    std::lock_guard<std::mutex> lock(controlMutex);
    if (!core || started) {
        return;
    }
    quit = false;
    paused = false;
    started = true;
    resyncClock = true;
    audio.setMinimumBuffer(intOption("audioBuffer", 2048));
    if (audio.open()) {
        audio.start();
    }
    worker = std::thread(&Emulator::loop, this);
}

void Emulator::shutdown() {
    {
        std::lock_guard<std::mutex> lock(controlMutex);
        quit = true;
        paused = false;
    }
    controlCv.notify_all();
    if (worker.joinable()) {
        worker.join();
    }
    started = false;
    audio.close();
    if (!core) {
        return;
    }
    if (rewindActive) {
        mCoreRewindContextDeinit(&rewindContext);
        rewindActive = false;
    }
    if (audioPrepared) {
        mAudioResamplerDeinit(&resampler);
        mAudioBufferDeinit(&outputBuffer);
        audioPrepared = false;
    }
    core->unloadROM(core);
    mCoreConfigDeinit(&core->config);
    core->deinit(core);
    core = nullptr;
    std::lock_guard<std::mutex> lock(controlMutex);
    quit = false;
    paused = true;
}

void Emulator::setPaused(bool value) {
    {
        std::lock_guard<std::mutex> lock(controlMutex);
        if (paused == value) {
            return;
        }
        paused = value;
        resyncClock = true;
    }
    if (value) {
        audio.pause();
    } else {
        audio.start();
    }
    controlCv.notify_all();
}

bool Emulator::isPaused() const {
    std::lock_guard<std::mutex> lock(controlMutex);
    return paused;
}

void Emulator::reset() {
    std::lock_guard<std::mutex> lock(coreMutex);
    if (core) {
        core->reset(core);
        if (rewindActive) {
            mCoreRewindContextDeinit(&rewindContext);
            mCoreRewindContextInit(&rewindContext, std::max<size_t>(1, static_cast<size_t>(intOption("rewindSeconds", 1)) * 60 / rewindInterval), false);
        }
    }
}

void Emulator::setKeys(uint32_t value) {
    keys = value;
}

void Emulator::setFastForward(bool enabled) {
    fastForward = enabled;
    resyncClock = true;
}

void Emulator::setRewinding(bool enabled) {
    rewinding = enabled;
    if (!enabled) {
        rewindEmpty = false;
    }
}

void Emulator::setSensors(float accelX, float accelY, float gyroZ) {
    tiltX = accelX;
    tiltY = accelY;
    gyro = gyroZ;
}

void Emulator::setLuxIndex(int index) {
    luxIndex = std::min(10, std::max(0, index));
    luxUseSensor = false;
}

void Emulator::setLuxSensor(float lux, bool useSensor) {
    luxSensor = lux;
    luxUseSensor = useSensor;
}

float Emulator::rumbleLevel() const {
    return rumble;
}

void Emulator::onRumble(struct mRumbleIntegrator*, float level) {
    instance().rumble = level;
}

void Emulator::onRotationSample(struct mRotationSource*) {}

int32_t Emulator::onTiltX(struct mRotationSource*) {
    Emulator& e = instance();
    float sensitivity = e.intOption("tiltSensitivity", 100) / 100.f;
    return static_cast<int32_t>(clampf(e.tiltX / kGravity * kTiltScale * sensitivity, -2.0e9f, 2.0e9f));
}

int32_t Emulator::onTiltY(struct mRotationSource*) {
    Emulator& e = instance();
    float sensitivity = e.intOption("tiltSensitivity", 100) / 100.f;
    return static_cast<int32_t>(clampf(-e.tiltY / kGravity * kTiltScale * sensitivity, -2.0e9f, 2.0e9f));
}

int32_t Emulator::onGyroZ(struct mRotationSource*) {
    Emulator& e = instance();
    float sensitivity = e.intOption("gyroSensitivity", 100) / 100.f;
    return static_cast<int32_t>(clampf(-e.gyro * kGyroScale * sensitivity, -2.0e9f, 2.0e9f));
}

void Emulator::onLuxSample(struct GBALuminanceSource*) {}

uint8_t Emulator::onLuxRead(struct GBALuminanceSource*) {
    Emulator& e = instance();
    int level;
    if (e.luxUseSensor) {
        level = static_cast<int>(cbrtf(std::max(0.f, e.luxSensor.load())) * 8.f);
        level = std::min(0xE9, level);
    } else {
        level = 0x16;
        int index = e.luxIndex;
        if (index > 0) {
            level += GBA_LUX_LEVELS[index - 1];
        }
    }
    return static_cast<uint8_t>(0xFF - level);
}

bool Emulator::saveState(const std::string& path) {
    std::lock_guard<std::mutex> lock(coreMutex);
    if (!core) {
        return false;
    }
    struct VFile* vf = VFileOpen(path.c_str(), O_CREAT | O_TRUNC | O_RDWR);
    if (!vf) {
        return false;
    }
    bool ok = mCoreSaveStateNamed(core, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    vf->close(vf);
    return ok;
}

bool Emulator::loadState(const std::string& path) {
    std::lock_guard<std::mutex> lock(coreMutex);
    if (!core) {
        return false;
    }
    struct VFile* vf = VFileOpen(path.c_str(), O_RDONLY);
    if (!vf) {
        return false;
    }
    bool ok = mCoreLoadStateNamed(core, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    vf->close(vf);
    if (ok && rewindActive) {
        mCoreRewindContextDeinit(&rewindContext);
        mCoreRewindContextInit(&rewindContext, std::max<size_t>(1, static_cast<size_t>(intOption("rewindSeconds", 1)) * 60 / rewindInterval), false);
    }
    return ok;
}

void Emulator::applyCheats(const std::vector<CheatEntry>& cheats) {
    std::lock_guard<std::mutex> lock(coreMutex);
    if (!core) {
        return;
    }
    struct mCheatDevice* device = core->cheatDevice(core);
    if (!device) {
        return;
    }
    mCheatDeviceClear(device);
    for (const CheatEntry& entry : cheats) {
        struct mCheatSet* set = device->createSet(device, entry.name.c_str());
        size_t start = 0;
        while (start <= entry.code.size()) {
            size_t end = entry.code.find('\n', start);
            if (end == std::string::npos) {
                end = entry.code.size();
            }
            std::string line = entry.code.substr(start, end - start);
            line.erase(std::remove(line.begin(), line.end(), '\r'), line.end());
            if (line.find_first_not_of(" \t") != std::string::npos) {
                mCheatAddLine(set, line.c_str(), 0);
            }
            start = end + 1;
        }
        set->enabled = entry.enabled;
        mCheatAddSet(device, set);
    }
}

std::vector<std::string> Emulator::gbPaletteNames() {
    std::vector<std::string> names;
    const struct GBColorPreset* presets = nullptr;
    size_t count = GBColorPresetList(&presets);
    for (size_t i = 0; i < count; ++i) {
        names.emplace_back(presets[i].name);
    }
    return names;
}

void Emulator::applyGbPalette(const std::string& name) {
    if (!core || name.empty() || core->platform(core) != mPLATFORM_GB) {
        return;
    }
    const struct GBColorPreset* presets = nullptr;
    size_t count = GBColorPresetList(&presets);
    for (size_t i = 0; i < count; ++i) {
        if (name != presets[i].name) {
            continue;
        }
        for (int c = 0; c < 12; ++c) {
            char key[16];
            snprintf(key, sizeof(key), "gb.pal[%d]", c);
            mCoreConfigSetUIntValue(&core->config, key, presets[i].colors[c] & 0xFFFFFF);
        }
        core->reloadConfigOption(core, "gb.pal", nullptr);
        return;
    }
}

int Emulator::readFrame(uint8_t* dst, size_t capacity, bool force) {
    std::lock_guard<std::mutex> lock(frameMutex);
    if (frameId == 0) {
        return 0;
    }
    size_t bytes = static_cast<size_t>(frameWidth) * frameHeight * 4;
    if (bytes > capacity) {
        return 0;
    }
    bool fresh = frameId != consumedFrameId;
    if (fresh || force) {
        memcpy(dst, frontBuffer.data(), bytes);
        consumedFrameId = frameId;
    }
    int packed = static_cast<int>((frameWidth << 16) | frameHeight);
    return (fresh || force) ? packed : -packed;
}

void Emulator::loop() {
    while (true) {
        {
            std::unique_lock<std::mutex> lock(controlMutex);
            controlCv.wait(lock, [this] { return quit || !paused; });
            if (quit) {
                break;
            }
        }
        bool ff = fastForward;
        bool rew = rewinding && rewindActive;
        bool skipFrame = rew && rewindEmpty;
        if (!skipFrame) {
            std::lock_guard<std::mutex> lock(coreMutex);
            runOneFrame(rew);
        }
        publishFrame();
        pushAudio(ff || rew);
        pace(ff || skipFrame);
    }
}

void Emulator::runOneFrame(bool rew) {
    core->setKeys(core, keys.load());
    core->runFrame(core);
    if (!rewindActive) {
        return;
    }
    if (rew) {
        if (!mCoreRewindRestore(&rewindContext, core, 1)) {
            rewindEmpty = true;
        }
        rewindContext.rewindFrameCounter = rewindInterval;
    } else {
        if (rewindContext.rewindFrameCounter == 0) {
            mCoreRewindAppend(&rewindContext, core);
            rewindContext.rewindFrameCounter = rewindInterval;
        }
        rewindContext.rewindFrameCounter--;
    }
}

void Emulator::publishFrame() {
    unsigned width = 0;
    unsigned height = 0;
    core->currentVideoSize(core, &width, &height);
    if (width == 0 || height == 0 || width > kVideoStride || height > kVideoRows) {
        return;
    }
    std::lock_guard<std::mutex> lock(frameMutex);
    uint32_t* out = reinterpret_cast<uint32_t*>(frontBuffer.data());
    for (unsigned y = 0; y < height; ++y) {
        const uint32_t* in = &videoBuffer[static_cast<size_t>(y) * kVideoStride];
        for (unsigned x = 0; x < width; ++x) {
            *out++ = in[x] | kOpaqueAlpha;
        }
    }
    frameWidth = width;
    frameHeight = height;
    ++frameId;
}

void Emulator::pushAudio(bool silent) {
    struct mAudioBuffer* input = core->getAudioBuffer(core);
    if (silent || !audio.isOpen()) {
        mAudioBufferClear(input);
        return;
    }
    mAudioResamplerSetSource(&resampler, input, core->audioSampleRate(core), true);
    mAudioResamplerProcess(&resampler);
    size_t available = mAudioBufferAvailable(&outputBuffer);
    if (available == 0) {
        return;
    }
    available = std::min(available, audioScratch.size() / 2);
    size_t frames = mAudioBufferRead(&outputBuffer, audioScratch.data(), available);
    int volume = intOption("mute", 0) ? 0 : std::min(100, std::max(0, intOption("volume", 100)));
    if (volume != 100) {
        for (size_t i = 0; i < frames * 2; ++i) {
            audioScratch[i] = static_cast<int16_t>(audioScratch[i] * volume / 100);
        }
    }
    audio.write(audioScratch.data(), frames);
}

void Emulator::pace(bool fastForwardActive) {
    bool audioPaced = audio.isOpen() && !fastForwardActive;
    if (audioPaced) {
        resyncClock = true;
        return;
    }
    int speed = intOption("fastForwardSpeed", 0);
    if (fastForwardActive && speed <= 0 && !(rewinding && rewindActive)) {
        resyncClock = true;
        return;
    }
    double divisor = fastForwardActive && speed > 0 && !(rewinding && rewindActive) ? speed : 1.0;
    double frameSeconds = static_cast<double>(core->frameCycles(core)) / core->frequency(core);
    int64_t period = static_cast<int64_t>(frameSeconds / divisor * 1e9);
    int64_t now = nowNanos();
    if (resyncClock || nextFrameNanos < now - 100000000LL) {
        nextFrameNanos = now;
        resyncClock = false;
    }
    nextFrameNanos += period;
    int64_t sleepFor = nextFrameNanos - now;
    if (sleepFor > 0) {
        std::this_thread::sleep_for(std::chrono::nanoseconds(sleepFor));
    }
}
