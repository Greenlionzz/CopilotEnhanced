package com.copilotenhanced.app.ui

import com.copilotenhanced.app.chat.Chat
import com.copilotenhanced.app.chat.ChatMode
import com.copilotenhanced.app.llm.ChatMessage

class ChatHistoryLogicTest {

    fun runAllTests() {
        testFilterChatsByTitle()
        testFilterChatsByMessageContent()
        testFilterChatsEmptyQuery()
        testFilterChatsNoMatches()
        testFormatTimeAgo()
        testLastMessagePreview()
        println("All ChatHistoryLogic tests passed successfully!")
    }

    private fun testFilterChatsByTitle() {
        val c1 = createSampleChat("1", "Calculator App", emptyList())
        val c2 = createSampleChat("2", "Weather Forecast", emptyList())
        val c3 = createSampleChat("3", "Note Taking", emptyList())

        val result = ChatHistoryLogic.filterChats(listOf(c1, c2, c3), "calc")
        check(result.size == 1 && result[0].id == "1") {
            "Expected 1 chat matching 'calc', got ${result.size}"
        }
    }

    private fun testFilterChatsByMessageContent() {
        val c1 = createSampleChat("1", "App One", listOf(ChatMessage.user("Build a flutter app")))
        val c2 = createSampleChat("2", "App Two", listOf(ChatMessage.user("Build a jetpack compose app")))

        val result = ChatHistoryLogic.filterChats(listOf(c1, c2), "jetpack")
        check(result.size == 1 && result[0].id == "2") {
            "Expected 1 chat matching 'jetpack', got ${result.size}"
        }
    }

    private fun testFilterChatsEmptyQuery() {
        val c1 = createSampleChat("1", "App One", emptyList())
        val c2 = createSampleChat("2", "App Two", emptyList())

        val result = ChatHistoryLogic.filterChats(listOf(c1, c2), "   ")
        check(result.size == 2) {
            "Expected all 2 chats for blank query, got ${result.size}"
        }
    }

    private fun testFilterChatsNoMatches() {
        val c1 = createSampleChat("1", "App One", emptyList())
        val result = ChatHistoryLogic.filterChats(listOf(c1), "nonexistent")
        check(result.isEmpty()) {
            "Expected empty list for nonexistent query, got ${result.size}"
        }
    }

    private fun testFormatTimeAgo() {
        val now = 1000000000000L
        check(ChatHistoryLogic.formatTimeAgo(now - 10000L, now) == "Just now")
        check(ChatHistoryLogic.formatTimeAgo(now - 5 * 60 * 1000L, now) == "5m ago")
        check(ChatHistoryLogic.formatTimeAgo(now - 3 * 3600 * 1000L, now) == "3h ago")
        check(ChatHistoryLogic.formatTimeAgo(now - 2 * 86400 * 1000L, now) == "2d ago")
    }

    private fun testLastMessagePreview() {
        val emptyChat = createSampleChat("1", "Empty", emptyList())
        check(ChatHistoryLogic.lastMessagePreview(emptyChat) == "No messages yet")

        val multiLineChat = createSampleChat(
            "2",
            "Multi",
            listOf(ChatMessage.assistant("Line 1\nLine 2\nLine 3"))
        )
        check(ChatHistoryLogic.lastMessagePreview(multiLineChat) == "Line 1 Line 2 Line 3")

        val longText = "A".repeat(100)
        val longChat = createSampleChat("3", "Long", listOf(ChatMessage.user(longText)))
        val preview = ChatHistoryLogic.lastMessagePreview(longChat)
        check(preview.length == 70 && preview.endsWith("...")) {
            "Expected truncated preview of 70 chars ending in ..., got length ${preview.length}: $preview"
        }
    }

    private fun createSampleChat(id: String, title: String, messages: List<ChatMessage>): Chat {
        val now = System.currentTimeMillis()
        return Chat(
            id = id,
            title = title,
            mode = ChatMode.PLAN,
            messages = messages,
            plan = null,
            planVersion = 0,
            memory = "",
            createdAt = now,
            updatedAt = now,
        )
    }
}
