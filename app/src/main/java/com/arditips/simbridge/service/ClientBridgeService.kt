package com.arditips.simbridge.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arditips.simbridge.R
import com.arditips.simbridge.SimBridgeApp
import com.arditips.simbridge.audio.LiveAudioRelay
import com.arditips.simbridge.bluetooth.BluetoothConnection
import com.arditips.simbridge.bluetooth.BluetoothConstants
import com.arditips.simbridge.model.BridgeEventItem
import com.arditips.simbridge.model.BridgePacket
import com.arditips.simbridge.model.CallCommand
import com.arditips.simbridge.model.CallStateData
import com.arditips.simbridge.model.SmsData
import com.arditips.simbridge.model.SmsSendRequest
import com.arditips.simbridge.ui.IncomingCallActivity
import com.arditips.simbridge.ui.MainActivity
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean

class ClientBridgeService : Service() {

    private val tag = "ClientBridgeService"
    private val isConnecting = AtomicBoolean(false)
    private var activeConnection: BluetoothConnection? = null
    private var audioRelay: LiveAudioRelay? = null
    private var ringtone: Ringtone? = null
    private val gson = Gson()

    companion object {
        var instance: ClientBridgeService? = null
            private set

        const val ACTION_CONNECT = "com.arditips.simbridge.CONNECT_CLIENT"
        const val ACTION_DISCONNECT = "com.arditips.simbridge.DISCONNECT_CLIENT"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        // Broadcast actions for UI
        const val BROADCAST_EVENT = "com.arditips.simbridge.EVENT"
        const val EXTRA_EVENT_JSON = "event_json"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioRelay = LiveAudioRelay(this)
        val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, defaultRingtoneUri)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) {
                    startForeground(1002, buildNotification("در حال اتصال به میزبان..."))
                    connectToGateway(address)
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun connectToGateway(deviceAddress: String) {
        if (isConnecting.getAndSet(true)) return

        Thread {
            try {
                val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = btManager.adapter.getRemoteDevice(deviceAddress)
                val socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.DATA_UUID)

                socket.connect()
                Log.d(tag, "Connected to Gateway: ${device.name}")
                updateNotification("متصل به میزبان (${device.name ?: deviceAddress}) ✓")

                activeConnection = BluetoothConnection(
                    socket = socket,
                    onPacketReceived = { packet -> handlePacket(packet) },
                    onDisconnected = {
                        Log.d(tag, "Disconnected from gateway")
                        updateNotification("ارتباط با میزبان قطع شد")
                        stopRinging()
                        audioRelay?.stop()
                    }
                )
                activeConnection?.startListening()
            } catch (e: Exception) {
                Log.e(tag, "Connection failed", e)
                updateNotification("خطا در اتصال به میزبان")
            } finally {
                isConnecting.set(false)
            }
        }.start()
    }

    private fun handlePacket(packet: BridgePacket) {
        when (packet.type) {
            BridgePacket.TYPE_SMS_RECEIVED -> {
                val sms = gson.fromJson(packet.payload, SmsData::class.java)
                onSmsReceived(sms)
            }
            BridgePacket.TYPE_CALL_STATE -> {
                val call = gson.fromJson(packet.payload, CallStateData::class.java)
                onCallStateChanged(call)
            }
        }
    }

    private fun onSmsReceived(sms: SmsData) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, SimBridgeApp.CHANNEL_SMS)
            .setContentTitle("پیامک از: ${sms.sender}")
            .setContentText(sms.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sms.body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notif)

        // Broadcast to UI
        val event = BridgeEventItem(
            type = "SMS",
            title = "پیامک از ${sms.sender}",
            detail = sms.body
        )
        broadcastEvent(event)
    }

    private fun onCallStateChanged(call: CallStateData) {
        when (call.state) {
            "RINGING" -> {
                startRinging()
                val intent = Intent(this, IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("CALLER_NUMBER", call.callerNumber ?: "ناشناس")
                }
                startActivity(intent)

                broadcastEvent(
                    BridgeEventItem(
                        type = "CALL",
                        title = "تماس ورودی",
                        detail = "از شماره: ${call.callerNumber ?: "ناشناس"}"
                    )
                )
            }
            "OFFHOOK" -> {
                stopRinging()
                // Start live audio relay
                activeConnection?.let { conn ->
                    audioRelay?.start(conn.getInputStream(), conn.getOutputStream())
                }
            }
            "IDLE" -> {
                stopRinging()
                audioRelay?.stop()
                val intent = Intent("com.arditips.simbridge.CALL_ENDED")
                sendBroadcast(intent)
            }
        }
    }

    fun answerCall() {
        val payload = gson.toJson(CallCommand(CallCommand.ACTION_ANSWER))
        activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_CALL_CMD, payload))
        stopRinging()
        activeConnection?.let { conn ->
            audioRelay?.start(conn.getInputStream(), conn.getOutputStream())
        }
    }

    fun rejectCall() {
        val payload = gson.toJson(CallCommand(CallCommand.ACTION_REJECT))
        activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_CALL_CMD, payload))
        stopRinging()
        audioRelay?.stop()
    }

    fun endCall() {
        val payload = gson.toJson(CallCommand(CallCommand.ACTION_HANGUP))
        activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_CALL_CMD, payload))
        audioRelay?.stop()
    }

    fun sendSms(recipient: String, body: String): Boolean {
        val payload = gson.toJson(SmsSendRequest(recipient, body))
        val success = activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_SMS_SEND_REQ, payload)) ?: false
        if (success) {
            broadcastEvent(
                BridgeEventItem(
                    type = "SMS_SENT",
                    title = "پیامک ارسالی به $recipient",
                    detail = body
                )
            )
        }
        return success
    }

    private fun startRinging() {
        try {
            if (ringtone?.isPlaying == false) {
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error playing ringtone", e)
        }
    }

    private fun stopRinging() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping ringtone", e)
        }
    }

    private fun broadcastEvent(event: BridgeEventItem) {
        val intent = Intent(BROADCAST_EVENT).apply {
            putExtra(EXTRA_EVENT_JSON, gson.toJson(event))
        }
        sendBroadcast(intent)
    }

    private fun disconnect() {
        activeConnection?.close()
        audioRelay?.stop()
        stopRinging()
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SimBridgeApp.CHANNEL_SERVICE)
            .setContentTitle("SIM Bridge - دریافت‌کننده کلاینت")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1002, buildNotification(text))
    }

    override fun onDestroy() {
        disconnect()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
