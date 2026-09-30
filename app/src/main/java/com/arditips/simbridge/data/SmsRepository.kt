package com.arditips.simbridge.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.ContactsContract
import com.arditips.simbridge.model.ChatConversation
import com.arditips.simbridge.model.ChatMessage
import com.arditips.simbridge.util.PhoneNumberUtil

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "simbridge_chat.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                address TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_outgoing INTEGER NOT NULL,
                is_read INTEGER DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_address ON messages(address)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_time ON messages(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

object LocalMessageStore {
    private var dbHelper: AppDatabaseHelper? = null

    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = AppDatabaseHelper(context.applicationContext)
        }
    }

    @Synchronized
    fun saveMessage(context: Context, address: String, body: String, timestamp: Long, isOutgoing: Boolean) {
        init(context)
        val normalized = PhoneNumberUtil.normalize(address)
        val db = dbHelper?.writableDatabase ?: return
        val values = ContentValues().apply {
            put("address", normalized)
            put("body", body)
            put("timestamp", timestamp)
            put("is_outgoing", if (isOutgoing) 1 else 0)
            put("is_read", if (isOutgoing) 1 else 0)
        }
        db.insert("messages", null, values)
    }

    @Synchronized
    fun markConversationAsRead(context: Context, address: String) {
        init(context)
        val db = dbHelper?.writableDatabase ?: return
        val targetNorm = PhoneNumberUtil.normalize(address)
        val values = ContentValues().apply {
            put("is_read", 1)
        }
        db.update("messages", values, "address = ?", arrayOf(targetNorm))
    }

    @Synchronized
    fun getConversations(context: Context): List<ChatConversation> {
        init(context)
        val list = mutableListOf<ChatConversation>()
        val db = dbHelper?.readableDatabase ?: return list

        val query = """
            SELECT address, body, timestamp, 
                   SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END) as unread_cnt
            FROM messages
            GROUP BY address
            ORDER BY timestamp DESC
        """.trimIndent()

        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, null)
            while (cursor.moveToNext()) {
                val address = cursor.getString(0) ?: continue
                val body = cursor.getString(1) ?: ""
                val timestamp = cursor.getLong(2)
                val unread = cursor.getInt(3)
                val name = SmsRepository.getContactName(context, address)

                list.add(
                    ChatConversation(
                        contact = address,
                        contactName = name,
                        lastMessage = body,
                        timestamp = timestamp,
                        unreadCount = unread
                    )
                )
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return list
    }

    @Synchronized
    fun getMessagesForContact(context: Context, address: String): List<ChatMessage> {
        init(context)
        val list = mutableListOf<ChatMessage>()
        val db = dbHelper?.readableDatabase ?: return list
        val targetNorm = PhoneNumberUtil.normalize(address)
        val contactName = SmsRepository.getContactName(context, address)

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                "messages",
                arrayOf("id", "address", "body", "timestamp", "is_outgoing"),
                null,
                null,
                null,
                null,
                "timestamp ASC"
            )
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val msgAddress = cursor.getString(1)
                val body = cursor.getString(2)
                val timestamp = cursor.getLong(3)
                val isOutgoing = cursor.getInt(4) == 1

                if (PhoneNumberUtil.isSame(msgAddress, targetNorm)) {
                    list.add(
                        ChatMessage(
                            id = id,
                            sender = msgAddress,
                            senderName = contactName,
                            body = body,
                            timestamp = timestamp,
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

object SmsRepository {

    @SuppressLint("Range")
    fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        var contactName: String? = null
        var cursor: Cursor? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return contactName
    }

    fun loadConversations(context: Context): List<ChatConversation> {
        val localList = LocalMessageStore.getConversations(context)
        if (localList.isNotEmpty()) {
            return localList
        }

        // Fallback to system messages
        val conversationsMap = mutableMapOf<String, ChatConversation>()
        val uri: Uri = android.provider.Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            android.provider.Telephony.Sms.ADDRESS,
            android.provider.Telephony.Sms.BODY,
            android.provider.Telephony.Sms.DATE,
            android.provider.Telephony.Sms.READ
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${android.provider.Telephony.Sms.DATE} DESC"
            )

            cursor?.let {
                val addressIdx = it.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(android.provider.Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(android.provider.Telephony.Sms.DATE)
                val readIdx = it.getColumnIndex(android.provider.Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val rawAddress = it.getString(addressIdx) ?: continue
                    val normalized = PhoneNumberUtil.normalize(rawAddress)
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val read = it.getInt(readIdx)

                    if (!conversationsMap.containsKey(normalized)) {
                        val name = getContactName(context, rawAddress)
                        conversationsMap[normalized] = ChatConversation(
                            contact = normalized,
                            contactName = name,
                            lastMessage = body,
                            timestamp = date,
                            unreadCount = if (read == 0) 1 else 0
                        )
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
        val localMsgs = LocalMessageStore.getMessagesForContact(context, contactAddress)
        if (localMsgs.isNotEmpty()) {
            return localMsgs
        }

        val list = mutableListOf<ChatMessage>()
        val uri: Uri = android.provider.Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            android.provider.Telephony.Sms._ID,
            android.provider.Telephony.Sms.ADDRESS,
            android.provider.Telephony.Sms.BODY,
            android.provider.Telephony.Sms.DATE,
            android.provider.Telephony.Sms.TYPE
        )

        val contactName = getContactName(context, contactAddress)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${android.provider.Telephony.Sms.DATE} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(android.provider.Telephony.Sms._ID)
                val addressIdx = it.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(android.provider.Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(android.provider.Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(android.provider.Telephony.Sms.TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val address = it.getString(addressIdx) ?: ""
                    if (!PhoneNumberUtil.isSame(address, contactAddress)) continue

                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val isOutgoing = (type == android.provider.Telephony.Sms.MESSAGE_TYPE_SENT || type == android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX)

                    list.add(
                        ChatMessage(
                            id = id,
                            sender = address,
                            senderName = contactName,
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
