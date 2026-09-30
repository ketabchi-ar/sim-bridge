package com.arditips.simbridge.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arditips.simbridge.R
import com.arditips.simbridge.model.ChatConversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onConversationClick: (ChatConversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    private val items = mutableListOf<ChatConversation>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setConversations(newItems: List<ChatConversation>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, timeFormat)
        holder.itemView.setOnClickListener {
            onConversationClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatarLetter)
        private val tvContact: TextView = itemView.findViewById(R.id.tvConversationContact)
        private val tvTime: TextView = itemView.findViewById(R.id.tvConversationTime)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvConversationLastMessage)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvUnreadBadge)

        fun bind(item: ChatConversation, timeFormat: SimpleDateFormat) {
            val displayName = item.contactName ?: item.contact
            tvContact.text = displayName
            tvTime.text = timeFormat.format(Date(item.timestamp))
            tvLastMessage.text = item.lastMessage

            val letter = if (displayName.isNotEmpty()) displayName.take(1).uppercase() else "#"
            tvAvatar.text = letter

            if (item.unreadCount > 0) {
                tvUnread.visibility = View.VISIBLE
                tvUnread.text = item.unreadCount.toString()
            } else {
                tvUnread.visibility = View.GONE
            }
        }
    }
}
