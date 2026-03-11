package com.example.smart_lamp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import okhttp3.*
import java.io.IOException

class NotificationReceiver : BroadcastReceiver() {
    private val client = OkHttpClient()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "ACTION_OFF") {
            val lampIp = intent.getStringExtra("LAMP_IP") ?: "http://192.168.1.15"
            sendOffSignal(lampIp)
            
            // Notificăm activitatea să actualizeze UI-ul
            val updateIntent = Intent("ACTION_UI_UPDATE_OFF")
            updateIntent.setPackage(context?.packageName)
            context?.sendBroadcast(updateIntent)
        }
    }

    private fun sendOffSignal(lampIp: String) {
        val url = "$lampIp/setBrightness?val=0"
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NotificationReceiver", "Error sending OFF to $url: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("NotificationReceiver", "Successfully sent OFF to $url")
                response.close()
            }
        })
    }
}
