package com.reybel.gunderlinscanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.reybel.gunderlinscanner.R
import com.reybel.gunderlinscanner.data.models.LogEntry
import com.reybel.gunderlinscanner.data.models.LogLevel
import java.text.SimpleDateFormat
import java.util.*

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs = listOf<LogEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    fun clearLogs() {
        logs = emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textLogMessage)
        private val textTime: TextView = itemView.findViewById(R.id.textLogTime)

        fun bind(logEntry: LogEntry) {
            textMessage.text = logEntry.message
            textTime.text = timeFormat.format(Date(logEntry.timestamp))

            // Determinar color basado en el contenido del mensaje - Colores más sobrios
            val colorResId = when {
                logEntry.message.startsWith("✅") -> R.color.log_success
                logEntry.message.startsWith("❌") -> R.color.log_error
                logEntry.message.startsWith("⚠️") -> R.color.log_warning
                logEntry.message.startsWith("🔍") -> R.color.log_info
                logEntry.message.startsWith("👤") -> R.color.log_user
                logEntry.message.startsWith("⏳") -> R.color.log_pending
                else -> R.color.text_secondary
            }

            val color = ContextCompat.getColor(itemView.context, colorResId)
            textMessage.setTextColor(color)
        }
    }
}