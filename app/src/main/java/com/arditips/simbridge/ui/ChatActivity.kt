package com.arditips.simbridge.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arditips.simbridge.data.SmsRepository
import com.arditips.simbridge.databinding.ActivityChatBinding
import com.arditips.simbridge.model.ChatMessage
import com.arditips.simbridge.service.ClientBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val messageAdapter = ChatMessageAdapter()
    private var contactAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactAddress = intent.getStringExtra(EXTRA_CONTACT) ?: ""
        binding.toolbarChat.title = contactAddress
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
                    body = text,
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = true
                )
                messageAdapter.addMessage(newMsg)
                binding.recyclerViewMessages.scrollToPosition(messageAdapter.itemCount - 1)
            } else {
                Toast.makeText(this, "خطا در ارسال پیام", Toast.LENGTH_SHORT).show()
            }
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

    companion object {
        const val EXTRA_CONTACT = "extra_contact"
    }
}
