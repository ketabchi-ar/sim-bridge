package com.arditips.simbridge.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.arditips.simbridge.R
import com.arditips.simbridge.SimBridgeApp
import com.arditips.simbridge.bluetooth.BluetoothConnection
import com.arditips.simbridge.bluetooth.BluetoothConstants
import com.arditips.simbridge.data.LocalMessageStore
import com.arditips.simbridge.data.SmsRepository
import com.arditips.simbridge.model.BridgeEventItem
import com.arditips.simbridge.model.BridgePacket
import com.arditips.simbridge.model.CallCommand
import com.arditips.simbridge.model.CallStateData
import com.arditips.simbridge.model.SmsData
import com.arditips.simbridge.model.SmsSendRequest
import com.arditips.simbridge.ui.IncomingCallActivity
import com.arditips.simbridge.ui.MainActivity
import com.arditips.simbridge.util.AppLog
import com.arditips.simbridge.util.PhoneNumberUtil
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean

class ClientBridgeService : Service() {

    private val tag = "ClientBridgeService"
    private val isConnecting = AtomicBoolean(false)
    private var activeConnection: BluetoothConnection? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val gson = Gson()

    companion object {
        var instance: ClientBridgeService? = null
            private set

        const val ACTION_CONNECT = "com.arditips.simbridge.CONNECT_CLIENT"
        const val ACTION_DISCONNECT = "com.arditips.simbridge.DISCONNECT_CLIENT"
        const val ACTION_ANSWER = "com.arditips.simbridge.ANSWER_FROM_NOTIF"
        const val ACTION_REJECT = "com.arditips.simbridge.REJECT_FROM_NOTIF"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        const val BROADCAST_EVENT = "com.arditips.simbridge.EVENT"
        const val BROADCAST_NEW_MESSAGE = "com.arditips.simbridge.NEW_MESSAGE"
        const val EXTRA_EVENT_JSON = "event_json"
        const val EXTRA_MSG_SENDER = "msg_sender"
        const val EXTRA_MSG_BODY = "msg_body"
        const val EXTRA_MSG_TIME = "msg_time"
        const val INCOMING_CALL_NOTIF_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLog.i(tag, "ClientBridgeService created")
        try {
            val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, defaultRingtoneUri)
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to initialize ringtone/vibrator", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                AppLog.i(tag, "Disconnecting ClientBridgeService")
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) {
                    try {
                        val notif = buildNotification("در حال اتصال به میزبان...")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(1002, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                        } else {
                            startForeground(1002, notif)
                        }
                    } catch (e: Exception) {
                        AppLog.e(tag, "Failed to start foreground client service", e)
                    }
                    connectToGateway(address)
                }
            }
            ACTION_ANSWER -> {
                answerCall()
                dismissCallNotification()
            }
            ACTION_REJECT -> {
                rejectCall()
                dismissCallNotification()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun connectToGateway(deviceAddress: String) {
        if (isConnecting.getAndSet(true)) return

        Thread {
            try {
                val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter
                if (adapter == null) {
                    AppLog.e(tag, "Bluetooth adapter not available")
                    return@Thread
                }
                val device = adapter.getRemoteDevice(deviceAddress)
                val socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.DATA_UUID)

                AppLog.i(tag, "Connecting socket to ${device.name ?: deviceAddress}...")
                socket.connect()
                AppLog.i(tag, "Connected to Gateway: ${device.name}")
                updateNotification("متصل به میزبان (${device.name ?: deviceAddress}) ✓")

                activeConnection = BluetoothConnection(
                    socket = socket,
                    onPacketReceived = { packet -> handlePacket(packet) },
                    onDisconnected = {
                        AppLog.i(tag, "Disconnected from gateway")
                        updateNotification("ارتباط با میزبان قطع شد")
                        stopRingingAndVibration()
                    }
                )
                activeConnection?.startListening()
            } catch (e: Exception) {
                AppLog.e(tag, "Connection failed", e)
                updateNotification("خطا در اتصال به میزبان")
            } finally {
                isConnecting.set(false)
            }
        }.start()
    }

    private fun handlePacket(packet: BridgePacket) {
        AppLog.d(tag, "Packet from gateway: ${packet.type}")
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
        val normalizedSender = PhoneNumberUtil.normalize(sms.sender)
        AppLog.i(tag, "SMS received from $normalizedSender")

        // 1. Save directly into Local App Database
        LocalMessageStore.saveMessage(
            context = this,
            address = normalizedSender,
            body = sms.body,
            timestamp = sms.timestamp,
            isOutgoing = false
        )

        // 2. Write to Android Native SMS Inbox & trigger sync
        writeSmsToInbox(normalizedSender, sms.body, sms.timestamp)
        triggerSystemSmsSync()

        // 3. Post High-Priority Notification with Contact Name
        val contactName = SmsRepository.getContactName(this, normalizedSender) ?: normalizedSender
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, SimBridgeApp.CHANNEL_SMS)
            .setContentTitle("پیامک از: $contactName")
            .setContentText(sms.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sms.body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notif)

        // 4. Broadcast live update to SimBridge Chat & Main Activity
        val event = BridgeEventItem(
            type = "SMS",
            title = contactName,
            detail = sms.body
        )
        broadcastEvent(event)

        val chatIntent = Intent(BROADCAST_NEW_MESSAGE).apply {
            putExtra(EXTRA_MSG_SENDER, normalizedSender)
            putExtra(EXTRA_MSG_BODY, sms.body)
            putExtra(EXTRA_MSG_TIME, sms.timestamp)
        }
        sendBroadcast(chatIntent)
    }

    private fun writeSmsToInbox(sender: String, body: String, timestamp: Long) {
        try {
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(this, sender)
            } catch (e: Exception) {
                0L
            }

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (threadId > 0) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
            }

            val uri = contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            uri?.let {
                contentResolver.notifyChange(it, null)
                contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
                contentResolver.notifyChange(Uri.parse("content://mms-sms/conversations"), null)
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to write SMS to native inbox: ${e.message}", e)
        }
    }

    private fun triggerSystemSmsSync() {
        try {
            val syncIntent = Intent("com.samsung.android.messaging.intent.action.TP_SYNC_FOR_RESTORE_MESSAGE")
            sendBroadcast(syncIntent)

            val finishIntent = Intent("com.samsung.android.messaging.intent.action.FINISH_RESTORE_MESSAGE")
            sendBroadcast(finishIntent)
        } catch (_: Exception) {}
    }

    private fun onCallStateChanged(call: CallStateData) {
        AppLog.i(tag, "Call state changed: ${call.state}, caller: ${call.callerNumber}")
        when (call.state) {
            "RINGING" -> {
                val callerNumber = call.callerNumber ?: "ناشناس"
                val callerName = SmsRepository.getContactName(this, callerNumber) ?: callerNumber

                startRingingAndVibration()
                showIncomingCallNotification(callerNumber, callerName)

                // Try starting Activity directly as well
                try {
                    val intent = Intent(this, IncomingCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("CALLER_NUMBER", callerNumber)
                        putExtra("CALLER_NAME", callerName)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    AppLog.w(tag, "Could not start direct call activity: ${e.message}")
                }

                broadcastEvent(
                    BridgeEventItem(
                        type = "CALL",
                        title = "تماس ورودی",
                        detail = "از: $callerName"
                    )
                )
            }
            "OFFHOOK" -> {
                stopRingingAndVibration()
                dismissCallNotification()
            }
            "IDLE" -> {
                stopRingingAndVibration()
                dismissCallNotification()
                val intent = Intent("com.arditips.simbridge.CALL_ENDED")
                sendBroadcast(intent)
            }
        }
    }

    private fun showIncomingCallNotification(callerNumber: String, callerName: String) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALLER_NUMBER", callerNumber)
            putExtra("CALLER_NAME", callerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 201, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerIntent = Intent(this, ClientBridgeService::class.java).apply {
            action = ACTION_ANSWER
        }
        val answerPendingIntent = PendingIntent.getService(
            this, 202, answerIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, ClientBridgeService::class.java).apply {
            action = ACTION_REJECT
        }
        val rejectPendingIntent = PendingIntent.getService(
            this, 203, rejectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, SimBridgeApp.CHANNEL_CALL)
            .setContentTitle("تماس ورودی مخابراتی")
            .setContentText(callerName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "پاسخ", answerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "رد تماس", rejectPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(INCOMING_CALL_NOTIF_ID, notif)
    }

    private fun dismissCallNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(INCOMING_CALL_NOTIF_ID)
    }

    fun answerCall() {
        AppLog.i(tag, "Answering call")
        val payload = gson.toJson(CallCommand(CallCommand.ACTION_ANSWER))
        activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_CALL_CMD, payload))
        stopRingingAndVibration()
        dismissCallNotification()
    }

    fun rejectCall() {
        AppLog.i(tag, "Rejecting call")
        val payload = gson.toJson(CallCommand(CallCommand.ACTION_REJECT))
        activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_CALL_CMD, payload))
        stopRingingAndVibration()
        dismissCallNotification()
    }

    fun endCall() {
        AppLog.i(tag, "Ending call")
        val payload = gson.toJson(CallCommand(CallCommand.ACTION_HANGUP))
        activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_CALL_CMD, payload))
        stopRingingAndVibration()
        dismissCallNotification()
    }

    fun sendSms(recipient: String, body: String): Boolean {
        val normalized = PhoneNumberUtil.normalize(recipient)
        val payload = gson.toJson(SmsSendRequest(normalized, body))
        val success = activeConnection?.sendPacket(BridgePacket(BridgePacket.TYPE_SMS_SEND_REQ, payload)) ?: false
        if (success) {
            val now = System.currentTimeMillis()
            AppLog.i(tag, "Outgoing SMS sent to $normalized")

            // 1. Save locally in App Database
            LocalMessageStore.saveMessage(
                context = this,
                address = normalized,
                body = body,
                timestamp = now,
                isOutgoing = true
            )

            // 2. Save in native database
            try {
                val threadId = try {
                    Telephony.Threads.getOrCreateThreadId(this, normalized)
                } catch (_: Exception) { 0L }

                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, normalized)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, now)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    if (threadId > 0) {
                        put(Telephony.Sms.THREAD_ID, threadId)
                    }
                }
                val uri = contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                uri?.let {
                    contentResolver.notifyChange(it, null)
                    contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)
                    contentResolver.notifyChange(Uri.parse("content://mms-sms/conversations"), null)
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to save sent SMS to native db", e)
            }

            val contactName = SmsRepository.getContactName(this, normalized) ?: normalized
            broadcastEvent(
                BridgeEventItem(
                    type = "SMS_SENT",
                    title = "پیامک ارسالی به $contactName",
                    detail = body
                )
            )
        } else {
            AppLog.w(tag, "Failed to send outgoing SMS to $normalized (not connected?)")
        }
        return success
    }

    private fun startRingingAndVibration() {
        try {
            if (ringtone?.isPlaying == false) {
                ringtone?.play()
            }
            val pattern = longArrayOf(0, 1000, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Error playing ringtone/vibration", e)
        }
    }

    private fun stopRingingAndVibration() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
            vibrator?.cancel()
        } catch (e: Exception) {
            AppLog.e(tag, "Error stopping ringtone/vibration", e)
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
        stopRingingAndVibration()
        dismissCallNotification()
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
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1002, buildNotification(text))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        disconnect()
        instance = null
        AppLog.i(tag, "ClientBridgeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
