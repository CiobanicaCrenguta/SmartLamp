package com.example.smart_lamp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class ConnectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        val etIpAddress = findViewById<TextInputEditText>(R.id.etIpAddress)
        val btnConnect = findViewById<Button>(R.id.btnConnect)

        btnConnect.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (validateIp(ip)) {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("LAMP_IP", "http://$ip")
                startActivity(intent)
                finish() // Închidem ConnectionActivity după conectare
            } else {
                Toast.makeText(this, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateIp(ip: String): Boolean {
        val pattern = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}\$")
        return pattern.matches(ip)
    }
}
