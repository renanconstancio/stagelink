# Test plan

## Functional smoke test

1. Set REAPER/audio interface to 48 kHz.
2. Put a metronome/click on a stereo bus.
3. Insert StageLink Sender on that bus.
4. Connect PC by Ethernet to the access point.
5. Connect Android to the dedicated Wi-Fi.
6. Start Android receiver at stream 1 / port 47321.
7. Enter the phone IPv4 in the VST3 and enable streaming.
8. Verify clean audio, increasing packet counter, no/low packet loss.

## Buffer sweep

Run at startup buffers 20, 10, 8, 5 and 3 ms. Record:
- audible dropouts
- packet loss
- late packets
- AudioTrack underruns
- jitter

Do not select the smallest buffer merely because it works for one minute. A stage profile needs headroom.

## End-to-end latency measurement

Route a click/impulse to StageLink. Physically/electrically route the Android DAC/headphone output back into a spare interface input and record it in REAPER beside the original click. Measure the sample offset:

`latency_ms = sample_offset / 48000 * 1000`

Repeat at least 30 times and report median, p95 and maximum.

## Soak test

For each candidate phone/router profile:
- 2 hours continuous playback
- screen on and screen off
- phone charging and not charging
- musician moving around expected stage area
- 1, 2, 4 and 8 receivers

A production release should also test RF congestion, AP channel changes, reconnects, airplane-mode transitions, incoming notifications/calls, thermal throttling and USB-C DAC reconnects.
