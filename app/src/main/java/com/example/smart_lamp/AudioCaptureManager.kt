package com.example.smart_lamp

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import kotlin.math.sqrt

class AudioCaptureManager {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)

    private var audioRecord: AudioRecord? = null
    private var sender: VisualizerSender? = null
    
    @Volatile
    private var running = false
    private var rollingAverage = 0f

    @SuppressLint("MissingPermission")
    fun start(espIp: String, mediaProjection: MediaProjection) {
        val s = VisualizerSender()
        s.start(espIp)
        sender = s

        // Configurare pentru captură sunet sistem (Media Projection)
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(audioEncoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioRecord?.startRecording()
        running = true

        Thread {
            val buffer = ShortArray(1024)
            while (running) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // 1. Compute RMS Volume
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += (buffer[i] * buffer[i]).toDouble()
                    }
                    val rms = sqrt(sum / read).toFloat()
                    val volume = (rms / 32).toInt().coerceIn(0, 255)

                    // 2. Compute Beat Detection
                    val beat = rms > rollingAverage * 1.4f && rms > 60
                    rollingAverage = rollingAverage * 0.9f + rms * 0.1f

                    // 3. Compute Hue from frequency (split buffer)
                    val half = read / 2
                    var bassSum = 0.0
                    for (i in 0 until half) bassSum += (buffer[i] * buffer[i]).toDouble()
                    val bassEnergy = sqrt(bassSum / half).toFloat().coerceIn(0f, 255f)

                    var trebleSum = 0.0
                    for (i in half until read) trebleSum += (buffer[i] * buffer[i]).toDouble()
                    val trebleEnergy = sqrt(trebleSum / (read - half)).toFloat().coerceIn(0f, 255f)

                    val hue = if (bassEnergy > trebleEnergy) {
                        (bassEnergy * 60 / 255).toInt()
                    } else {
                        (100 + trebleEnergy * 60 / 255).toInt()
                    }

                    s.send(volume, beat, hue)
                }
                Thread.sleep(25)
            }
        }.start()
    }

    fun stop() {
        running = false
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            sender?.stop()
        }
    }
}
