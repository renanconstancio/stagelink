#include "PluginEditor.h"

StageLinkSenderAudioProcessorEditor::StageLinkSenderAudioProcessorEditor(StageLinkSenderAudioProcessor& p)
    : AudioProcessorEditor(&p), processor(p)
{
    title.setText("StageLink Sender", juce::dontSendNotification);
    title.setFont(juce::FontOptions(24.0f, juce::Font::bold));
    hostLabel.setText("Destination IP", juce::dontSendNotification);
    portLabel.setText("UDP port", juce::dontSendNotification);
    streamLabel.setText("Stream ID", juce::dontSendNotification);

    host.setText(processor.getDestinationHost());
    port.setText(juce::String(processor.getDestinationPort()));
    stream.setText(juce::String(processor.getStreamId()));
    enabled.setToggleState(processor.getStreaming(), juce::dontSendNotification);

    for (auto* c : std::initializer_list<juce::Component*>{ &title, &hostLabel, &portLabel, &streamLabel,
                                                            &statusLabel, &statsLabel, &host, &port, &stream,
                                                            &enabled, &apply })
        addAndMakeVisible(c);

    apply.onClick = [this] { applySettings(); };
    enabled.onClick = [this] { applySettings(); };
    setSize(460, 330);
    startTimerHz(4);
}

void StageLinkSenderAudioProcessorEditor::paint(juce::Graphics& g)
{
    g.fillAll(getLookAndFeel().findColour(juce::ResizableWindow::backgroundColourId));
}

void StageLinkSenderAudioProcessorEditor::resized()
{
    auto r = getLocalBounds().reduced(20);
    title.setBounds(r.removeFromTop(36));
    r.removeFromTop(12);

    auto row = [&](juce::Label& label, juce::Component& editor)
    {
        auto x = r.removeFromTop(36);
        label.setBounds(x.removeFromLeft(130));
        editor.setBounds(x);
        r.removeFromTop(6);
    };
    row(hostLabel, host);
    row(portLabel, port);
    row(streamLabel, stream);
    enabled.setBounds(r.removeFromTop(32));
    apply.setBounds(r.removeFromTop(36).removeFromLeft(120));
    r.removeFromTop(10);
    statusLabel.setBounds(r.removeFromTop(26));
    statsLabel.setBounds(r.removeFromTop(26));
}

void StageLinkSenderAudioProcessorEditor::applySettings()
{
    processor.setDestination(host.getText(), port.getText().getIntValue(), stream.getText().getIntValue());
    processor.setStreaming(enabled.getToggleState());
}

void StageLinkSenderAudioProcessorEditor::timerCallback()
{
    if (!processor.sampleRateIsSupported())
        statusLabel.setText("ERROR: REAPER/device must run at 48 kHz for StageLink v1", juce::dontSendNotification);
    else
        statusLabel.setText(processor.getStreaming() ? "Streaming" : "Stopped", juce::dontSendNotification);

    statsLabel.setText("Packets sent: " + juce::String(processor.getSentPackets())
        + "   Ring drops: " + juce::String(processor.getDroppedFrames()), juce::dontSendNotification);
}
