package com.stagelink.receiver.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import com.stagelink.receiver.protocol.StageLinkPacket
import com.stagelink.receiver.protocol.StageLinkProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class StageAudioReceiver {
    private val running = AtomicBoolean(false)
    private val jitter = JitterBuffer()
    private val packetCount = AtomicLong(0)
    private val invalidCount = AtomicLong(0)
    private val lostCount = AtomicLong(0)
    private val volume = AtomicReference(0.70f)
    private val mute = AtomicBoolean(false)

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var receiveThread: Thread? = null
    @Volatile private var playbackThread: Thread? = null
    @Volatile private var wantedStreamId = 1
    @Volatile private var startupBufferPackets = 4
    @Volatile private var startedPlayback = false

    fun start(port: Int, streamId: Int, startupBufferMs: Int) {
        stop()
        packetCount.set(0)
        invalidCount.set(0)
        lostCount.set(0)
        jitter.clear()
        wantedStreamId = streamId.coerceIn(1, 65535)
        startupBufferPackets = max(2, ((startupBufferMs / (1000.0 * StageLinkProtocol.FRAMES_PER_PACKET / StageLinkProtocol.SAMPLE_RATE))).roundToInt())
        startedPlayback = false
        running.set(true)

        createAudioTrack()
        receiveThread = thread(name = "StageLink-UDP", priority = Thread.MAX_PRIORITY) { receiveLoop(port) }
        playbackThread = thread(name = "StageLink-Audio", priority = Thread.MAX_PRIORITY) { playbackLoop() }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
        receiveThread?.join(500)
        playbackThread?.join(500)
        receiveThread = null
        playbackThread = null
        audioTrack?.runCatching { pause(); flush(); stop(); release() }
        audioTrack = null
        jitter.clear()
    }

    fun setVolume(value: Float) { volume.set(value.coerceIn(0f, 1f)) }
    fun setMute(value: Boolean) { mute.set(value) }

    fun stats(): ReceiverStats = ReceiverStats(
        packetsReceived = packetCount.get(),
        packetsLost = lostCount.get(),
        latePackets = jitter.latePackets,
        invalidPackets = invalidCount.get(),
        audioUnderruns = if (android.os.Build.VERSION.SDK_INT >= 24) audioTrack?.underrunCount ?: 0 else 0,
        queuePackets = jitter.size(),
        jitterMs = jitter.jitterMs(),
        running = running.get()
    )

    private fun createAudioTrack() {
        val minBytes = AudioTrack.getMinBufferSize(
            StageLinkProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Hardware buffer is intentionally not tiny in v1; network latency is controlled
        // by the jitter buffer while AudioTrack gets enough room to avoid underruns.
        val requestedBytes = max(minBytes, StageLinkProtocol.PAYLOAD_BYTES * 4)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(StageLinkProtocol.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(requestedBytes)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build().also {
                it.setVolume(1f)
                it.play()
            }
    }

    private fun receiveLoop(port: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val localSocket = DatagramSocket(port).also {
            it.soTimeout = 250
            it.receiveBufferSize = 256 * 1024
            socket = it
        }
        val buf = ByteArray(2048)

        try {
            while (running.get()) {
                val datagram = DatagramPacket(buf, buf.size)
                try {
                    localSocket.receive(datagram)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val parsed = StageLinkPacket.parse(datagram.data, datagram.length)
                if (parsed == null || parsed.streamId != wantedStreamId) {
                    invalidCount.incrementAndGet()
                    continue
                }
                packetCount.incrementAndGet()
                val arrivalUs = SystemClock.elapsedRealtimeNanos() / 1000L
                jitter.offer(parsed, arrivalUs)
            }
        } catch (_: Exception) {
            if (running.get()) invalidCount.incrementAndGet()
        } finally {
            localSocket.close()
        }
    }

    private fun playbackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val silence = ShortArray(StageLinkProtocol.SAMPLES_PER_PACKET)
        var previous = ShortArray(StageLinkProtocol.SAMPLES_PER_PACKET)
        var fadeGain = 0f

        while (running.get()) {
            if (!startedPlayback) {
                if (jitter.size() < startupBufferPackets) {
                    SystemClock.sleep(1)
                    continue
                }
                jitter.startIfNeeded()
                startedPlayback = true
            }

            val packet = jitter.pollExpected()
            val samples = if (packet != null) {
                packet.samples
            } else {
                lostCount.incrementAndGet()
                concealLoss(previous, silence)
            }

            val processed = ShortArray(samples.size)
            val target = if (mute.get()) 0f else volume.get()
            // Smooth start/reconnect gain to avoid sudden bursts.
            fadeGain += (target - fadeGain) * 0.12f
            applySafetyGain(samples, processed, fadeGain)

            val track = audioTrack ?: break
            var offset = 0
            while (offset < processed.size && running.get()) {
                val n = track.write(processed, offset, processed.size - offset, AudioTrack.WRITE_BLOCKING)
                if (n <= 0) break
                offset += n
            }
            previous = processed
        }
    }

    private fun concealLoss(previous: ShortArray, out: ShortArray): ShortArray {
        // 2.67 ms ramp toward silence instead of repeating a stale packet.
        for (i in previous.indices) {
            val frame = i / StageLinkProtocol.CHANNELS
            val gain = 1f - frame.toFloat() / StageLinkProtocol.FRAMES_PER_PACKET.toFloat()
            out[i] = (previous[i] * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun applySafetyGain(input: ShortArray, output: ShortArray, gain: Float) {
        // Digital ceiling at ~ -1 dBFS. This is NOT an SPL limiter; physical headphone
        // output still needs a safe listening level.
        val ceiling = 29203 // 32767 * 10^(-1/20)
        for (i in input.indices) {
            val x = (input[i] * gain).roundToInt()
            output[i] = min(ceiling, max(-ceiling, x)).toShort()
        }
    }
}
