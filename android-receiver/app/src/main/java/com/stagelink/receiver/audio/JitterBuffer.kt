package com.stagelink.receiver.audio

import com.stagelink.receiver.protocol.StageLinkPacket
import java.util.TreeMap
import kotlin.math.abs

class JitterBuffer(private val maxPackets: Int = 64) {
    private val packets = TreeMap<Long, StageLinkPacket>()
    private var expectedSequence: Long? = null
    private var previousTransitUs: Long? = null
    private var jitterUs = 0.0
    var latePackets: Long = 0
        private set

    @Synchronized
    fun clear() {
        packets.clear()
        expectedSequence = null
        previousTransitUs = null
        jitterUs = 0.0
        latePackets = 0
    }

    @Synchronized
    fun offer(packet: StageLinkPacket, arrivalUs: Long) {
        expectedSequence?.let { expected ->
            if (unsignedLessThan(packet.sequence, expected)) {
                latePackets++
                return
            }
        }

        if (packets.size >= maxPackets) packets.pollFirstEntry()
        packets.putIfAbsent(packet.sequence, packet)

        // RFC3550-style smoothed inter-arrival jitter. Sender/receiver clocks do not
        // need to be synchronized; changes in transit time are what matter.
        val transit = arrivalUs - packet.senderTimestampUs
        previousTransitUs?.let { prev ->
            val d = abs((transit - prev).toDouble())
            jitterUs += (d - jitterUs) / 16.0
        }
        previousTransitUs = transit
    }

    @Synchronized
    fun startIfNeeded(): Long? {
        if (expectedSequence == null && packets.isNotEmpty())
            expectedSequence = packets.firstKey()
        return expectedSequence
    }

    @Synchronized
    fun pollExpected(): StageLinkPacket? {
        val seq = expectedSequence ?: return null
        val p = packets.remove(seq)
        expectedSequence = (seq + 1) and 0xffffffffL
        return p
    }

    @Synchronized
    fun size(): Int = packets.size

    @Synchronized
    fun jitterMs(): Double = jitterUs / 1000.0

    private fun unsignedLessThan(a: Long, b: Long): Boolean {
        val diff = (a - b) and 0xffffffffL
        return diff > 0x80000000L
    }
}
