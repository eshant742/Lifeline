package com.example.lifeline.ui

import android.view.LayoutInflater
import android.view.View
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
 *
 * Enhanced with:
 * - Priority badges (P1-P5) with color coding
 * - Message type icons (SOS, ACK, HEARTBEAT, RESOURCE, DANGER_ZONE)
 * - Hop count / relay indicator
 * - Medical info display (blood type, allergies)
 * - Rescue status display
 * - Color-coded status: Red for TRAPPED/INJURED, Green for SAFE
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

                // ── Message Type Icon ──
                tvMessageTypeIcon.text = getMessageTypeIcon(message.messageType)

                // ── Priority Badge ──
                tvPriority.text = "P${message.priority}"
                tvPriority.setBackgroundColor(getPriorityColor(message.priority))
                tvPriority.visibility = if (message.messageType == "HEARTBEAT") View.GONE else View.VISIBLE

                // ── Status Badge ──
                tvStatus.text = message.status
                val statusColor = when (message.status) {
                    "TRAPPED", "INJURED" -> ContextCompat.getColor(root.context, R.color.emergency_red)
                    "SAFE", "EVACUATED" -> ContextCompat.getColor(root.context, R.color.safe_green)
                    else -> ContextCompat.getColor(root.context, R.color.warning_amber)
                }
                tvStatus.setBackgroundColor(statusColor)

                // ── Status Indicator Bar ──
                statusIndicator.setBackgroundColor(statusColor)

                // ── Hop Count / Relay Indicator ──
                if (message.hopCount == 0) {
                    tvHopCount.text = "↗ Direct"
                } else {
                    tvHopCount.text = "↗ via ${message.hopCount} relay${if (message.hopCount > 1) "s" else ""}"
                }

                // ── Medical Info ──
                val hasMedicalInfo = message.bloodType.isNotEmpty() ||
                        message.allergies.isNotEmpty() ||
                        message.medicalConditions.isNotEmpty()

                if (hasMedicalInfo && (message.messageType == "SOS" || message.messageType == "STATUS_UPDATE")) {
                    layoutMedicalInfo.visibility = View.VISIBLE
                    val medParts = mutableListOf<String>()
                    if (message.bloodType.isNotEmpty()) medParts.add("🩸 ${message.bloodType}")
                    if (message.allergies.isNotEmpty()) medParts.add("⚠️ ${message.allergies}")
                    if (message.medicalConditions.isNotEmpty()) medParts.add("🏥 ${message.medicalConditions}")
                    tvMedicalInfo.text = medParts.joinToString(" | ")
                } else {
                    layoutMedicalInfo.visibility = View.GONE
                }

                // ── Rescue Status ──
                if (message.rescueStatus.isNotEmpty() && message.rescueStatus != "") {
                    tvRescueStatus.visibility = View.VISIBLE
                    val rescueIcon = when (message.rescueStatus) {
                        "RECEIVED" -> "📨"
                        "EN_ROUTE" -> "🚑"
                        "REACHED" -> "📍"
                        "FIRST_AID" -> "🩹"
                        "EVACUATED" -> "✅"
                        else -> "🔄"
                    }
                    tvRescueStatus.text = "$rescueIcon ${message.rescuerName}: ${message.rescueStatus}"
                } else {
                    tvRescueStatus.visibility = View.GONE
                }

                root.setOnClickListener {
                    onItemClick?.invoke(message)
                }
            }
        }

        private fun getMessageTypeIcon(type: String): String {
            return when (type) {
                "SOS" -> "🆘"
                "ACK" -> "✅"
                "HEARTBEAT" -> "💚"
                "RESOURCE" -> "📦"
                "DANGER_ZONE" -> "⚠️"
                "STATUS_UPDATE" -> "📝"
                else -> "📨"
            }
        }

        private fun getPriorityColor(priority: Int): Int {
            val context = binding.root.context
            return when (priority) {
                5 -> ContextCompat.getColor(context, R.color.emergency_red)       // Critical
                4 -> ContextCompat.getColor(context, R.color.warning_amber)       // High
                3 -> ContextCompat.getColor(context, R.color.warning_amber)       // Medium
                2 -> ContextCompat.getColor(context, R.color.safe_green)          // Info
                1 -> ContextCompat.getColor(context, R.color.safe_green)          // Low
                else -> ContextCompat.getColor(context, R.color.text_secondary)
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
