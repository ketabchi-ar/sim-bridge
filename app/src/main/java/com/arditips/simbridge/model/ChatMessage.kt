package com.arditips.simbridge.model

data class ChatMessage(
    val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean
)

data class ChatConversation(
    val contact: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0
)
