package com.example.smart_lamp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorListener
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val lampIp = "http://192.168.1.15" // IP-ul ESP32-ului tău
    private val client = OkHttpClient()

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

    // Launcher pentru permisiunea de MediaProjection
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startVisualizerService(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupControls()
        setupModeSelector()
    }

    private fun setupModeSelector() {
        val modeSpinner = findViewById<Spinner>(R.id.modeSelector)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = adapter

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { // Ambient
                        stopVisualizerService()
                        sendToLamp("/setMode?val=0")
                        updateUIState(enabled = false)
                    }
                    1 -> { // Run All Animations
                        stopVisualizerService()
                        sendToLamp("/setAuto?val=1")
                        sendToLamp("/setMode?val=1")
                        updateUIState(enabled = false)
                    }
                    2 -> { // Pick Animation
                        stopVisualizerService()
                        sendToLamp("/setAuto?val=0")
                        sendToLamp("/setMode?val=1")
                        updateUIState(enabled = true)
                    }
                    3 -> { // Pick Color
                        stopVisualizerService()
                        sendToLamp("/setMode?val=0")
                        updateUIState(enabled = true)
                    }
                    4 -> { // Music Visualizer
                        sendToLamp("/setMode?val=2")
                        updateUIState(enabled = false)
                        // Cerem permisiunea pentru captură audio
                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateUIState(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        
        val colorPicker = findViewById<ColorPickerView>(R.id.colorPickerView)
        colorPicker.isEnabled = enabled
        colorPicker.alpha = alpha

        val animSpinner = findViewById<Spinner>(R.id.spinnerAnimations)
        animSpinner.isEnabled = enabled
        animSpinner.alpha = alpha
    }

    private fun setupControls() {
        findViewById<Button>(R.id.btnOn).setOnClickListener { sendToLamp("/setMode?val=0") }
        findViewById<Button>(R.id.btnOff).setOnClickListener { sendToLamp("/setBrightness?val=0") }

        val colorPickerView = findViewById<ColorPickerView>(R.id.colorPickerView)
        colorPickerView.setColorListener(ColorListener { color, fromUser ->
            if (fromUser && colorPickerView.isEnabled) {
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                sendToLamp("/setColor?r=$r&g=$g&b=$b")
            }
        })

        findViewById<SeekBar>(R.id.sbBrightness).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) sendToLamp("/setBrightness?val=$progress")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val spinner = findViewById<Spinner>(R.id.spinnerAnimations)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, animNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spinner.isEnabled) {
                    sendToLamp("/setAnimation?val=$position")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
            override fun onFailure(call: Call, e: IOException) { Log.e("SmartLamp", "Eroare: ${e.message}") }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
