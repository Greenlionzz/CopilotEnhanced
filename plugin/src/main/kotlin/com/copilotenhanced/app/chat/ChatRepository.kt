package com.copilotenhanced.app.chat

import com.copilotenhanced.app.llm.ChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class ChatRepository(val dataDir: File) {

    companion object {
        private const val CHATS_DIR_NAME = "chats_v2"
    }

    private val chatsDir: File = if (dataDir.name == CHATS_DIR_NAME) dataDir else File(dataDir, CHATS_DIR_NAME)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val lock = Any()

    init {
        if (!chatsDir.exists()) {
            chatsDir.mkdirs()
        }
    }

    fun create(
        title: String = "New Chat",
        mode: ChatMode = ChatMode.PLAN,
        id: String = UUID.randomUUID().toString()
    ): Chat = synchronized(lock) {
        val now = System.currentTimeMillis()
        val chat = Chat(
            id = id,
            title = title,
            mode = mode,
            messages = emptyList(),
            plan = null,
            planVersion = 0,
            memory = "",
            createdAt = now,
            updatedAt = now
        )
        val chatDir = getChatDir(id)
        if (!chatDir.exists()) {
            chatDir.mkdirs()
        }
        saveChatJson(chat)
        writeMemoryFile(id, "")
        chat
    }

    fun list(): List<Chat> = synchronized(lock) {
        try {
            if (!chatsDir.exists() || !chatsDir.isDirectory) {
                return emptyList()
            }
            val dirs = chatsDir.listFiles { file -> file.isDirectory && file.name != "_corrupt" } ?: return emptyList()
            dirs.mapNotNull { dir ->
                try {
                    get(dir.name)
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Corrupt chat ${dir.name}, moving to _corrupt", e)
                    moveChatToCorrupt(dir.name)
                    null
                }
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to list chats", e)
            emptyList()
        }
    }

    fun get(id: String): Chat? = synchronized(lock) {
        try {
            val chatDir = getChatDir(id)
            val chatJsonFile = File(chatDir, "chat.json")
            if (!chatJsonFile.exists() || !chatJsonFile.isFile) {
                return null
            }
            val content = chatJsonFile.readText(Charsets.UTF_8)
            val chat = json.decodeFromString<Chat>(content)
            val memoryFile = File(chatDir, "memory.md")
            val memoryText = if (memoryFile.exists() && memoryFile.isFile) {
                memoryFile.readText(Charsets.UTF_8)
            } else {
                chat.memory
            }
            chat.copy(memory = memoryText)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Corrupt chat $id, moving to _corrupt", e)
            moveChatToCorrupt(id)
            null
        }
    }

    private fun moveChatToCorrupt(id: String) {
        try {
            val chatDir = getChatDir(id)
            if (chatDir.exists() && chatDir.name != "_corrupt") {
                val corruptDir = File(chatsDir, "_corrupt/$id")
                corruptDir.parentFile?.mkdirs()
                Files.move(chatDir.toPath(), corruptDir.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to move corrupt chat $id to _corrupt", e)
            try {
                getChatDir(id).deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    fun append(id: String, message: ChatMessage): Chat? = synchronized(lock) {
        val existing = get(id) ?: return null
        val updated = existing.copy(
            messages = existing.messages + message,
            updatedAt = System.currentTimeMillis()
        )
        saveChatJson(updated)
        updated
    }

    fun updateMessages(id: String, messages: List<ChatMessage>): Chat? = synchronized(lock) {
        val existing = get(id) ?: return null
        val updated = existing.copy(
            messages = messages,
            updatedAt = System.currentTimeMillis()
        )
        saveChatJson(updated)
        updated
    }

    fun setMode(id: String, mode: ChatMode): Chat? = synchronized(lock) {
        val existing = get(id) ?: return null
        val updated = existing.copy(
            mode = mode,
            updatedAt = System.currentTimeMillis()
        )
        saveChatJson(updated)
        updated
    }

    fun setModeState(id: String, modeState: ModeState): Chat? = synchronized(lock) {
        val existing = get(id) ?: return null
        val updated = existing.copy(
            modeState = modeState,
            updatedAt = System.currentTimeMillis()
        )
        saveChatJson(updated)
        updated
    }

    fun setFilePermission(id: String, granted: Boolean): Chat? = synchronized(lock) {
        val existing = get(id) ?: return null
        val updated = existing.copy(
            filePermissionGranted = granted,
            updatedAt = System.currentTimeMillis()
        )
        saveChatJson(updated)
        updated
    }

    fun setPlan(id: String, plan: String?, version: Int? = null): Chat? = synchronized(lock) {
        val existing = get(id) ?: return null
        val newVersion = version ?: if (plan != null) {
            if (existing.planVersion > 0) existing.planVersion + 1 else 1
        } else {
            0
        }
        val updated = existing.copy(
            plan = plan,
            planVersion = newVersion,
            updatedAt = System.currentTimeMillis()
        )
        saveChatJson(updated)
        updated
    }

    fun readMemory(id: String): String = synchronized(lock) {
        val memoryFile = File(getChatDir(id), "memory.md")
        if (memoryFile.exists() && memoryFile.isFile) {
            memoryFile.readText(Charsets.UTF_8)
        } else {
            get(id)?.memory ?: ""
        }
    }

    fun writeMemory(id: String, text: String): Boolean = synchronized(lock) {
        val chatDir = getChatDir(id)
        if (!chatDir.exists()) {
            chatDir.mkdirs()
        }
        writeMemoryFile(id, text)
        val existing = get(id)
        if (existing != null) {
            val updated = existing.copy(
                memory = text,
                updatedAt = System.currentTimeMillis()
            )
            saveChatJson(updated)
        }
        true
    }

    fun rename(id: String, title: String): Chat? = synchronized(lock) {
        val existing = get(id) ?: return null
        val updated = existing.copy(
            title = title,
            updatedAt = System.currentTimeMillis()
        )
        saveChatJson(updated)
        updated
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val chatDir = getChatDir(id)
        if (!chatDir.exists()) {
            return false
        }
        chatDir.deleteRecursively()
    }

    private fun getChatDir(id: String): File = File(chatsDir, id)

    private fun saveChatJson(chat: Chat) {
        val chatDir = getChatDir(chat.id)
        if (!chatDir.exists()) {
            chatDir.mkdirs()
        }
        val chatJsonFile = File(chatDir, "chat.json")
        val jsonText = json.encodeToString(chat)
        writeAtomic(chatJsonFile, jsonText)
    }

    private fun writeMemoryFile(id: String, text: String) {
        val chatDir = getChatDir(id)
        if (!chatDir.exists()) {
            chatDir.mkdirs()
        }
        val memoryFile = File(chatDir, "memory.md")
        writeAtomic(memoryFile, text)
    }

    private fun writeAtomic(targetFile: File, content: String) {
        val parent = targetFile.parentFile ?: File(".")
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(parent, "${targetFile.name}.tmp.${UUID.randomUUID()}")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                try {
                    fos.getFD().sync()
                } catch (ignored: Exception) {
                }
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                if (!tempFile.renameTo(targetFile)) {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
