package com.arditips.simbridge.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arditips.simbridge.R
import com.arditips.simbridge.SimBridgeApp
import com.arditips.simbridge.audio.LiveAudioRelay
import com.arditips.simbridge.bluetooth.BluetoothConnection
import com.arditips.simbridge.bluetooth.BluetoothConstants
import com.arditips.simbridge.model.BridgePacket
import com.arditips.simbridge.model.CallCommand
import com.arditips.simbridge.model.CallStateData
import com.arditips.simbridge.model.SmsData
import com.arditips.simbridge.model.SmsSendRequest
import com.arditips.simbridge.ui.MainActivity
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean

class GatewayBridgeService : Service() {

    private val tag = "GatewayBridgeService"
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: BluetoothServerSocket? = null
    private var activeConnection: BluetoothConnection? = null
    private var audioRelay: LiveAudioRelay? = null
    private val gson = Gson()

    companion object {
        var instance: GatewayBridgeService? = null
            private set

        const val ACTION_START = "com.arditips.simbridge.START_GATEWAY"
        const val ACTION_STOP = "com.arditips.simbridge.STOP_GATEWAY"

        fun dispatchSms(sender: String, body: String) {
            instance?.sendSmsToClient(sender, body)
        }

        fun dispatchCallState(state: String, callerNumber: String?) {
            instance?.sendCallStateToClient(state, callerNumber)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioRelay = LiveAudioRelay(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(1001, buildNotification("سرور میزبان فعال - در انتظار اتصال گوشی دوم..."))
                startServer()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        if (isRunning.getAndSet(true)) return

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter

        Thread {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                    BluetoothConstants.SERVICE_NAME,
                    BluetoothConstants.DATA_UUID
                )
                Log.d(tag, "Gateway server socket listening...")

                while (isRunning.get()) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(tag, "Client connected: ${socket.remoteDevice.name}")

                    updateNotification("متصل به گوشی دوم (${socket.remoteDevice.name ?: "Client"})")

                    activeConnection = BluetoothConnection(
                        socket = socket,
                        onPacketReceived = { packet -> handleClientPacket(packet) },
                        onDisconnected = {
                            Log.d(tag, "Client disconnected")
                            updateNotification("سرور میزبان فعال - ارتباط با کلاینت قطع شد")
                            audioRelay?.stop()
                        }
                    )
                    activeConnection?.startListening()
                }
            } catch (e: Exception) {
                Log.e(tag, "Server socket error", e)
            }
        }.start()
    }

    private fun handleClientPacket(packet: BridgePacket) {
        when (packet.type) {
            BridgePacket.TYPE_SMS_SEND_REQ -> {
                val req = gson.fromJson(packet.payload, SmsSendRequest::class.java)
                sendOutgoingSms(req.recipient, req.body)
            }
            BridgePacket.TYPE_CALL_CMD -> {
                val cmd = gson.fromJson(packet.payload, CallCommand::class.java)
                handleCallCommand(cmd.action)
            }
            BridgePacket.TYPE_PING -> {
                activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_PONG, "{}"))
            }
        }
    }

    private fun sendSmsToClient(sender: String, body: String) {
        val payload = gson.toJson(SmsData(sender, body))
        val packet = BridgePacket(BridgePacket.TYPE_SMS_RECEIVED, payload)
        activeConnection?.sendPacket(packet)
    }

    private fun sendCallStateToClient(state: String, callerNumber: String?) {
        val payload = gson.toJson(CallStateData(state, callerNumber))
        val packet = BridgePacket(BridgePacket.TYPE_CALL_STATE, payload)
        activeConnection?.sendPacket(packet)

        if (state == "OFFHOOK" && activeConnection != null) {
            // Call answered: start audio relay
            activeConnection?.let { conn ->
                audioRelay?.start(conn.getInputStream(), conn.getOutputStream())
            }
        } else if (state == "IDLE") {
            audioRelay?.stop()
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleCallCommand(action: String) {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try {
            when (action) {
                CallCommand.ACTION_ANSWER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        telecomManager.acceptRingingCall()
                    }
                }
                CallCommand.ACTION_REJECT, CallCommand.ACTION_HANGUP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        telecomManager.endCall()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute call command: $action", e)
        }
    }

    private fun sendOutgoingSms(recipient: String, body: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(recipient, null, body, null, null)
            Log.d(tag, "Relayed SMS sent to $recipient")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send relayed SMS", e)
        }
    }

    private fun stopServer() {
        isRunning.set(false)
        try {
            activeConnection?.close()
            serverSocket?.close()
            audioRelay?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping server", e)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SimBridgeApp.CHANNEL_SERVICE)
            .setContentTitle("SIM Bridge - میزبان سیم‌کارت")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1001, buildNotification(text))
    }

    override fun onDestroy() {
        stopServer()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
