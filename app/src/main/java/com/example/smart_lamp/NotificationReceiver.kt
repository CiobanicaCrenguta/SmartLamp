package com.example.smart_lamp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import okhttp3.*
import java.io.IOException

class NotificationReceiver : BroadcastReceiver() {
    private val lampIp = "http://192.168.1.15"
    private val client = OkHttpClient()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "ACTION_OFF") {
            sendOffSignal()
        }
    }

    private fun sendOffSignal() {
        val url = "$lampIp/setBrightness?val=0"
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NotificationReceiver", "Error sending off signal: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                Log.d("NotificationReceiver", "Lamp turned off via notification")
            }
        })
    }
}
