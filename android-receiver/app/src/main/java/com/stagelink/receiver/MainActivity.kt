package com.stagelink.receiver

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.stagelink.receiver.audio.StageAudioReceiver
import com.stagelink.receiver.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val receiver = StageAudioReceiver()
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val updateStats = object : Runnable {
        override fun run() {
            val s = receiver.stats()
            binding.statusText.text = if (s.running) "Receiver running" else "Stopped"
            binding.statsText.text = buildString {
                appendLine("Packets : ${s.packetsReceived}")
                appendLine("Lost    : ${s.packetsLost}")
                appendLine("Late    : ${s.latePackets}")
                appendLine("Invalid : ${s.invalidPackets}")
                appendLine("Jitter  : %.2f ms".format(s.jitterMs))
                appendLine("Queue   : ${s.queuePackets} packets")
                append("Underrun: ${s.audioUnderruns}")
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ipText.text = "Phone IP: ${findIpv4Address() ?: "not found"}"
        binding.bufferText.text = "${binding.bufferSeek.progress + 3} ms"

        binding.bufferSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.bufferText.text = "${progress + 3} ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                receiver.setVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.muteCheck.setOnCheckedChangeListener { _, isChecked -> receiver.setMute(isChecked) }

        binding.startButton.setOnClickListener {
            if (!running) {
                val port = binding.portEdit.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: 47321
                val stream = binding.streamEdit.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: 1
                val bufferMs = binding.bufferSeek.progress + 3
                receiver.setVolume(binding.volumeSeek.progress / 100f)
                receiver.setMute(binding.muteCheck.isChecked)
                receiver.start(port, stream, bufferMs)
                running = true
                binding.startButton.text = "Stop receiver"
                binding.portEdit.isEnabled = false
                binding.streamEdit.isEnabled = false
                binding.bufferSeek.isEnabled = false
            } else {
                stopReceiver()
            }
        }

        handler.post(updateStats)
    }

    private fun stopReceiver() {
        receiver.stop()
        running = false
        binding.startButton.text = "Start receiver"
        binding.portEdit.isEnabled = true
        binding.streamEdit.isEnabled = true
        binding.bufferSeek.isEnabled = true
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        receiver.stop()
        super.onDestroy()
    }

    private fun findIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
