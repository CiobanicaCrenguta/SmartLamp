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
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
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

    private val brightnessState = mutableStateOf(120f)
    private val isLampOn = mutableStateOf(true)
    private val selectedMode = mutableStateOf("Ambient")
    private val selectedAnimation = mutableStateOf("Spiral")
    private val uiEnabled = mutableStateOf(true)
    private val selectedColor = mutableStateOf(Color(0xFF2196F3))

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
        enableEdgeToEdge()
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
            targetValue = if (uiEnabled.value) 1.0f else 0.98f,
            animationSpec = tween(durationMillis = 400),
            label = "scale"
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Smart Lamp",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        )
                    },
                    actions = {},
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Modern Power Section
                PowerSection()

                Spacer(modifier = Modifier.height(24.dp))

                // Modern Controls Group
                Column(
                    modifier = Modifier
                        .alpha(alpha)
                        .scale(scale),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ModernModeSelector()
                    ModernColorPickerCard()
                    ModernAnimationCard()
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    @Composable
    fun PowerSection() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PowerButton(
                label = "ON",
                isActive = isLampOn.value,
                activeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.PowerSettingsNew,
                onClick = {
                    sendToLamp("/setMode?val=0")
                    sendToLamp("/setBrightness?val=255")
                    uiEnabled.value = true
                    isLampOn.value = true
                },
                modifier = Modifier.weight(1f)
            )

            PowerButton(
                label = "OFF",
                isActive = !isLampOn.value,
                activeColor = MaterialTheme.colorScheme.error,
                icon = Icons.Default.PowerSettingsNew,
                onClick = {
                    sendToLamp("/setBrightness?val=0")
                    uiEnabled.value = false
                    isLampOn.value = false
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    fun PowerButton(
        label: String,
        isActive: Boolean,
        activeColor: Color,
        icon: ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val containerColor by animateColorAsState(
            targetValue = if (isActive) activeColor else Color.Transparent,
            animationSpec = tween(300), label = "color"
        )
        val contentColor by animateColorAsState(
            targetValue = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            animationSpec = tween(300), label = "content"
        )

        Box(
            modifier = modifier
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ModernModeSelector() {
        var modeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = modeExpanded,
            onExpandedChange = { if (uiEnabled.value) modeExpanded = !modeExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedMode.value,
                onValueChange = {},
                readOnly = true,
                enabled = uiEnabled.value,
                label = { Text("Lamp Mode", fontWeight = FontWeight.Medium) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
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
    }

    @Composable
    fun ModernColorPickerCard() {
        val isColorMode = selectedMode.value == "Pick Color"
        val colorPickerAlpha by animateFloatAsState(
            targetValue = if (uiEnabled.value && isColorMode) 1.0f else 0.4f,
            label = "colorAlpha"
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(colorPickerAlpha),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "COLORS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    color = selectedColor.value
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                SimpleColorPicker(
                    enabled = uiEnabled.value && isColorMode,
                    onColorChanged = { r, g, b ->
                        selectedColor.value = android.graphics.Color.rgb(r, g, b).let {
                            Color(it)
                        }
                        sendToLamp("/setColor?r=$r&g=$g&b=$b")
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Brightness",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(end = 12.dp)
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ModernAnimationCard() {
        val isAnimMode = selectedMode.value == "Pick Animation"
        val animPickerAlpha by animateFloatAsState(
            targetValue = if (uiEnabled.value && isAnimMode) 1.0f else 0.4f,
            label = "animAlpha"
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(animPickerAlpha),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "ANIMATIONS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                var animExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = animExpanded,
                    onExpandedChange = { if (uiEnabled.value && isAnimMode) animExpanded = !animExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedAnimation.value,
                        onValueChange = {},
                        readOnly = true,
                        enabled = uiEnabled.value && isAnimMode,
                        placeholder = { Text("Select animation") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = animExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
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
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        val angleRad = atan2(dy, dx)
                        var adjustedAngle = (angleRad * 180 / PI).toFloat() + 90f
                        if (adjustedAngle < 0) adjustedAngle += 360f
                        val hue = (360f - adjustedAngle) % 360f
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
                        val dx = change.position.x - centerX
                        val dy = change.position.y - centerY
                        val angleRad = atan2(dy, dx)
                        var adjustedAngle = (angleRad * 180 / PI).toFloat() + 90f
                        if (adjustedAngle < 0) adjustedAngle += 360f
                        val hue = (360f - adjustedAngle) % 360f
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
                
                rotate(-90f) {
                    drawCircle(
                        brush = sweepBrush,
                        radius = radius - thickness / 2,
                        style = Stroke(width = thickness)
                    )
                }

                indicatorAngle?.let { angle ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val indicatorRadius = radius - thickness / 2
                    val x = centerX + indicatorRadius * cos(angle)
                    val y = centerY + indicatorRadius * sin(angle)

                    drawCircle(
                        color = Color.White,
                        radius = 14.dp.toPx(),
                        center = Offset(x, y),
                        style = Stroke(width = 4.dp.toPx())
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
        try { unregisterReceiver(offReceiver) } catch (e: Exception) {}
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
