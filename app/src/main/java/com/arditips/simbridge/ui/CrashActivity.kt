package com.arditips.simbridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arditips.simbridge.databinding.ActivityCrashBinding
import com.arditips.simbridge.util.AppLog

class CrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val errorDetails = intent.getStringExtra(EXTRA_ERROR_DETAILS) ?: AppLog.getAllLogs()
        binding.tvCrashDetails.text = errorDetails

        binding.btnCopyError.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SIM Bridge Crash Log", errorDetails)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "متن خطا در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
        }

        binding.btnRestartApp.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    companion object {
        const val EXTRA_ERROR_DETAILS = "extra_error_details"
    }
}
