# Architecture

## Sender

The VST3 is a transparent stereo effect. `processBlock()` copies frames into a preallocated interleaved float ring buffer using JUCE `AbstractFifo`. It performs no network I/O, allocation, locking, logging, or file access in the realtime audio callback.

A dedicated high-priority JUCE thread pulls exactly 128 frames, converts to PCM16LE, writes the 28-byte StageLink header, and sends one UDP datagram to the configured Android device.

## Receiver

The Android app uses two long-lived worker threads:

- UDP receive thread: validates packets and inserts them into a sequence-keyed jitter queue.
- Audio playback thread: waits for the configured startup depth, advances one sequence per audio packet, conceals missing data, applies smooth gain + digital peak ceiling, and writes blocking PCM to low-latency `AudioTrack`.

The UI thread never handles PCM audio.

## Latency budget

At 48 kHz / 128 frames, each network packet represents 2.667 ms. Total user-perceived latency includes:

- ASIO/interface input buffer
- REAPER processing and plug-in delay compensation
- sender packetization (0..2.667 ms)
- Wi-Fi transport/jitter
- receiver startup/jitter buffer
- Android AudioTrack/hardware output buffer
- DAC/headphone path

The app-reported jitter is not the same as end-to-end latency. End-to-end latency must be measured with an acoustic/electrical loopback test.
