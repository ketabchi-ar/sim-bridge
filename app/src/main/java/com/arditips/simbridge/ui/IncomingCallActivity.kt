package com.arditips.simbridge.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.arditips.simbridge.R
import com.arditips.simbridge.databinding.ActivityIncomingCallBinding
import com.arditips.simbridge.service.ClientBridgeService

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private var vibrator: Vibrator? = null
    private var isMuted = false
    private var isSpeakerOn = true
    private var callSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeScreenAndUnlock()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "ناشناس"
        val callerName = intent.getStringExtra("CALLER_NAME") ?: callerNumber

        binding.tvCallerNumber.text = callerName
        binding.tvCallStatus.text = if (callerName != callerNumber) callerNumber else "تماس ورودی مخابراتی..."
        binding.tvCallAvatar.text = if (callerName.isNotEmpty()) callerName.take(1).uppercase() else "👤"

        startVibration()

        binding.btnAnswerCall.setOnClickListener {
            stopVibration()
            ClientBridgeService.instance?.answerCall()

            binding.layoutIncomingActions.visibility = View.GONE
            binding.layoutCallControls.visibility = View.VISIBLE
            binding.btnEndOngoingCall.visibility = View.VISIBLE
            binding.tvCallTimer.visibility = View.VISIBLE
            binding.tvCallStatus.text = "مکالمه در جریان است..."

            startCallTimer()
        }

        binding.btnRejectCall.setOnClickListener {
            stopVibration()
            ClientBridgeService.instance?.rejectCall()
            finish()
        }

        binding.btnEndOngoingCall.setOnClickListener {
            stopVibration()
            stopCallTimer()
            ClientBridgeService.instance?.endCall()
            finish()
        }

        binding.btnToggleSpeaker.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            isSpeakerOn = !isSpeakerOn
            audioManager.isSpeakerphoneOn = isSpeakerOn
            binding.btnToggleSpeaker.setIconResource(
                if (isSpeakerOn) android.R.drawable.stat_sys_speakerphone else android.R.drawable.stat_sys_phone_call
            )
        }

        binding.btnToggleMute.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            isMuted = !isMuted
            audioManager.isMicrophoneMute = isMuted
            binding.btnToggleMute.setIconResource(
                if (isMuted) android.R.drawable.stat_notify_call_mute else android.R.drawable.ic_btn_speak_now
            )
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 800, 800, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun startCallTimer() {
        callSeconds = 0
        timerRunnable = object : Runnable {
            override fun run() {
                callSeconds++
                val mins = callSeconds / 60
                val secs = callSeconds % 60
                binding.tvCallTimer.text = String.format("%02d:%02d", mins, secs)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopCallTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun wakeScreenAndUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    override fun onDestroy() {
        stopVibration()
        stopCallTimer()
        super.onDestroy()
    }
}
