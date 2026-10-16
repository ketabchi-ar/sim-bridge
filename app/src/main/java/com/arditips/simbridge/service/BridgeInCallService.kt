package com.arditips.simbridge.service

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.arditips.simbridge.util.AppLog

class BridgeInCallService : InCallService() {

    private val tag = "BridgeInCallService"

    companion object {
        var currentCall: Call? = null
            private set
        var instance: BridgeInCallService? = null
            private set

        fun answerCurrentCall(): Boolean {
            val call = currentCall ?: return false
            try {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                setSpeaker(true)
                AppLog.i("BridgeInCallService", "Answered call via InCallService")
                return true
            } catch (e: Exception) {
                AppLog.e("BridgeInCallService", "Failed to answer call via InCallService", e)
                return false
            }
        }

        fun endCurrentCall(): Boolean {
            val call = currentCall ?: return false
            try {
                call.disconnect()
                AppLog.i("BridgeInCallService", "Disconnected call via InCallService")
                return true
            } catch (e: Exception) {
                AppLog.e("BridgeInCallService", "Failed to end call via InCallService", e)
                return false
            }
        }

        fun setSpeaker(on: Boolean) {
            val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            try {
                instance?.setAudioRoute(route)
                AppLog.i("BridgeInCallService", "setAudioRoute to $route")
            } catch (e: Exception) {
                AppLog.e("BridgeInCallService", "Failed to setAudioRoute", e)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        instance = this

        val number = call.details.handle?.schemeSpecificPart
        AppLog.i(tag, "InCallService call added: $number")

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                val num = c.details.handle?.schemeSpecificPart
                AppLog.i(tag, "InCallService call state changed: $state, number: $num")
                when (state) {
                    Call.STATE_RINGING -> {
                        GatewayBridgeService.dispatchCallState("RINGING", num)
                    }
                    Call.STATE_ACTIVE -> {
                        setSpeaker(true)
                        GatewayBridgeService.dispatchCallState("OFFHOOK", num)
                    }
                    Call.STATE_DISCONNECTED -> {
                        GatewayBridgeService.dispatchCallState("IDLE", num)
                        if (currentCall == c) {
                            currentCall = null
                        }
                    }
                }
            }
        })

        if (call.state == Call.STATE_RINGING) {
            GatewayBridgeService.dispatchCallState("RINGING", number)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        AppLog.i(tag, "InCallService call removed")
        if (currentCall == call) {
            currentCall = null
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
