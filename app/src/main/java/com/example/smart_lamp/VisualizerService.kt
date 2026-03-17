package com.example.smart_lamp

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VisualizerService : Service() {

    private var mediaProjection: MediaProjection? = null
    private val audioCaptureManager = AudioCaptureManager()

    private val channelId = "VisualizerServiceChannel"
    private val notificationId = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        val lampIp = intent?.getStringExtra("LAMP_IP") ?: ""

        if (resultCode == Activity.RESULT_OK && data != null) {
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Smart Lamp Visualizer")
                .setContentText("UDP streaming active...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()

            startForeground(notificationId, notification)

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = projectionManager.getMediaProjection(resultCode, data)
            
            if (mp != null) {
                mediaProjection = mp
                // Transmitem și obiectul MediaProjection către manager pentru captură sistem
                audioCaptureManager.start(lampIp, mp)
            } else {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, "Visualizer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        audioCaptureManager.stop()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
