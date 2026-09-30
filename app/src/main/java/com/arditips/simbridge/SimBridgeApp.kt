package com.arditips.simbridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.arditips.simbridge.ui.CrashActivity
import com.arditips.simbridge.util.AppLog

class SimBridgeApp : Application() {

    companion object {
        const val CHANNEL_SERVICE = "sim_bridge_service_channel"
        const val CHANNEL_SMS = "sim_bridge_sms_channel"
        const val CHANNEL_CALL = "sim_bridge_call_channel"
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        setupGlobalExceptionHandler()
        createNotificationChannels()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e("CrashHandler", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                val fullReport = "Thread: ${thread.name}\nException: ${throwable.javaClass.name}\nMessage: ${throwable.message}\n\nStack Trace:\n$stackTrace\n\nRecent Logs:\n${AppLog.getAllLogs()}"

                val intent = Intent(this, CrashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(CrashActivity.EXTRA_ERROR_DETAILS, fullReport)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("SimBridge", "Failed to launch CrashActivity", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "SIM Bridge Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت اتصال و پایداری سرویس در پس‌زمینه"
            }

            val smsChannel = NotificationChannel(
                CHANNEL_SMS,
                "پیامک‌های دریافتی سیم‌کارت",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان پیامک‌های منتقل‌شده از گوشی اول"
                enableVibration(true)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALL,
                "تماس‌های ورودی سیم‌کارت",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان تماس‌های ورودی منتقل‌شده از گوشی اول"
                enableVibration(true)
            }

            manager.createNotificationChannels(listOf(serviceChannel, smsChannel, callChannel))
        }
    }
}
