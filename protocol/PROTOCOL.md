# StageLink Audio Protocol v1

Transport: UDP unicast over a dedicated LAN.

Audio format for v1:
- 48,000 Hz
- stereo
- signed PCM 16-bit little-endian payload
- 128 frames per packet (2.6667 ms)

Header is 28 bytes, big-endian/network byte order:

| Offset | Size | Field |
|---:|---:|---|
| 0 | 4 | Magic `STGL` (0x5354474C) |
| 4 | 1 | Version = 1 |
| 5 | 1 | Flags = 0 |
| 6 | 2 | Stream ID |
| 8 | 4 | Sequence number |
| 12 | 8 | Sender monotonic timestamp in microseconds |
| 20 | 2 | Frames |
| 22 | 1 | Channels |
| 23 | 1 | Format = 1 (PCM16LE) |
| 24 | 4 | Sample rate |

Payload starts at byte 28 and contains interleaved PCM16LE samples.

For 128 stereo frames the payload is 512 bytes, total datagram 540 bytes.

The receiver never requests retransmission: late/missing packets are concealed locally because retransmitted monitor audio is already stale.
