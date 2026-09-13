package com.stagelink.receiver.audio

data class ReceiverStats(
    val packetsReceived: Long = 0,
    val packetsLost: Long = 0,
    val latePackets: Long = 0,
    val invalidPackets: Long = 0,
    val audioUnderruns: Int = 0,
    val queuePackets: Int = 0,
    val jitterMs: Double = 0.0,
    val running: Boolean = false
)
