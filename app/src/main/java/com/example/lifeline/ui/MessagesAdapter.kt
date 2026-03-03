package com.example.lifeline.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lifeline.R
import com.example.lifeline.data.MessageEntity
import com.example.lifeline.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * MessagesAdapter — RecyclerView adapter for displaying SOS messages.
 * Color-coded: Red for TRAPPED, Green for SAFE.
 */
class MessagesAdapter(
    private val onItemClick: ((MessageEntity) -> Unit)? = null
) : ListAdapter<MessageEntity, MessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {

    inner class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageEntity) {
            binding.apply {
                tvSenderName.text = message.senderName
                tvSenderPhone.text = message.senderPhone
                tvContent.text = message.content
                tvTimestamp.text = formatTimestamp(message.timestamp)
                tvLocation.text = String.format(
                    Locale.US, "📍 %.4f, %.4f",
                    message.latitude, message.longitude
                )

                // Status badge
                tvStatus.text = message.status
                val statusColor = if (message.status == "TRAPPED") {
                    ContextCompat.getColor(root.context, R.color.emergency_red)
                } else {
                    ContextCompat.getColor(root.context, R.color.safe_green)
                }
                tvStatus.setBackgroundColor(statusColor)

                // Status indicator bar
                statusIndicator.setBackgroundColor(statusColor)

                root.setOnClickListener {
                    onItemClick?.invoke(message)
                }
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            return oldItem == newItem
        }
    }
}
