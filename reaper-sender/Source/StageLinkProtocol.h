#pragma once
#include <JuceHeader.h>
#include <array>
#include <cstdint>

namespace stagelink
{
constexpr uint32_t kMagic = 0x5354474c; // STGL
constexpr uint8_t kVersion = 1;
constexpr uint8_t kFormatPcm16Le = 1;
constexpr int kSampleRate = 48000;
constexpr int kChannels = 2;
constexpr int kFramesPerPacket = 128;
constexpr int kHeaderBytes = 28;
constexpr int kPayloadBytes = kFramesPerPacket * kChannels * 2;
constexpr int kPacketBytes = kHeaderBytes + kPayloadBytes;

inline void putU16BE(uint8_t* p, uint16_t v)
{
    p[0] = static_cast<uint8_t>((v >> 8) & 0xff);
    p[1] = static_cast<uint8_t>(v & 0xff);
}
inline void putU32BE(uint8_t* p, uint32_t v)
{
    p[0] = static_cast<uint8_t>((v >> 24) & 0xff);
    p[1] = static_cast<uint8_t>((v >> 16) & 0xff);
    p[2] = static_cast<uint8_t>((v >> 8) & 0xff);
    p[3] = static_cast<uint8_t>(v & 0xff);
}
inline void putU64BE(uint8_t* p, uint64_t v)
{
    for (int i = 0; i < 8; ++i)
        p[i] = static_cast<uint8_t>((v >> (56 - 8 * i)) & 0xff);
}
inline void putI16LE(uint8_t* p, int16_t v)
{
    const auto u = static_cast<uint16_t>(v);
    p[0] = static_cast<uint8_t>(u & 0xff);
    p[1] = static_cast<uint8_t>((u >> 8) & 0xff);
}

inline int16_t floatToPcm16(float x)
{
    x = juce::jlimit(-1.0f, 0.999969f, x);
    return static_cast<int16_t>(std::lrint(x * 32768.0f));
}

inline uint64_t monotonicMicros()
{
    return static_cast<uint64_t>(juce::Time::getHighResolutionTicks()
        * 1000000.0 / juce::Time::getHighResolutionTicksPerSecond());
}
} // namespace stagelink
