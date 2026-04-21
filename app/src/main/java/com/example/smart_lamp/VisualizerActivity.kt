package com.example.smart_lamp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Music Visualizer") },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(200.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "STREAMING SYSTEM AUDIO",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp)
                )

                Text(
                    text = "The lamp is dancing to your music",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Button(
                    onClick = {
                        stopVisualizerService()
                        finish()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                        .height(64.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("STOP VISUALIZER")
                }
            }
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
