#pragma once
#include <JuceHeader.h>
#include "PluginProcessor.h"

class StageLinkSenderAudioProcessorEditor final : public juce::AudioProcessorEditor,
                                                   private juce::Timer
{
public:
    explicit StageLinkSenderAudioProcessorEditor(StageLinkSenderAudioProcessor&);
    ~StageLinkSenderAudioProcessorEditor() override = default;
    void paint(juce::Graphics&) override;
    void resized() override;

private:
    StageLinkSenderAudioProcessor& processor;
    juce::Label title;
    juce::Label hostLabel, portLabel, streamLabel, statusLabel, statsLabel;
    juce::TextEditor host;
    juce::TextEditor port;
    juce::TextEditor stream;
    juce::ToggleButton enabled { "Streaming enabled" };
    juce::TextButton apply { "Apply" };

    void timerCallback() override;
    void applySettings();
    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR(StageLinkSenderAudioProcessorEditor)
};
