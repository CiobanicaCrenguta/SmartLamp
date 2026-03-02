package com.example.smart_lamp

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class VisualizerService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    private val client = OkHttpClient()
    private val lampIp = "http://192.168.1.15"

    private val channelId = "VisualizerServiceChannel"
    private val notificationId = 1

    // Throttling mechanism variables
    private var lastSendTime = 0L
    private val sendIntervalMs = 50L // 20 updates per second
    private val isRequestInProgress = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == Activity.RESULT_OK && data != null) {
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Smart Lamp Visualizer")
                .setContentText("Capturing system audio...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()

            startForeground(notificationId, notification)

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            startAudioCapture()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(audioFormat)
            .build()

        audioRecord?.startRecording()
        isRecording = true
        
        recordingThread = Thread {
            val buffer = ShortArray(2048) // Increased buffer size for better analysis
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    processAudio(buffer, read)
                }
            }
        }
        recordingThread?.start()
    }

    private fun processAudio(buffer: ShortArray, read: Int) {
        val currentTime = System.currentTimeMillis()
        
        // Throttling: Check if 50ms have passed AND no request is currently in progress
        if (currentTime - lastSendTime < sendIntervalMs || isRequestInProgress.get()) {
            return // Skip this frame to keep sync and avoid lag
        }

        // Simple frequency bands estimation (Simulation without FFT for efficiency)
        // Bass: low frequency components
        // Mid: middle components
        // Treble: high components
        
        var bassSum = 0L
        var midSum = 0L
        var trebleSum = 0L
        
        // Very basic "pseudo-FFT" by analyzing zero crossings or slopes could be done here, 
        // but for now, let's calculate RMS and split it into dummy bands for the ESP visualizer
        var totalSum = 0L
        for (i in 0 until read) {
            totalSum += abs(buffer[i].toInt())
        }
        val avgAmplitude = (totalSum / read).toInt()
        
        // Map amplitude to dummy bands (b, m, t) for ESP compatibility
        val b = (avgAmplitude / 64).coerceIn(0, 255)
        val m = (avgAmplitude / 128).coerceIn(0, 255)
        val t = (avgAmplitude / 256).coerceIn(0, 255)

        if (b > 5) { // Noise gate
            lastSendTime = currentTime
            sendVisualizerData(b, m, t)
        }
    }

    private fun sendVisualizerData(b: Int, m: Int, t: Int) {
        isRequestInProgress.set(true)
        
        val url = "$lampIp/visualizer?b=$b&m=$m&t=$t"
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                isRequestInProgress.set(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                isRequestInProgress.set(false)
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId, "Visualizer Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
