package com.arditips.simbridge.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.arditips.simbridge.util.AppLog

class OtpCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_COPY_OTP) {
            val otp = intent.getStringExtra(EXTRA_OTP) ?: return
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("کد تایید", otp))

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "کد تایید کپی شد: $otp ✓", Toast.LENGTH_SHORT).show()
                }

                if (notifId != -1) {
                    val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notifManager.cancel(notifId)
                }
                AppLog.i("OtpCopyReceiver", "OTP copied successfully: $otp")
            } catch (e: Exception) {
                AppLog.e("OtpCopyReceiver", "Error copying OTP", e)
            }
        }
    }

    companion object {
        const val ACTION_COPY_OTP = "com.arditips.simbridge.ACTION_COPY_OTP"
        const val EXTRA_OTP = "extra_otp"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
