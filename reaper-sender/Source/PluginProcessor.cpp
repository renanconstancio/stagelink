#include "PluginProcessor.h"
#include "PluginEditor.h"
#include "StageLinkProtocol.h"

StageLinkSenderAudioProcessor::StageLinkSenderAudioProcessor()
    : AudioProcessor(BusesProperties()
        .withInput("Input", juce::AudioChannelSet::stereo(), true)
        .withOutput("Output", juce::AudioChannelSet::stereo(), true)),
      juce::Thread("StageLink network sender")
{
    startThread(juce::Thread::Priority::high);
}

StageLinkSenderAudioProcessor::~StageLinkSenderAudioProcessor()
{
    signalThreadShouldExit();
    notify();
    stopThread(2000);
}

void StageLinkSenderAudioProcessor::prepareToPlay(double sr, int)
{
    sampleRateOk.store(std::abs(sr - 48000.0) < 1.0);
    fifo.reset();
}

void StageLinkSenderAudioProcessor::releaseResources() {}

bool StageLinkSenderAudioProcessor::isBusesLayoutSupported(const BusesLayout& layouts) const
{
    return layouts.getMainInputChannelSet() == juce::AudioChannelSet::stereo()
        && layouts.getMainOutputChannelSet() == juce::AudioChannelSet::stereo();
}

void StageLinkSenderAudioProcessor::processBlock(juce::AudioBuffer<float>& buffer, juce::MidiBuffer&)
{
    juce::ScopedNoDenormals noDenormals;
    // Pass-through plug-in: never modifies the REAPER bus.
    if (streaming.load(std::memory_order_relaxed) && sampleRateOk.load(std::memory_order_relaxed))
    {
        writeFramesToRing(buffer);
        notify();
    }
}

void StageLinkSenderAudioProcessor::writeFramesToRing(const juce::AudioBuffer<float>& buffer)
{
    const int n = buffer.getNumSamples();
    int start1, size1, start2, size2;
    fifo.prepareToWrite(n, start1, size1, start2, size2);
    const int written = size1 + size2;
    if (written < n)
        droppedFrames.fetch_add(static_cast<uint64_t>(n - written), std::memory_order_relaxed);

    auto copySegment = [&](int ringStart, int count, int srcStart)
    {
        if (count <= 0) return;
        const float* l = buffer.getReadPointer(0, srcStart);
        const float* r = buffer.getNumChannels() > 1 ? buffer.getReadPointer(1, srcStart) : l;
        for (int i = 0; i < count; ++i)
        {
            ring[(ringStart + i) * 2]     = l[i];
            ring[(ringStart + i) * 2 + 1] = r[i];
        }
    };

    copySegment(start1, size1, 0);
    copySegment(start2, size2, size1);
    fifo.finishedWrite(written);
}

bool StageLinkSenderAudioProcessor::readPacketFrames(std::array<float, 256>& out)
{
    int start1, size1, start2, size2;
    fifo.prepareToRead(stagelink::kFramesPerPacket, start1, size1, start2, size2);
    if (size1 + size2 < stagelink::kFramesPerPacket)
        return false;

    int dst = 0;
    auto copySegment = [&](int ringStart, int count)
    {
        for (int i = 0; i < count; ++i)
        {
            out[dst++] = ring[(ringStart + i) * 2];
            out[dst++] = ring[(ringStart + i) * 2 + 1];
        }
    };
    copySegment(start1, size1);
    copySegment(start2, size2);
    fifo.finishedRead(stagelink::kFramesPerPacket);
    return true;
}

void StageLinkSenderAudioProcessor::run()
{
    juce::DatagramSocket socket(false);
    std::array<float, 256> audio {};
    std::array<uint8_t, stagelink::kPacketBytes> packet {};
    uint32_t sequence = 0;

    while (!threadShouldExit())
    {
        if (!streaming.load() || !sampleRateOk.load())
        {
            wait(20);
            continue;
        }

        if (!readPacketFrames(audio))
        {
            wait(2);
            continue;
        }

        juce::String host;
        {
            const juce::ScopedLock sl(settingsLock);
            host = destinationHost;
        }
        const auto port = destinationPort.load();
        const auto sid = static_cast<uint16_t>(juce::jlimit(1, 65535, streamId.load()));

        auto* p = packet.data();
        stagelink::putU32BE(p + 0, stagelink::kMagic);
        p[4] = stagelink::kVersion;
        p[5] = 0;
        stagelink::putU16BE(p + 6, sid);
        stagelink::putU32BE(p + 8, sequence++);
        stagelink::putU64BE(p + 12, stagelink::monotonicMicros());
        stagelink::putU16BE(p + 20, stagelink::kFramesPerPacket);
        p[22] = stagelink::kChannels;
        p[23] = stagelink::kFormatPcm16Le;
        stagelink::putU32BE(p + 24, stagelink::kSampleRate);

        for (int i = 0; i < static_cast<int>(audio.size()); ++i)
            stagelink::putI16LE(p + stagelink::kHeaderBytes + i * 2, stagelink::floatToPcm16(audio[static_cast<size_t>(i)]));

        const int result = socket.write(host, port, packet.data(), static_cast<int>(packet.size()));
        if (result == static_cast<int>(packet.size()))
            sentPackets.fetch_add(1, std::memory_order_relaxed);
    }
}

void StageLinkSenderAudioProcessor::setDestination(const juce::String& host, int port, int sid)
{
    {
        const juce::ScopedLock sl(settingsLock);
        destinationHost = host.trim();
    }
    destinationPort.store(juce::jlimit(1, 65535, port));
    streamId.store(juce::jlimit(1, 65535, sid));
}

void StageLinkSenderAudioProcessor::setStreaming(bool enabled)
{
    if (enabled)
        fifo.reset();
    streaming.store(enabled);
    notify();
}

juce::String StageLinkSenderAudioProcessor::getDestinationHost() const
{
    const juce::ScopedLock sl(settingsLock);
    return destinationHost;
}

void StageLinkSenderAudioProcessor::getStateInformation(juce::MemoryBlock& destData)
{
    juce::ValueTree state("StageLink");
    state.setProperty("host", getDestinationHost(), nullptr);
    state.setProperty("port", destinationPort.load(), nullptr);
    state.setProperty("streamId", streamId.load(), nullptr);
    state.setProperty("streaming", streaming.load(), nullptr);
    if (auto xml = state.createXml())
        copyXmlToBinary(*xml, destData);
}

void StageLinkSenderAudioProcessor::setStateInformation(const void* data, int size)
{
    if (auto xml = getXmlFromBinary(data, size))
    {
        auto state = juce::ValueTree::fromXml(*xml);
        if (state.hasType("StageLink"))
        {
            setDestination(state.getProperty("host", "192.168.1.100").toString(),
                           static_cast<int>(state.getProperty("port", 47321)),
                           static_cast<int>(state.getProperty("streamId", 1)));
            setStreaming(static_cast<bool>(state.getProperty("streaming", false)));
        }
    }
}

juce::AudioProcessorEditor* StageLinkSenderAudioProcessor::createEditor()
{
    return new StageLinkSenderAudioProcessorEditor(*this);
}

juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
    return new StageLinkSenderAudioProcessor();
}
