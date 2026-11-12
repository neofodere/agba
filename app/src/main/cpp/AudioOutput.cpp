#include "AudioOutput.h"

#include <aaudio/AAudio.h>

#include <algorithm>

AudioOutput::~AudioOutput() {
    close();
}

bool AudioOutput::open() {
    if (stream) {
        return true;
    }
    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        return false;
    }
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, kSampleRate);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStream* opened = nullptr;
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &opened);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK) {
        return false;
    }
    int32_t burst = AAudioStream_getFramesPerBurst(opened);
    AAudioStream_setBufferSizeInFrames(opened, std::max<int32_t>(burst * 2, minimumBuffer));
    stream = reinterpret_cast<AAudioStreamStruct*>(opened);
    return true;
}

void AudioOutput::close() {
    if (!stream) {
        return;
    }
    auto* s = reinterpret_cast<AAudioStream*>(stream);
    AAudioStream_requestStop(s);
    AAudioStream_close(s);
    stream = nullptr;
}

void AudioOutput::start() {
    if (stream) {
        AAudioStream_requestStart(reinterpret_cast<AAudioStream*>(stream));
    }
}

void AudioOutput::pause() {
    if (stream) {
        AAudioStream_requestPause(reinterpret_cast<AAudioStream*>(stream));
    }
}

bool AudioOutput::isOpen() const {
    return stream != nullptr;
}

void AudioOutput::write(const int16_t* frames, size_t frameCount) {
    if (!stream || frameCount == 0) {
        return;
    }
    constexpr int64_t kTimeoutNanos = 100LL * 1000 * 1000;
    AAudioStream_write(reinterpret_cast<AAudioStream*>(stream), frames, static_cast<int32_t>(frameCount), kTimeoutNanos);
}
