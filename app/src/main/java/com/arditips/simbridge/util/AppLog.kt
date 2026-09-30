package com.arditips.simbridge.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val TAG = "SimBridge"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val memoryLogs = mutableListOf<String>()
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, "app_logs.txt")
            if (logFile?.exists() == true && logFile!!.length() > 500_000) {
                logFile?.delete()
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun d(tag: String, message: String) {
        val entry = "${timeFormat.format(Date())} [D/$tag] $message"
        Log.d(tag, message)
        append(entry)
    }

    @Synchronized
    fun i(tag: String, message: String) {
        val entry = "${timeFormat.format(Date())} [I/$tag] $message"
        Log.i(tag, message)
        append(entry)
    }

    @Synchronized
    fun w(tag: String, message: String) {
        val entry = "${timeFormat.format(Date())} [W/$tag] $message"
        Log.w(tag, message)
        append(entry)
    }

    @Synchronized
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val stack = throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
        val entry = "${timeFormat.format(Date())} [E/$tag] $message$stack"
        Log.e(tag, message, throwable)
        append(entry)
    }

    private fun append(entry: String) {
        if (memoryLogs.size > 200) {
            memoryLogs.removeAt(0)
        }
        memoryLogs.add(entry)
        try {
            logFile?.appendText(entry + "\n")
        } catch (_: Exception) {}
    }

    fun getAllLogs(): String {
        return try {
            if (logFile?.exists() == true) {
                logFile?.readText() ?: memoryLogs.joinToString("\n")
            } else {
                memoryLogs.joinToString("\n")
            }
        } catch (_: Exception) {
            memoryLogs.joinToString("\n")
        }
    }

    fun clear() {
        memoryLogs.clear()
        try {
            logFile?.delete()
        } catch (_: Exception) {}
    }
}
