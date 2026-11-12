#pragma once

#include <cstddef>
#include <cstdint>

struct AAudioStreamStruct;

class AudioOutput {
public:
    static constexpr int32_t kSampleRate = 48000;

    ~AudioOutput();

    void setMinimumBuffer(int32_t frames) { minimumBuffer = frames; }
    bool open();
    void close();
    void start();
    void pause();
    bool isOpen() const;
    void write(const int16_t* frames, size_t frameCount);

private:
    AAudioStreamStruct* stream = nullptr;
    int32_t minimumBuffer = 2048;
};
