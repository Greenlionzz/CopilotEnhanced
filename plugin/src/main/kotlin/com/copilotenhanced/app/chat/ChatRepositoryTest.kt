package com.copilotenhanced.app.chat

import com.copilotenhanced.app.llm.ChatMessage
import java.io.File
import java.nio.file.Files

class ChatRepositoryTest {

    private fun createTempDir(): File {
        return Files.createTempDirectory("chat_repo_test_${System.currentTimeMillis()}").toFile()
    }

    fun testCreateAndGet() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val chat = repo.create(title = "My Project", mode = ChatMode.PLAN, id = "chat-123")

            check(chat.id == "chat-123") { "Expected id chat-123, got ${chat.id}" }
            check(chat.title == "My Project") { "Expected title My Project, got ${chat.title}" }
            check(chat.mode == ChatMode.PLAN) { "Expected mode PLAN, got ${chat.mode}" }
            check(chat.messages.isEmpty()) { "Messages should be empty" }
            check(chat.plan == null) { "Plan should be null" }
            check(chat.planVersion == 0) { "PlanVersion should be 0" }
            check(chat.memory == "") { "Memory should be empty" }

            val chatJsonFile = File(tempDir, "chats_v2/chat-123/chat.json")
            val memoryFile = File(tempDir, "chats_v2/chat-123/memory.md")
            check(chatJsonFile.exists()) { "chat.json should exist on disk" }
            check(memoryFile.exists()) { "memory.md should exist on disk" }

            val loaded = repo.get("chat-123")
            check(loaded != null) { "Loaded chat should not be null" }
            check(loaded!!.id == "chat-123")
            check(loaded!!.title == "My Project")
            check(loaded!!.mode == ChatMode.PLAN)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testListSortedByUpdatedAt() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val chat1 = repo.create(title = "Chat 1", id = "c1")
            Thread.sleep(10)
            val chat2 = repo.create(title = "Chat 2", id = "c2")
            Thread.sleep(10)
            val chat3 = repo.create(title = "Chat 3", id = "c3")

            val initialList = repo.list()
            check(initialList.size == 3) { "Expected 3 chats_v2, got ${initialList.size}" }
            check(initialList.map { it.id } == listOf("c3", "c2", "c1")) {
                "Expected c3, c2, c1; got ${initialList.map { it.id }}"
            }

            // Update c1
            Thread.sleep(10)
            repo.rename("c1", "Chat 1 Renamed")

            val updatedList = repo.list()
            check(updatedList.map { it.id } == listOf("c1", "c3", "c2")) {
                "Expected c1, c3, c2 after updating c1; got ${updatedList.map { it.id }}"
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testAppendMessages() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val chat = repo.create(title = "Test Append", id = "c-append")

            repo.append("c-append", ChatMessage.user("Hello"))
            repo.append("c-append", ChatMessage.assistant("Hi there!"))

            val loaded = repo.get("c-append")
            check(loaded != null) { "Chat should exist" }
            check(loaded!!.messages.size == 2) { "Expected 2 messages, got ${loaded!!.messages.size}" }
            check(loaded!!.messages[0].role == ChatMessage.ROLE_USER && loaded!!.messages[0].content == "Hello")
            check(loaded!!.messages[1].role == ChatMessage.ROLE_ASSISTANT && loaded!!.messages[1].content == "Hi there!")
            check(loaded!!.updatedAt >= chat.updatedAt) { "updatedAt should have advanced" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testSetMode() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            repo.create(title = "Mode Test", mode = ChatMode.PLAN, id = "c-mode")

            val updated = repo.setMode("c-mode", ChatMode.BUILD)
            check(updated?.mode == ChatMode.BUILD) { "Mode should be BUILD" }

            val reloaded = repo.get("c-mode")
            check(reloaded?.mode == ChatMode.BUILD) { "Persisted mode should be BUILD" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testSetPlan() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            repo.create(title = "Plan Test", id = "c-plan")

            val plan1 = repo.setPlan("c-plan", "# Plan Step 1")
            check(plan1?.plan == "# Plan Step 1")
            check(plan1?.planVersion == 1) { "Expected planVersion 1, got ${plan1?.planVersion}" }

            val plan2 = repo.setPlan("c-plan", "# Plan Step 2")
            check(plan2?.plan == "# Plan Step 2")
            check(plan2?.planVersion == 2) { "Expected planVersion 2, got ${plan2?.planVersion}" }

            val explicitVersionPlan = repo.setPlan("c-plan", "# Explicit Plan", version = 5)
            check(explicitVersionPlan?.planVersion == 5) { "Expected planVersion 5" }

            val reloaded = repo.get("c-plan")
            check(reloaded != null) { "Reloaded plan should not be null" }
            check(reloaded!!.plan == "# Explicit Plan")
            check(reloaded!!.planVersion == 5)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testReadAndWriteMemory() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            repo.create(title = "Memory Test", id = "c-mem")

            check(repo.readMemory("c-mem") == "") { "Initial memory should be empty" }

            val memoryContent = "- User prefers Kotlin\n- Target SDK is 36"
            val written = repo.writeMemory("c-mem", memoryContent)
            check(written) { "writeMemory should succeed" }

            val readBack = repo.readMemory("c-mem")
            check(readBack == memoryContent) { "readMemory should match written text" }

            val memoryFile = File(tempDir, "chats_v2/c-mem/memory.md")
            check(memoryFile.exists()) { "memory.md file should exist" }
            check(memoryFile.readText() == memoryContent) { "memory.md content mismatch" }

            val chat = repo.get("c-mem")
            check(chat?.memory == memoryContent) { "Chat object memory property mismatch" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testRename() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            repo.create(title = "Old Name", id = "c-rename")

            val updated = repo.rename("c-rename", "New Name")
            check(updated?.title == "New Name") { "Renamed chat title mismatch" }

            val reloaded = repo.get("c-rename")
            check(reloaded?.title == "New Name") { "Reloaded chat title mismatch" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testDelete() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            repo.create(title = "To Delete", id = "c-del")

            check(repo.get("c-del") != null) { "Chat should exist before deletion" }

            val deleted = repo.delete("c-del")
            check(deleted) { "Delete should return true" }
            check(repo.get("c-del") == null) { "Chat should not exist after deletion" }

            val chatDir = File(tempDir, "chats_v2/c-del")
            check(!chatDir.exists()) { "Directory should be completely deleted" }
            check(repo.list().isEmpty()) { "List should be empty" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testAtomicWriteNoTempFilesRemain() {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val chat = repo.create(title = "Atomic Test", id = "c-atom")
            repo.append("c-atom", ChatMessage.user("Hello"))
            repo.writeMemory("c-atom", "Some memory notes")

            val chatDir = File(tempDir, "chats_v2/c-atom")
            val files = chatDir.listFiles() ?: emptyArray()
            val tempFiles = files.filter { it.name.contains(".tmp") }
            check(tempFiles.isEmpty()) { "Found leftover temp files: ${tempFiles.map { it.name }}" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun runAllTests() {
        testCreateAndGet()
        testListSortedByUpdatedAt()
        testAppendMessages()
        testSetMode()
        testSetPlan()
        testReadAndWriteMemory()
        testRename()
        testDelete()
        testAtomicWriteNoTempFilesRemain()
        println("All ChatRepository tests passed successfully!")
    }
}
