package com.arditips.simbridge.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arditips.simbridge.R
import com.arditips.simbridge.model.BridgeEventItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    private val items = mutableListOf<BridgeEventItem>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun addEvent(event: BridgeEventItem) {
        items.add(0, event)
        notifyItemInserted(0)
    }

    fun clearEvents() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(items[position], timeFormat)
    }

    override fun getItemCount(): Int = items.size

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEventType: TextView = itemView.findViewById(R.id.tvEventType)
        private val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        private val tvEventSender: TextView = itemView.findViewById(R.id.tvEventSender)
        private val tvEventDetails: TextView = itemView.findViewById(R.id.tvEventDetails)

        fun bind(item: BridgeEventItem, timeFormat: SimpleDateFormat) {
            tvEventType.text = when (item.type) {
                "CALL" -> "📞 تماس ورودی"
                "SMS" -> "📩 پیامک دریافتی"
                "SMS_SENT" -> "📤 پیامک ارسالی"
                else -> item.type
            }
            tvEventTime.text = timeFormat.format(Date(item.timestamp))
            tvEventSender.text = item.title
            tvEventDetails.text = item.detail
        }
    }
}
