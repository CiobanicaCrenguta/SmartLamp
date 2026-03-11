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
import kotlin.math.sqrt

class VisualizerService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    private val client = OkHttpClient()
    private var lampIp = "http://192.168.1.15"

    private val channelId = "VisualizerServiceChannel"
    private val notificationId = 1

    // Throttling mechanism variables
    private var lastSendTime = 0L
    private val sendIntervalMs = 40L // ~25 FPS
    private val isRequestInProgress = AtomicBoolean(false)

    // Beat detection and Volume state
    private val rmsHistory = DoubleArray(43)
    private var historyIndex = 0
    private var lastBeatTime = 0L
    private var smoothedVol = 0.0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        val ipFromIntent = intent?.getStringExtra("LAMP_IP")
        if (ipFromIntent != null) lampIp = ipFromIntent

        if (resultCode == Activity.RESULT_OK && data != null) {
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Smart Lamp Visualizer")
                .setContentText("Dancing to your system audio...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
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
            val buffer = ShortArray(1024)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) processAudio(buffer, read)
            }
        }
        recordingThread?.start()
    }

    private fun processAudio(buffer: ShortArray, read: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSendTime < sendIntervalMs || isRequestInProgress.get()) return

        // 1. Calculare RMS (Root Mean Square) pentru volum
        var sumOfSquares = 0.0
        for (i in 0 until read) {
            sumOfSquares += buffer[i].toDouble() * buffer[i].toDouble()
        }
        val currentRMS = sqrt(sumOfSquares / read)

        // 2. Mentinere istoric circular pentru beat detection
        rmsHistory[historyIndex] = currentRMS
        historyIndex = (historyIndex + 1) % rmsHistory.size

        // 3. Calculare medie istoric
        var historySum = 0.0
        for (rms in rmsHistory) historySum += rms
        val avgHistory = historySum / rmsHistory.size

        // 4. Logica de Beat Detection
        var beat = 0
        if (currentRMS > avgHistory * 1.4 && currentRMS > 800 && (currentTime - lastBeatTime) >= 200) {
            beat = 1
            lastBeatTime = currentTime
        }

        // 5. Normalizare volum 0-255
        val normalizedRMS = (currentRMS / 32).coerceIn(0.0, 255.0)

        // 6. Smoothing 70/30
        smoothedVol = smoothedVol * 0.7 + normalizedRMS * 0.3

        lastSendTime = currentTime
        sendVisualizerData(smoothedVol.toInt(), beat)
    }

    private fun sendVisualizerData(v: Int, beat: Int) {
        isRequestInProgress.set(true)
        val url = "$lampIp/visualizer?v=$v&beat=$beat"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { isRequestInProgress.set(false) }
            override fun onResponse(call: Call, response: Response) { 
                response.close()
                isRequestInProgress.set(false) 
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, "Visualizer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        isRecording = false
        audioRecord?.apply { stop(); release() }
        mediaProjection?.stop()
        super.onDestroy()
    }
}
