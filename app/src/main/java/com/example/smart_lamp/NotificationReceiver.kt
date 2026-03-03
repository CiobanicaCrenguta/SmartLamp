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
        // The notification button sends "ACTION_OFF"
        if (intent?.action == "ACTION_OFF") {
            sendOffSignal()
            
            // Send a DIFFERENT action to the Activity to avoid an infinite loop
            val updateIntent = Intent("ACTION_UI_UPDATE_OFF")
            updateIntent.setPackage(context?.packageName)
            context?.sendBroadcast(updateIntent)
        }
    }

    private fun sendOffSignal() {
        val url = "$lampIp/setBrightness?val=0"
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NotificationReceiver", "Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
