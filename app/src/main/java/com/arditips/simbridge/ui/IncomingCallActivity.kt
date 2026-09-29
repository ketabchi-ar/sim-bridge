package com.arditips.simbridge.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.arditips.simbridge.databinding.ActivityIncomingCallBinding
import com.arditips.simbridge.service.ClientBridgeService

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding

    private val callEndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndShowOnLockScreen()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "ناشناس"
        binding.tvCallerNumber.text = callerNumber

        binding.btnAnswerCall.setOnClickListener {
            ClientBridgeService.instance?.answerCall()
            binding.layoutIncomingActions.visibility = View.GONE
            binding.btnEndOngoingCall.visibility = View.VISIBLE
            binding.tvCallStatus.text = "مکالمه زنده برقرار است (صدا و میکروفون فعال)"
        }

        binding.btnRejectCall.setOnClickListener {
            ClientBridgeService.instance?.rejectCall()
            finish()
        }

        binding.btnEndOngoingCall.setOnClickListener {
            ClientBridgeService.instance?.endCall()
            finish()
        }

        val filter = IntentFilter("com.arditips.simbridge.CALL_ENDED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndReceiver, filter)
        }
    }

    private fun turnScreenOnAndShowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(callEndReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
