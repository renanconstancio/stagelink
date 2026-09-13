# StageLink Android Receiver

Native Android/Kotlin receiver. No React Native is required for this MVP.

## Requirements

- Android Studio
- JDK 17
- Android SDK 35
- Physical Android device, API 26+
- Wired headphones or USB-C DAC

## Run

1. Open this folder in Android Studio.
2. Sync Gradle.
3. Run on a physical device.
4. Note the IPv4 address shown at the top.
5. Set that address in the StageLink Sender VST3 inside REAPER.
6. Match UDP port and stream ID.
7. Start receiver, then enable streaming in the VST3.

## Latency tuning

Start at 10 ms jitter buffer. If the LAN is stable, reduce toward 5 ms or 3 ms. If you hear clicks/dropouts, increase the buffer. The displayed `AudioTrack.underrunCount`, lost packets and jitter help separate phone-output problems from Wi-Fi problems.

This v1 uses `AudioTrack.PERFORMANCE_MODE_LOW_LATENCY`. If specific phones still have excessive output latency, the next engine should use Oboe/AAudio while keeping the same UDP protocol and UI.
