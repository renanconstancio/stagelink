#pragma once
#include <JuceHeader.h>
#include <atomic>
#include <array>

class StageLinkSenderAudioProcessor final : public juce::AudioProcessor,
                                            private juce::Thread
{
public:
    StageLinkSenderAudioProcessor();
    ~StageLinkSenderAudioProcessor() override;

    void prepareToPlay(double sampleRate, int samplesPerBlock) override;
    void releaseResources() override;
    bool isBusesLayoutSupported(const BusesLayout& layouts) const override;
    void processBlock(juce::AudioBuffer<float>&, juce::MidiBuffer&) override;

    juce::AudioProcessorEditor* createEditor() override;
    bool hasEditor() const override { return true; }

    const juce::String getName() const override { return "StageLink Sender"; }
    bool acceptsMidi() const override { return false; }
    bool producesMidi() const override { return false; }
    bool isMidiEffect() const override { return false; }
    double getTailLengthSeconds() const override { return 0.0; }
    int getNumPrograms() override { return 1; }
    int getCurrentProgram() override { return 0; }
    void setCurrentProgram(int) override {}
    const juce::String getProgramName(int) override { return {}; }
    void changeProgramName(int, const juce::String&) override {}
    void getStateInformation(juce::MemoryBlock&) override;
    void setStateInformation(const void*, int) override;

    void setDestination(const juce::String& host, int port, int streamId);
    void setStreaming(bool enabled);
    bool getStreaming() const noexcept { return streaming.load(); }
    juce::String getDestinationHost() const;
    int getDestinationPort() const noexcept { return destinationPort.load(); }
    int getStreamId() const noexcept { return streamId.load(); }
    uint64_t getSentPackets() const noexcept { return sentPackets.load(); }
    uint64_t getDroppedFrames() const noexcept { return droppedFrames.load(); }
    bool sampleRateIsSupported() const noexcept { return sampleRateOk.load(); }

    // Auto-discovery diagnostics. The current UI does not need to use these yet,
    // but they are useful when we add a discovery status indicator later.
    juce::String getLastDiscoveredClient() const;
    uint64_t getDiscoveryCount() const noexcept { return discoveryCount.load(); }

private:
    static constexpr int ringCapacityFrames = 48000 * 2;
    static constexpr int discoveryPort = 47320;

    // REAPER's Web Browser Interface port is not exposed to a generic VST3.
    // For StageLink V1 we standardise it on 8010. Later this can become a
    // plug-in setting (or be supplied by a ReaScript helper).
    static constexpr int reaperWebPort = 8010;

    juce::AbstractFifo fifo { ringCapacityFrames };
    std::array<float, ringCapacityFrames * 2> ring {};

    mutable juce::CriticalSection settingsLock;
    juce::String destinationHost { "192.168.1.100" };
    juce::String lastDiscoveredClient;

    std::atomic<int> destinationPort { 47321 };
    std::atomic<int> streamId { 1 };
    std::atomic<bool> streaming { false };
    std::atomic<bool> sampleRateOk { true };
    std::atomic<uint64_t> sentPackets { 0 };
    std::atomic<uint64_t> droppedFrames { 0 };
    std::atomic<uint64_t> discoveryCount { 0 };

    void run() override;
    void handleDiscovery(juce::DatagramSocket& discoverySocket);
    bool readPacketFrames(std::array<float, 256>& interleaved);
    void writeFramesToRing(const juce::AudioBuffer<float>& buffer);

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR(StageLinkSenderAudioProcessor)
};
