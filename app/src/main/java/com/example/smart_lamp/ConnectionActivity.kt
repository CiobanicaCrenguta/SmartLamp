package com.example.smart_lamp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_lamp.ui.theme.Smart_LampTheme

class ConnectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Smart_LampTheme {
                ConnectionScreen()
            }
        }
    }

    @Composable
    fun ConnectionScreen() {
        var ipAddress by remember { mutableStateOf("") }
        val colorScheme = MaterialTheme.colorScheme
        val isDark = isSystemInDarkTheme()

        // Modern adaptive gradient from VisualizerActivity
        val gradientBackground = Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color(0xFF121212),
                    Color(0xFF1E1E2E)
                )
            } else {
                listOf(
                    Color(0xFFC5CAE9), // Indigo 100
                    Color(0xFFE8EAF6), // Indigo 50
                    Color.White
                )
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lan,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (isDark) Color(0xFFD0BCFF) else colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Smart Lamp",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = if (isDark) Color.White else colorScheme.onBackground
                )

                Text(
                    text = "Enter the IP address of your lamp to start controlling it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
                )

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Lamp IP Address") },
                    placeholder = { Text("e.g. 192.168.1.15") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDark) Color(0xFFD0BCFF) else colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.5f),
                        unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val ip = ipAddress.trim()
                        val intent = Intent(this@ConnectionActivity, MainActivity::class.java)
                        if (ip.isNotEmpty()) {
                            val formattedIp = if (ip.startsWith("http")) ip else "http://$ip"
                            intent.putExtra("LAMP_IP", formattedIp)
                        }
                        startActivity(intent)
                        finish()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF7E57C2) else colorScheme.primary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "CONNECT",
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
