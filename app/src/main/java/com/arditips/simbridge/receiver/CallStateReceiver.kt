package com.arditips.simbridge.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.telephony.TelephonyManager
import com.arditips.simbridge.service.GatewayBridgeService
import com.arditips.simbridge.util.AppLog

class CallStateReceiver : BroadcastReceiver() {

    private val tag = "CallStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            @Suppress("DEPRECATION")
            var incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            // If incomingNumber is null (Android 9+ privacy rule), query the latest incoming entry from CallLog
            if (incomingNumber.isNullOrEmpty() && (stateStr == TelephonyManager.EXTRA_STATE_RINGING || stateStr == "RINGING")) {
                incomingNumber = getLatestIncomingCallNumber(context)
            }

            AppLog.i(tag, "CallState changed to $stateStr, resolved number: $incomingNumber")
            GatewayBridgeService.dispatchCallState(stateStr, incomingNumber)
        }
    }

    @SuppressLint("Range")
    private fun getLatestIncomingCallNumber(context: Context): String? {
        var number: String? = null
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )
            if (cursor != null && cursor.moveToFirst()) {
                number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))
            }
        } catch (e: Exception) {
            AppLog.w(tag, "Failed to query CallLog: ${e.message}")
        } finally {
            cursor?.close()
        }
        return number
    }
}
