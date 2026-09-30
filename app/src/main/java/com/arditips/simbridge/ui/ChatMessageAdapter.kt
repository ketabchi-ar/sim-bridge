package com.arditips.simbridge.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.arditips.simbridge.R
import com.arditips.simbridge.model.ChatMessage
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMessageAdapter : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_bubble, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position], timeFormat)
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView as LinearLayout
        private val card: MaterialCardView = itemView.findViewById(R.id.cardMessage)
        private val tvBody: TextView = itemView.findViewById(R.id.tvMessageBody)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)

        fun bind(message: ChatMessage, timeFormat: SimpleDateFormat) {
            tvBody.text = message.body
            tvTime.text = timeFormat.format(Date(message.timestamp))

            val context = itemView.context
            if (message.isOutgoing) {
                container.gravity = Gravity.END
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.purple_700))
            } else {
                container.gravity = Gravity.START
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
            }
        }
    }
}
