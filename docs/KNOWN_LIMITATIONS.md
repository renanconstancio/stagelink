# Known limitations of this MVP

- This is prototype source code, not a certified professional wireless IEM system.
- No encryption or pairing/authentication. Anyone on the trusted LAN who knows the port/format could inject packets.
- No FEC/redundant packet path.
- No automatic network discovery.
- No Wi-Fi roaming or multi-AP synchronization.
- No sample-rate conversion; sender requires 48 kHz.
- One stereo unicast stream per sender plug-in instance.
- `AudioTrack` low-latency behavior varies by Android device/USB DAC. A future Oboe/AAudio engine is the preferred route for tighter device-specific latency.
- The digital -1 dBFS ceiling is not a hearing-protection SPL limiter.
