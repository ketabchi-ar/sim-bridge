package com.arditips.simbridge.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.arditips.simbridge.model.ChatConversation
import com.arditips.simbridge.model.ChatMessage

object SmsRepository {

    fun loadConversations(context: Context): List<ChatConversation> {
        val conversationsMap = mutableMapOf<String, ChatConversation>()
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.let {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val address = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val read = it.getInt(readIdx)

                    if (!conversationsMap.containsKey(address)) {
                        conversationsMap[address] = ChatConversation(
                            contact = address,
                            lastMessage = body,
                            timestamp = date,
                            unreadCount = if (read == 0) 1 else 0
                        )
                    } else if (read == 0) {
                        val existing = conversationsMap[address]!!
                        conversationsMap[address] = existing.copy(unreadCount = existing.unreadCount + 1)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }

        return conversationsMap.values.toList()
    }

    fun loadMessagesForContact(context: Context, contactAddress: String): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(contactAddress),
                "${Telephony.Sms.DATE} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val address = it.getString(addressIdx) ?: contactAddress
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val isOutgoing = (type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX)

                    list.add(
                        ChatMessage(
                            id = id,
                            sender = address,
                            body = body,
                            timestamp = date,
                            isOutgoing = isOutgoing
                        )
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }

        return list
    }
}
