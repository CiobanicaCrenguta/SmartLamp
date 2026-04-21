package com.example.smart_lamp

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color.HSVToColor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smart_lamp.ui.theme.Smart_LampTheme
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import kotlin.math.*

class MainActivity : ComponentActivity() {

    private lateinit var lampIp: String
    private val client = OkHttpClient()
    private val CHANNEL_ID = "LampControlChannel"

    private var brightnessState = mutableStateOf(120f)
    private var isLampOn = mutableStateOf(true)
    private var selectedMode = mutableStateOf("Ambient")
    private var selectedAnimation = mutableStateOf("Spiral")
    private var uiEnabled = mutableStateOf(true)

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
                isLampOn.value = false
                uiEnabled.value = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lampIp = intent.getStringExtra("LAMP_IP") ?: "http://192.168.1.15"

        createNotificationChannel()
        showPersistentNotification()

        val filter = IntentFilter("ACTION_UI_UPDATE_OFF")
        ContextCompat.registerReceiver(
            this,
            offReceiver,
            filter,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.RECEIVER_NOT_EXPORTED else 0
        )

        setContent {
            Smart_LampTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val scrollState = rememberScrollState()
        
        val alpha by animateFloatAsState(
            targetValue = if (uiEnabled.value) 1.0f else 0.4f,
            animationSpec = tween(durationMillis = 400),
            label = "alpha"
        )
        val scale by animateFloatAsState(
            targetValue = if (uiEnabled.value) 1.0f else 0.96f,
            animationSpec = tween(durationMillis = 400),
            label = "scale"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SMART LAMP",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
            )

            // Power Controls Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val onButtonColor by animateColorAsState(
                        targetValue = if (isLampOn.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        animationSpec = tween(durationMillis = 300),
                        label = "onColor"
                    )
                    val offButtonColor by animateColorAsState(
                        targetValue = if (!isLampOn.value) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                        animationSpec = tween(durationMillis = 300),
                        label = "offColor"
                    )

                    Button(
                        onClick = {
                            sendToLamp("/setMode?val=0")
                            sendToLamp("/setBrightness?val=255")
                            uiEnabled.value = true
                            isLampOn.value = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = onButtonColor,
                            contentColor = if (isLampOn.value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("On")
                    }

                    Button(
                        onClick = {
                            sendToLamp("/setBrightness?val=0")
                            uiEnabled.value = false
                            isLampOn.value = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = offButtonColor,
                            contentColor = if (!isLampOn.value) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Off")
                    }
                }
            }

            // Grouping the rest of the UI for collective animation
            Column(
                modifier = Modifier
                    .alpha(alpha)
                    .scale(scale),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Selection
                var modeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { if (uiEnabled.value) modeExpanded = !modeExpanded },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = selectedMode.value,
                        onValueChange = {},
                        readOnly = true,
                        enabled = uiEnabled.value,
                        label = { Text("Select Lamp Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false }
                    ) {
                        modes.forEachIndexed { index, mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = {
                                    selectedMode.value = mode
                                    modeExpanded = false
                                    handleModeSelection(index)
                                }
                            )
                        }
                    }
                }

                // Color Picker Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "COLOR",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        SimpleColorPicker(
                            enabled = uiEnabled.value,
                            onColorChanged = { r, g, b ->
                                sendToLamp("/setColor?r=$r&g=$g&b=$b")
                            }
                        )

                        Slider(
                            value = brightnessState.value,
                            onValueChange = { 
                                if (uiEnabled.value) {
                                    brightnessState.value = it
                                    sendToLamp("/setBrightness?val=${it.toInt()}")
                                }
                            },
                            valueRange = 0f..255f,
                            enabled = uiEnabled.value,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                // Animation Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "ANIMATIONS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        var animExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = animExpanded,
                            onExpandedChange = { if (uiEnabled.value) animExpanded = !animExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedAnimation.value,
                                onValueChange = {},
                                readOnly = true,
                                enabled = uiEnabled.value,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = animExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            ExposedDropdownMenu(
                                expanded = animExpanded,
                                onDismissRequest = { animExpanded = false }
                            ) {
                                animNames.forEachIndexed { index, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedAnimation.value = name
                                            animExpanded = false
                                            if (uiEnabled.value) {
                                                sendToLamp("/setAnimation?val=$index")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SimpleColorPicker(enabled: Boolean, onColorChanged: (Int, Int, Int) -> Unit) {
        val sweepBrush = remember {
            Brush.sweepGradient(
                listOf(
                    Color.Red, Color.Magenta, Color.Blue, Color.Cyan,
                    Color.Green, Color.Yellow, Color.Red
                )
            )
        }

        var indicatorAngle by remember { mutableStateOf<Float?>(null) }

        Box(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val angleRad = atan2(offset.y - centerY, offset.x - centerX)
                        var hue = (angleRad * 180 / PI).toFloat()
                        if (hue < 0) hue += 360f
                        indicatorAngle = angleRad

                        val colorInt = HSVToColor(floatArrayOf(hue, 1f, 1f))
                        onColorChanged(
                            android.graphics.Color.red(colorInt),
                            android.graphics.Color.green(colorInt),
                            android.graphics.Color.blue(colorInt)
                        )
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val angleRad = atan2(change.position.y - centerY, change.position.x - centerX)
                        var hue = (angleRad * 180 / PI).toFloat()
                        if (hue < 0) hue += 360f
                        indicatorAngle = angleRad

                        val colorInt = HSVToColor(floatArrayOf(hue, 1f, 1f))
                        onColorChanged(
                            android.graphics.Color.red(colorInt),
                            android.graphics.Color.green(colorInt),
                            android.graphics.Color.blue(colorInt)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                val thickness = 40.dp.toPx()
                drawCircle(
                    brush = sweepBrush,
                    radius = radius - thickness / 2,
                    style = Stroke(width = thickness)
                )

                indicatorAngle?.let { angle ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val indicatorRadius = radius - thickness / 2
                    val x = centerX + indicatorRadius * cos(angle)
                    val y = centerY + indicatorRadius * sin(angle)

                    drawCircle(
                        color = Color.White,
                        radius = 12.dp.toPx(),
                        center = Offset(x, y),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        radius = 13.dp.toPx(),
                        center = Offset(x, y),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
    }

    private fun handleModeSelection(position: Int) {
        when (position) {
            0 -> { // Ambient
                sendToLamp("/setMode?val=0")
                uiEnabled.value = true
            }
            1 -> { // Run All Animations
                sendToLamp("/setAuto?val=1")
                sendToLamp("/setMode?val=1")
                uiEnabled.value = true
            }
            2 -> { // Pick Animation
                sendToLamp("/setAuto?val=0")
                sendToLamp("/setMode?val=1")
                uiEnabled.value = true
            }
            3 -> { // Pick Color
                sendToLamp("/setMode?val=0")
                uiEnabled.value = true
            }
            4 -> { // Music Visualizer
                val intent = Intent(this, VisualizerActivity::class.java)
                intent.putExtra("LAMP_IP", lampIp)
                startActivity(intent)
            }
        }
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
        try {
            unregisterReceiver(offReceiver)
        } catch (e: Exception) {}
    }

    private fun fetchLampStatus() {
        val request = Request.Builder().url("$lampIp/").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SmartLamp", "Lampa offline")
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
                    } catch (e: Exception) {}
                }
                response.close()
            }
        })
    }

    private fun updateUIFromStatus(mode: Int, brightness: Int, auto: Int) {
        val isOn = brightness > 0
        isLampOn.value = isOn
        brightnessState.value = brightness.toFloat()

        val modeText = when {
            mode == 0 && isOn -> modes[0]
            mode == 1 && auto == 1 -> modes[1]
            mode == 1 && auto == 0 -> modes[2]
            mode == 0 && !isOn -> modes[3]
            mode == 2 -> modes[4]
            else -> modes[0]
        }

        if (selectedMode.value != modeText) {
            selectedMode.value = modeText
        }
        uiEnabled.value = isOn
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lamp Control"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            putExtra("LAMP_IP", lampIp)
        }
        val pendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentTitle("Smart Lamp Control")
            .setContentText("Lamp is active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_lock_power_off, "TURN OFF", pendingIntent)

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
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
