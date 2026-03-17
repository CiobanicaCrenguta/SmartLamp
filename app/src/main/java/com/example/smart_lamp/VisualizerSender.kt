package com.example.smart_lamp

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class VisualizerSender {
    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private val port = 4210

    fun start(espIp: String) {
        runCatching {
            // Remove http:// prefix if present
            val cleanIp = espIp.replace("http://", "").replace("https://", "")
            address = InetAddress.getByName(cleanIp)
            socket = DatagramSocket()
        }
    }

    fun stop() {
        runCatching {
            socket?.close()
            socket = null
        }
    }

    fun send(volume: Int, beat: Boolean, hue: Int) {
        val addr = address ?: return
        val sock = socket ?: return

        Thread {
            runCatching {
                val data = byteArrayOf(
                    volume.toByte(),
                    if (beat) 1.toByte() else 0.toByte(),
                    hue.toByte()
                )
                val packet = DatagramPacket(data, data.size, addr, port)
                sock.send(packet)
            }
        }.start()
    }
}
