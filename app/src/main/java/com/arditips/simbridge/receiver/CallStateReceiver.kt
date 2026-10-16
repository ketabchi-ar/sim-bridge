package com.arditips.simbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.arditips.simbridge.service.GatewayBridgeService
import com.arditips.simbridge.util.AppLog

class CallStateReceiver : BroadcastReceiver() {

    private val tag = "CallStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            @Suppress("DEPRECATION")
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.takeIf { it.isNotBlank() }

            AppLog.i(tag, "CallState broadcast received: $stateStr, number: $incomingNumber")
            GatewayBridgeService.dispatchCallState(stateStr, incomingNumber)
        }
    }
}
