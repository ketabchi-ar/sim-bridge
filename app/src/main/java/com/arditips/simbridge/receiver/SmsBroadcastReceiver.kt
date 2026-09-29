package com.arditips.simbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.arditips.simbridge.service.GatewayBridgeService

class SmsBroadcastReceiver : BroadcastReceiver() {

    private val tag = "SmsReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages[0].displayOriginatingAddress ?: "ناشناس"
            val body = StringBuilder()
            for (msg in messages) {
                body.append(msg.displayMessageBody)
            }

            Log.d(tag, "Received SMS from $sender: $body")
            GatewayBridgeService.dispatchSms(sender, body.toString())
        }
    }
}
