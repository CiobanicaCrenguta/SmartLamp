package com.example.smart_lamp

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorListener
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var lampIp: String
    private val client = OkHttpClient()
    private val CHANNEL_ID = "LampControlChannel"

    private lateinit var btnOn: MaterialButton
    private lateinit var btnOff: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            fetchLampStatus()
            handler.postDelayed(this, 3000)
        }
    }

    private val animNames = arrayOf(
        "Spiral", "Fire", "Rain", "Heart", "Plasma", "Noise",
        "Snake", "Twinkle", "Ripple", "Meteor", "ColorWave",
        "Pacifica", "Matrix", "Pulse"
    )

    private val modes = arrayOf(
        "Ambient",
        "Run All Animations",
        "Pick Animation",
        "Pick Color",
        "Music Visualizer"
    )

    private val offReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_UI_UPDATE_OFF") {
                runOnUiThread {
                    animateUIState(enabled = false)
                    setButtonState(btnOn, btnOff, isOn = false)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lampIp = intent.getStringExtra("LAMP_IP") ?: "http://192.168.1.15"

        createNotificationChannel()
        setupControls()
        setupModeSelector()
        showPersistentNotification()

        val filter = IntentFilter("ACTION_UI_UPDATE_OFF")
        ContextCompat.registerReceiver(
            this,
            offReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStart() {
        super.onStart()
        handler.post(pollRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(offReceiver)
    }

    private fun fetchLampStatus() {
        val request = Request.Builder().url("$lampIp/").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SmartLamp", "Lampa offline: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val json = JSONObject(body)
                        val statusMode = json.getInt("mode")
                        val statusBrightness = json.getInt("brightness")
                        val statusAuto = json.getInt("auto")

                        runOnUiThread {
                            updateUIFromStatus(statusMode, statusBrightness, statusAuto)
                        }
                    } catch (e: Exception) {
                        Log.e("SmartLamp", "Eroare parsare JSON: ${e.message}")
                    }
                }
                response.close()
            }
        })
    }

    private fun updateUIFromStatus(mode: Int, brightness: Int, auto: Int) {
        val isOn = brightness > 0
        setButtonState(btnOn, btnOff, isOn)

        val modeText = when {
            mode == 0 && isOn -> modes[0] // Ambient
            mode == 1 && auto == 1 -> modes[1] // Run All
            mode == 1 && auto == 0 -> modes[2] // Pick Animation
            mode == 0 && !isOn -> modes[3] // Pick Color
            mode == 2 -> modes[4] // Music Visualizer
            else -> modes[0]
        }

        val modeSelector = findViewById<AutoCompleteTextView>(R.id.modeSelector)
        if (modeSelector.text.toString() != modeText) {
            modeSelector.setText(modeText, false)
            animateUIState(enabled = (mode == 1 && auto == 0) || (mode == 0 && isOn))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showPersistentNotification()
        }
    }

    private fun setupModeSelector() {
        val modeSelector = findViewById<AutoCompleteTextView>(R.id.modeSelector)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, modes)
        modeSelector.setAdapter(adapter)

        modeSelector.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> { // Ambient
                    sendToLamp("/setMode?val=0")
                    animateUIState(enabled = false)
                }
                1 -> { // Run All Animations
                    sendToLamp("/setAuto?val=1")
                    sendToLamp("/setMode?val=1")
                    animateUIState(enabled = false)
                }
                2 -> { // Pick Animation
                    sendToLamp("/setAuto?val=0")
                    sendToLamp("/setMode?val=1")
                    animateUIState(enabled = true)
                }
                3 -> { // Pick Color
                    sendToLamp("/setMode?val=0")
                    animateUIState(enabled = true)
                }
                4 -> { // Music Visualizer
                    val intent = Intent(this, VisualizerActivity::class.java)
                    intent.putExtra("LAMP_IP", lampIp)
                    startActivity(intent)
                }
            }
        }
    }

    private fun animateUIState(enabled: Boolean) {
        val targetAlpha = if (enabled) 1.0f else 0.4f
        val targetScale = if (enabled) 1.0f else 0.96f
        val duration = 400L
        val interpolator = AccelerateDecelerateInterpolator()

        val colorCard = findViewById<View>(R.id.colorCard)
        val animationCard = findViewById<View>(R.id.animationCard)

        listOfNotNull(colorCard, animationCard).forEach { view ->
            view.animate()
                .alpha(targetAlpha)
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
        }

        findViewById<ColorPickerView>(R.id.colorPickerView).isEnabled = enabled
        findViewById<AutoCompleteTextView>(R.id.spinnerAnimations).isEnabled = enabled
        findViewById<Slider>(R.id.sbBrightness).isEnabled = enabled
    }

    private fun setupControls() {
        btnOn = findViewById(R.id.btnOn)
        btnOff = findViewById(R.id.btnOff)

        setButtonState(btnOn, btnOff, isOn = true)

        btnOn.setOnClickListener {
            playPowerAnimation(it)
            sendToLamp("/setMode?val=0")
            sendToLamp("/setBrightness?val=255")
            animateUIState(enabled = true)
            setButtonState(btnOn, btnOff, isOn = true)
        }

        btnOff.setOnClickListener {
            playPowerAnimation(it)
            sendToLamp("/setBrightness?val=0")
            animateUIState(enabled = false)
            setButtonState(btnOn, btnOff, isOn = false)
        }

        val colorPickerView = findViewById<ColorPickerView>(R.id.colorPickerView)
        colorPickerView.setColorListener(ColorListener { color, fromUser ->
            if (fromUser && colorPickerView.isEnabled) {
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                sendToLamp("/setColor?r=$r&g=$g&b=$b")
            }
        })

        findViewById<Slider>(R.id.sbBrightness).addOnChangeListener { _, value, fromUser ->
            if (fromUser) sendToLamp("/setBrightness?val=${value.toInt()}")
        }

        val animSelector = findViewById<AutoCompleteTextView>(R.id.spinnerAnimations)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, animNames)
        animSelector.setAdapter(adapter)
        animSelector.setOnItemClickListener { _, _, position, _ ->
            if (animSelector.isEnabled) {
                sendToLamp("/setAnimation?val=$position")
            }
        }
    }

    private fun setButtonState(btnOn: MaterialButton, btnOff: MaterialButton, isOn: Boolean) {
        val primaryColor = MaterialColors.getColor(btnOn, com.google.android.material.R.attr.colorPrimary)
        val containerColor = MaterialColors.getColor(btnOn, com.google.android.material.R.attr.colorSecondaryContainer)
        val onPrimaryColor = MaterialColors.getColor(btnOn, com.google.android.material.R.attr.colorOnPrimary)
        val onContainerColor = MaterialColors.getColor(btnOn, com.google.android.material.R.attr.colorOnSecondaryContainer)

        if (isOn) {
            btnOn.backgroundTintList = ColorStateList.valueOf(primaryColor)
            btnOn.setTextColor(onPrimaryColor)
            btnOn.iconTint = ColorStateList.valueOf(onPrimaryColor)

            btnOff.backgroundTintList = ColorStateList.valueOf(containerColor)
            btnOff.setTextColor(onContainerColor)
            btnOff.iconTint = ColorStateList.valueOf(onContainerColor)
        } else {
            btnOn.backgroundTintList = ColorStateList.valueOf(containerColor)
            btnOn.setTextColor(onContainerColor)
            btnOn.iconTint = ColorStateList.valueOf(onContainerColor)

            btnOff.backgroundTintList = ColorStateList.valueOf(primaryColor)
            btnOff.setTextColor(onPrimaryColor)
            btnOff.iconTint = ColorStateList.valueOf(onPrimaryColor)
        }
    }

    private fun playPowerAnimation(view: View) {
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lamp Control"
            val descriptionText = "Persistent notification for lamp control"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showPersistentNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return
            }
        }

        // IMPORTANT: Trimitem IP-ul actual al lămpii către Receiver
        val intent = Intent(this, NotificationReceiver::class.java).apply {
            action = "ACTION_OFF"
            putExtra("LAMP_IP", lampIp)
        }
        val pendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentTitle("Smart Lamp is ON")
            .setContentText("Tap to turn off")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_lock_power_off, "TURN OFF", pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(10, builder.build())
            }
        }
    }

    private fun sendToLamp(path: String) {
        val url = "$lampIp$path"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.e("SmartLamp", "Error: ${e.message}") }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
