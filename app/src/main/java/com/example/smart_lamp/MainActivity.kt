package com.example.smart_lamp

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorListener
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val lampIp = "http://192.168.1.15"
    private val client = OkHttpClient()
    private val CHANNEL_ID = "LampControlChannel"

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

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startVisualizerService(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        setupControls()
        setupModeSelector()
        showPersistentNotification()
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
                    stopVisualizerService()
                    sendToLamp("/setMode?val=0")
                    animateUIState(enabled = false)
                }
                1 -> { // Run All Animations
                    stopVisualizerService()
                    sendToLamp("/setAuto?val=1")
                    sendToLamp("/setMode?val=1")
                    animateUIState(enabled = false)
                }
                2 -> { // Pick Animation
                    stopVisualizerService()
                    sendToLamp("/setAuto?val=0")
                    sendToLamp("/setMode?val=1")
                    animateUIState(enabled = true)
                }
                3 -> { // Pick Color
                    stopVisualizerService()
                    sendToLamp("/setMode?val=0")
                    animateUIState(enabled = true)
                }
                4 -> { // Music Visualizer
                    sendToLamp("/setMode?val=2")
                    animateUIState(enabled = false)
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
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

        listOf(colorCard, animationCard).forEach { view ->
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
        val btnOn = findViewById<MaterialButton>(R.id.btnOn)
        val btnOff = findViewById<MaterialButton>(R.id.btnOff)

        setButtonState(btnOn, btnOff, isOn = true)

        btnOn.setOnClickListener {
            playPowerAnimation(it)
            sendToLamp("/setMode?val=0")
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

        val intent = Intent(this, NotificationReceiver::class.java).apply {
            action = "ACTION_OFF"
        }
        val pendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentTitle("Smart Lamp Control")
            .setContentText("Tap to turn off the lamp")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Persistent
            .addAction(android.R.drawable.ic_lock_power_off, "TURN OFF", pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        with(NotificationManagerCompat.from(this)) {
            notify(10, builder.build())
        }
    }

    private fun startVisualizerService(resultCode: Int, data: Intent) {
        val intent = Intent(this, VisualizerService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        startForegroundService(intent)
    }

    private fun stopVisualizerService() {
        stopService(Intent(this, VisualizerService::class.java))
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
