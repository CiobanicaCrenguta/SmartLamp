package com.example.smart_lamp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_lamp.ui.theme.Smart_LampTheme

class VisualizerActivity : ComponentActivity() {

    private lateinit var lampIp: String

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startVisualizerService(result.resultCode, result.data!!)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lampIp = intent.getStringExtra("LAMP_IP") ?: "http://192.168.1.15"

        setContent {
            Smart_LampTheme {
                VisualizerScreen()
            }
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun VisualizerScreen() {
        val colorScheme = MaterialTheme.colorScheme
        val isDark = isSystemInDarkTheme()

        // Shared Animation State for Perfect Sync
        val infiniteTransition = rememberInfiniteTransition(label = "shared_visualizer")
        val scale1 by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale1"
        )
        val scale2 by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale2"
        )
        val scale3 by infiniteTransition.animateFloat(
            initialValue = 1.1f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale3"
        )
        
        // Deep, modern gradient for a "Visualizer" feel
        val gradientBackground = Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color(0xFF121212),
                    Color(0xFF1E1E2E)
                )
            } else {
                listOf(
                    Color(0xFFC5CAE9), // Indigo 100
                    //Color(0xFFE8EAF6), // Indigo 50
                    Color.White
                )
            }
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Music Visualizer",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = if (isDark) Color.White else colorScheme.onBackground
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBackground)
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedVisualizerIcon(scale1, scale2, scale3)

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "MUSIC VISUALIZER",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        ),
                        color = if (isDark) Color(0xFFD0BCFF) else colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Your lamp is dancing to the rhythm!\nSystem audio is being captured and\ntranslated into beautiful light patterns.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDark) Color.White.copy(alpha = 0.9f) else colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // High contrast Streaming Card with Animated Icon
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) {
                                Color(0xFF2D2D44)
                            } else {
                                Color(0xFFE3F2FD) // Solid Light Blue for better contrast/no glitch
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF80DEEA) else colorScheme.primary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .scale(scale1) // Animated in sync
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Streaming Live Audio",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = if (isDark) Color(0xFFE0F7FA) else colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = {
                            stopVisualizerService()
                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF7E57C2) else colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "STOP VISUALIZER",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun AnimatedVisualizerIcon(scale1: Float, scale2: Float, scale3: Float) {
        // Purple and Pink colors for the visualizer
        val purple = Color(0xFFBB86FC)
        val pink = Color(0xFFF48FB1)

        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale1),
                tint = purple
            )

            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale2)
                    .align(Alignment.Center),
                tint = pink.copy(alpha = 0.7f)
            )

            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale3)
                    .align(Alignment.Center),
                tint = purple.copy(alpha = 0.4f)
            )
        }
    }

    private fun startVisualizerService(resultCode: Int, data: Intent) {
        val intent = Intent(this, VisualizerService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
            putExtra("LAMP_IP", lampIp)
        }
        startForegroundService(intent)
    }

    private fun stopVisualizerService() {
        stopService(Intent(this, VisualizerService::class.java))
    }
}
