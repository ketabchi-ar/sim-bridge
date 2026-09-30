package com.arditips.simbridge.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arditips.simbridge.databinding.ActivityIncomingCallBinding
import com.arditips.simbridge.service.ClientBridgeService

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "ناشناس"
        val callerName = intent.getStringExtra("CALLER_NAME") ?: callerNumber

        binding.tvCallerNumber.text = callerName
        binding.tvCallStatus.text = if (callerName != callerNumber) callerNumber else "در حال زنگ خوردن..."

        binding.btnAnswerCall.setOnClickListener {
            ClientBridgeService.instance?.answerCall()
            binding.layoutIncomingActions.visibility = android.view.View.GONE
            binding.btnEndOngoingCall.visibility = android.view.View.VISIBLE
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
    }
}
