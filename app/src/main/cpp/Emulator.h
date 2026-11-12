#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <mgba/core/core.h>
#include <mgba/core/interface.h>
#include <mgba/core/rewind.h>
#include <mgba/gba/interface.h>
#include <mgba-util/audio-buffer.h>
#include <mgba-util/audio-resampler.h>

#include "AudioOutput.h"

struct CheatEntry {
    std::string name;
    std::string code;
    bool enabled;
};

class Emulator {
public:
    enum LoadResult {
        kOk = 0,
        kRomInvalid = 1,
        kCoreFailed = 2,
    };

    enum Platform {
        kPlatformGba = 0,
        kPlatformGb = 1,
    };

    static Emulator& instance();

    void setOption(const std::string& key, const std::string& value);
    int load(int romFd, int patchFd, const std::string& savePath);
    void start();
    void shutdown();

    bool isLoaded() const;
    int platform() const;
    void setPaused(bool paused);
    bool isPaused() const;
    void reset();

    void setKeys(uint32_t keys);
    void setFastForward(bool enabled);
    void setRewinding(bool enabled);
    void setSensors(float accelX, float accelY, float gyroZ);
    void setLuxIndex(int index);
    void setLuxSensor(float lux, bool useSensor);
    float rumbleLevel() const;

    bool saveState(const std::string& path);
    bool loadState(const std::string& path);
    void applyCheats(const std::vector<CheatEntry>& cheats);
    void applyGbPalette(const std::string& name);

    int readFrame(uint8_t* dst, size_t capacity, bool force);

    static std::vector<std::string> gbPaletteNames();

private:
    Emulator();
    Emulator(const Emulator&) = delete;
    Emulator& operator=(const Emulator&) = delete;

    void loop();
    void runOneFrame(bool rewinding);
    void publishFrame();
    void pushAudio(bool silent);
    void pace(bool fastForward);
    void configureCore();
    void attachPeripherals();
    int intOption(const char* key, int fallback) const;
    std::string stringOption(const char* key) const;

    static void onRumble(struct mRumbleIntegrator* integrator, float level);
    static void onRotationSample(struct mRotationSource* source);
    static int32_t onTiltX(struct mRotationSource* source);
    static int32_t onTiltY(struct mRotationSource* source);
    static int32_t onGyroZ(struct mRotationSource* source);
    static void onLuxSample(struct GBALuminanceSource* source);
    static uint8_t onLuxRead(struct GBALuminanceSource* source);

    struct mCore* core = nullptr;
    std::map<std::string, std::string> options;

    std::mutex coreMutex;
    mutable std::mutex controlMutex;
    std::condition_variable controlCv;
    std::thread worker;
    bool quit = false;
    bool paused = true;
    bool started = false;

    std::atomic<uint32_t> keys{0};
    std::atomic<bool> fastForward{false};
    std::atomic<bool> rewinding{false};
    std::atomic<float> tiltX{0.f};
    std::atomic<float> tiltY{0.f};
    std::atomic<float> gyro{0.f};
    std::atomic<int> luxIndex{0};
    std::atomic<float> luxSensor{0.f};
    std::atomic<bool> luxUseSensor{false};
    std::atomic<float> rumble{0.f};

    std::vector<mColor> videoBuffer;
    std::vector<uint8_t> frontBuffer;
    std::mutex frameMutex;
    unsigned frameWidth = 0;
    unsigned frameHeight = 0;
    uint64_t frameId = 0;
    uint64_t consumedFrameId = 0;

    AudioOutput audio;
    struct mAudioBuffer outputBuffer;
    struct mAudioResampler resampler;
    std::vector<int16_t> audioScratch;
    bool audioPrepared = false;

    struct mCoreRewindContext rewindContext;
    bool rewindActive = false;
    bool rewindEmpty = false;
    int rewindInterval = 1;

    struct mRumbleIntegrator rumbleIntegrator;
    struct mRotationSource rotationSource;
    struct GBALuminanceSource luminanceSource;

    int64_t nextFrameNanos = 0;
    bool resyncClock = true;
};
