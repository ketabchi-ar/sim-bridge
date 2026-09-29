package com.arditips.simbridge.model

data class BridgePacket(
    val type: String, // "SMS_RECEIVED", "SMS_SEND_REQ", "CALL_STATE", "CALL_CMD", "PING", "PONG"
    val payload: String
) {
    companion object {
        const val TYPE_SMS_RECEIVED = "SMS_RECEIVED"
        const val TYPE_SMS_SEND_REQ = "SMS_SEND_REQ"
        const val TYPE_CALL_STATE = "CALL_STATE"
        const val TYPE_CALL_CMD = "CALL_CMD"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"
    }
}

data class SmsData(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SmsSendRequest(
    val recipient: String,
    val body: String
)

data class CallStateData(
    val state: String, // "RINGING", "OFFHOOK", "IDLE"
    val callerNumber: String?
)

data class CallCommand(
    val action: String // "ANSWER", "REJECT", "HANGUP"
) {
    companion object {
        const val ACTION_ANSWER = "ANSWER"
        const val ACTION_REJECT = "REJECT"
        const val ACTION_HANGUP = "HANGUP"
    }
}

data class BridgeEventItem(
    val type: String,
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)
