package com.stagelink.receiver.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object StageLinkProtocol {
    const val MAGIC = 0x5354474C
    const val VERSION = 1
    const val FORMAT_PCM16_LE = 1
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
    const val FRAMES_PER_PACKET = 128
    const val HEADER_BYTES = 28
    const val SAMPLES_PER_PACKET = FRAMES_PER_PACKET * CHANNELS
    const val PAYLOAD_BYTES = SAMPLES_PER_PACKET * 2
    const val PACKET_BYTES = HEADER_BYTES + PAYLOAD_BYTES
}

data class StageLinkPacket(
    val streamId: Int,
    val sequence: Long,
    val senderTimestampUs: Long,
    val frames: Int,
    val channels: Int,
    val sampleRate: Int,
    val samples: ShortArray
) {
    companion object {
        fun parse(data: ByteArray, length: Int): StageLinkPacket? {
            if (length < StageLinkProtocol.HEADER_BYTES) return null
            val h = ByteBuffer.wrap(data, 0, StageLinkProtocol.HEADER_BYTES)
                .order(ByteOrder.BIG_ENDIAN)

            if (h.int != StageLinkProtocol.MAGIC) return null
            if ((h.get().toInt() and 0xff) != StageLinkProtocol.VERSION) return null
            h.get() // flags
            val streamId = h.short.toInt() and 0xffff
            val sequence = h.int.toLong() and 0xffffffffL
            val timestamp = h.long
            val frames = h.short.toInt() and 0xffff
            val channels = h.get().toInt() and 0xff
            val format = h.get().toInt() and 0xff
            val sampleRate = h.int

            if (format != StageLinkProtocol.FORMAT_PCM16_LE ||
                frames != StageLinkProtocol.FRAMES_PER_PACKET ||
                channels != StageLinkProtocol.CHANNELS ||
                sampleRate != StageLinkProtocol.SAMPLE_RATE) return null

            val expected = StageLinkProtocol.HEADER_BYTES + frames * channels * 2
            if (length < expected) return null

            val samples = ShortArray(frames * channels)
            val payload = ByteBuffer.wrap(data, StageLinkProtocol.HEADER_BYTES, samples.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
            for (i in samples.indices) samples[i] = payload.short

            return StageLinkPacket(streamId, sequence, timestamp, frames, channels, sampleRate, samples)
        }
    }
}
