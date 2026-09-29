package com.arditips.simbridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SimBridgeApp : Application() {

    companion object {
        const val CHANNEL_SERVICE = "sim_bridge_service_channel"
        const val CHANNEL_SMS = "sim_bridge_sms_channel"
        const val CHANNEL_CALL = "sim_bridge_call_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
