package com.arditips.simbridge.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arditips.simbridge.data.LocalMessageStore
import com.arditips.simbridge.data.SmsRepository
import com.arditips.simbridge.databinding.ActivityChatBinding
import com.arditips.simbridge.model.ChatMessage
import com.arditips.simbridge.service.ClientBridgeService
import com.arditips.simbridge.util.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val messageAdapter = ChatMessageAdapter()
    private var contactAddress: String = ""

    private val liveMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra(ClientBridgeService.EXTRA_MSG_SENDER) ?: return
            val body = intent.getStringExtra(ClientBridgeService.EXTRA_MSG_BODY) ?: ""
            val time = intent.getLongExtra(ClientBridgeService.EXTRA_MSG_TIME, System.currentTimeMillis())

            if (PhoneNumberUtil.isSame(sender, contactAddress)) {
                val newMsg = ChatMessage(
                    sender = sender,
                    senderName = binding.toolbarChat.title.toString(),
                    body = body,
                    timestamp = time,
                    isOutgoing = false
                )
                messageAdapter.addMessage(newMsg)
                binding.recyclerViewMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactAddress = intent.getStringExtra(EXTRA_CONTACT) ?: ""
        val contactName = SmsRepository.getContactName(this, contactAddress) ?: contactAddress
        binding.toolbarChat.title = contactName
        binding.toolbarChat.subtitle = if (contactName != contactAddress) contactAddress else null
        binding.toolbarChat.setNavigationOnClickListener { finish() }

        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = messageAdapter

        binding.btnSendReply.setOnClickListener {
            val text = binding.etChatReply.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) return@setOnClickListener

            val client = ClientBridgeService.instance
            if (client == null) {
                Toast.makeText(this, "ارتباط با گوشی اول متصل نیست", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sent = client.sendSms(contactAddress, text)
            if (sent) {
                binding.etChatReply.setText("")
                val newMsg = ChatMessage(
                    sender = contactAddress,
                    senderName = contactName,
                    body = text,
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = true
                )
                messageAdapter.addMessage(newMsg)
                binding.recyclerViewMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
            } else {
                Toast.makeText(this, "خطا در ارسال پیام", Toast.LENGTH_SHORT).show()
            }
        }

        val filter = IntentFilter(ClientBridgeService.BROADCAST_NEW_MESSAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(liveMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(liveMessageReceiver, filter)
        }

        loadChatHistory()
    }

    private fun loadChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = SmsRepository.loadMessagesForContact(this@ChatActivity, contactAddress)
            withContext(Dispatchers.Main) {
                messageAdapter.setMessages(messages)
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(liveMessageReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONTACT = "extra_contact"
    }
}
