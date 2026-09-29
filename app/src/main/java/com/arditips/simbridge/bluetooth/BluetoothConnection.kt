package com.arditips.simbridge.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.arditips.simbridge.model.BridgePacket
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothConnection(
    private val socket: BluetoothSocket,
    private val onPacketReceived: (BridgePacket) -> Unit,
    private val onDisconnected: () -> Unit
) {
    private val tag = "BluetoothConnection"
    private val isConnected = AtomicBoolean(true)
    private val gson = Gson()

    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var workerThread: Thread? = null

    init {
        try {
            reader = BufferedReader(InputStreamReader(socket.inputStream))
            writer = PrintWriter(socket.outputStream, true)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize streams", e)
            close()
        }
    }

    fun startListening() {
        workerThread = Thread {
            try {
                while (isConnected.get()) {
                    val line = reader?.readLine() ?: break
                    if (line.isNotBlank()) {
                        try {
                            val packet = gson.fromJson(line, BridgePacket::class.java)
                            if (packet != null) {
                                onPacketReceived(packet)
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Error parsing packet: $line", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "Connection reader stopped: ${e.message}")
            } finally {
                close()
                onDisconnected()
            }
        }.apply { start() }
    }

    @Synchronized
    fun sendPacket(packet: BridgePacket): Boolean {
        if (!isConnected.get() || writer == null) return false
        return try {
            val json = gson.toJson(packet)
            writer?.println(json)
            true
        } catch (e: Exception) {
            Log.e(tag, "Error sending packet", e)
            false
        }
    }

    fun close() {
        if (!isConnected.getAndSet(false)) return
        try {
            reader?.close()
            writer?.close()
            socket.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing connection", e)
        }
    }

    fun getInputStream() = socket.inputStream
    fun getOutputStream() = socket.outputStream
}
