# StageLink MVP

StageLink is a proof-of-concept low-latency stage-monitor link:

`REAPER -> StageLink Sender VST3 -> Ethernet/Wi-Fi LAN -> Android -> wired headphones/USB-C DAC`

This repository contains two independent projects:

- `reaper-sender/`: JUCE VST3 sender for REAPER (Windows/macOS buildable; intended first for Windows).
- `android-receiver/`: native Android Studio/Kotlin receiver using `DatagramSocket` + low-latency `AudioTrack`.
- `protocol/`: shared wire-format specification.

## Important safety / scope

This is an engineering MVP, not certified IEM hardware. Do not rely on it as the only monitoring path in a safety-critical show. Begin with low headphone volume. The receiver applies a digital ceiling and fades on discontinuities, but downstream DAC/headphone amplifiers can still produce unsafe SPL.

For best results:

1. Run the REAPER machine at **48 kHz**.
2. Connect the REAPER computer to the access point using **Ethernet**.
3. Use a dedicated 5/6 GHz Wi-Fi network.
4. Disable battery optimization for the Android app during tests.
5. Use wired headphones or a USB-C DAC, not Bluetooth.
6. Keep the phone on the same subnet as the REAPER PC.

## Quick start

### 1. Android receiver

Open `android-receiver/` in Android Studio, let Gradle sync, then run on a physical Android device.

The app shows the phone's IPv4 address. Enter the UDP listen port (default `47321`) and stream ID (default `1`) and tap **Start receiver**.

### 2. REAPER sender

Build `reaper-sender/` using CMake. The build fetches JUCE 9.0.1 by default. JUCE 9.0.1 is available under AGPLv3 or a JUCE commercial license; choose licensing appropriate for your distribution.

Example on Windows with Visual Studio 2022/2026 developer tools:

```powershell
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
```

If you use a newer Visual Studio generator, replace the generator string accordingly.

The built VST3 is under the build output tree. Copy/install it to your VST3 location and rescan REAPER.

Insert **StageLink Sender** on a monitor bus, set **Destination IP** to the phone IP shown by the app, leave port `47321`, stream `1`, and enable streaming.

### 3. REAPER audio settings

Use a 48 kHz project/device sample rate. Start with a 64- or 128-sample ASIO buffer if the interface is stable.

## What is implemented

- VST3 receives the bus audio without changing the REAPER signal path.
- Audio thread writes stereo samples into a lock-free JUCE `AbstractFifo` ring buffer.
- Dedicated network thread packetizes 128 frames at a time.
- UDP PCM16LE packets with stream ID, sequence and timestamps.
- Android UDP receiver on a high-priority thread.
- Ordered jitter queue keyed by sequence number.
- Configurable startup jitter target (3-40 ms).
- Packet-loss/late-packet counters.
- Missing-packet concealment using a short ramp toward silence.
- Android low-latency `AudioTrack` path.
- Receiver volume, mute, digital peak ceiling, fade-in on start.
- Live stats: packets, lost, late, jitter, underruns, queue depth.

## Deliberate v1 constraints

- 48 kHz only. A production release should add high-quality sample-rate conversion or negotiate the sample rate.
- Stereo only.
- Unicast only; one VST3 instance/destination per receiver. This makes initial troubleshooting predictable.
- No encryption/authentication yet. Use an isolated trusted stage LAN.
- No automatic discovery yet. Phone IP is entered in the plug-in.
- Android is the first receiver platform.

## Next production milestones

1. mDNS/NSD discovery and pairing PIN/QR.
2. Authenticated session control and optional packet encryption.
3. Adaptive jitter buffer with slow shrink / fast growth.
4. Better packet-loss concealment and optional parity/FEC.
5. One sender service feeding many phone destinations without one plug-in per musician.
6. Per-device latency calibration.
7. Oboe/AAudio native engine for Android devices where `AudioTrack` cannot reach the target latency.
8. Swift/Core Audio iOS receiver.
9. Automated round-trip latency test and soak-test tools.

See `protocol/PROTOCOL.md` for the exact packet format.
