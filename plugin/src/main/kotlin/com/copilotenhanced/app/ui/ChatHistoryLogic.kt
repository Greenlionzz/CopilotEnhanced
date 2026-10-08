package com.copilotenhanced.app.ui

import com.copilotenhanced.app.chat.Chat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatHistoryLogic {

    fun filterChats(chats: List<Chat>, query: String): List<Chat> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return chats

        return chats.filter { chat ->
            chat.title.contains(trimmed, ignoreCase = true) ||
                chat.messages.any { it.content.contains(trimmed, ignoreCase = true) }
        }
    }

    fun formatTimeAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val oneMin = 60_000L
        val oneHour = 60 * oneMin
        val oneDay = 24 * oneHour
        val oneWeek = 7 * oneDay

        return when {
            diff < oneMin -> "Just now"
            diff < oneHour -> "${diff / oneMin}m ago"
            diff < oneDay -> "${diff / oneHour}h ago"
            diff < oneWeek -> "${diff / oneDay}d ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    fun lastMessagePreview(chat: Chat): String {
        val last = chat.messages.lastOrNull()?.content?.trim()
        if (last.isNullOrEmpty()) return "No messages yet"
        val singleLine = last.replace(Regex("\\s+"), " ")
        return if (singleLine.length > 70) singleLine.take(67) + "..." else singleLine
    }
}
