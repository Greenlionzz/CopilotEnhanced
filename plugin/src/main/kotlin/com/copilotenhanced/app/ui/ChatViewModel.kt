package com.copilotenhanced.app.ui

import com.copilotenhanced.app.BuildLoopManager
import com.copilotenhanced.app.chat.*
import com.copilotenhanced.app.llm.ChatMessage
import com.copilotenhanced.app.llm.LlmClient
import com.copilotenhanced.app.llm.LlmEvent
import com.copilotenhanced.app.protocol.ParsedPart
import com.copilotenhanced.app.protocol.ProtocolParser
import com.copilotenhanced.app.protocol.toRawText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ChatViewModel(
    private val repository: ChatRepository,
    private val llmClient: LlmClient,
    private val scope: CoroutineScope,
    private val context: android.content.Context? = null,
) {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private val modeController = ModeController()

    val modeState: ModeState get() = modeController.currentState

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("ChatViewModel", "Coroutine exception caught", throwable)
        val currentStreamingText = _uiState.value.streamingText.trim().take(100_000)
        val chat = _uiState.value.currentChat
        if (chat != null && currentStreamingText.isNotEmpty()) {
            try {
                val assistantMsg = ChatMessage.assistant(currentStreamingText)
                repository.append(chat.id, assistantMsg)
            } catch (_: Exception) {}
        }
        _uiState.update {
            it.copy(
                isStreaming = false,
                streamingText = "",
                progressMessage = null,
                error = throwable.message ?: "An unexpected error occurred",
                modeState = modeController.currentState,
            )
        }
    }

    init {
        loadInitialChat()
    }

    fun loadInitialChat() {
        try {
            val history = repository.list()
            if (history.isNotEmpty()) {
                val latest = history.first()
                val state = if (latest.modeState != ModeState.IDLE) {
                    latest.modeState
                } else if (latest.mode == ChatMode.PLAN) {
                    if (latest.plan != null) ModeState.PLAN_READY
                    else if (latest.messages.isNotEmpty()) ModeState.CLARIFYING
                    else ModeState.IDLE
                } else {
                    ModeState.BUILDING
                }
                modeController.reset(state)
                _uiState.update {
                    it.copy(
                        currentChat = latest,
                        messages = latest.messages,
                        currentMode = latest.mode,
                        chatTitle = latest.title,
                        historyList = history,
                        modeState = modeController.currentState,
                    )
                }
                if (latest.mode == ChatMode.BUILD && latest.modeState != ModeState.DONE) {
                    val milestones = extractMilestones(latest.messages, "")
                    val hasUnfinished = milestones.any { it.status.lowercase() != "done" }
                    if (hasUnfinished && BuildLoopManager.currentJob?.isActive != true) {
                        startBuildLoop(latest)
                    }
                }
            } else {
                openFreshEmptyChat()
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to load initial chat, opening fresh empty chat", e)
            openFreshEmptyChat()
        }
    }

    private fun openFreshEmptyChat() {
        try {
            val newChat = repository.create(title = "New Chat", mode = ChatMode.PLAN)
            modeController.reset(ModeState.IDLE)
            _uiState.update {
                it.copy(
                    currentChat = newChat,
                    messages = emptyList(),
                    currentMode = newChat.mode,
                    chatTitle = newChat.title,
                    historyList = listOf(newChat),
                    modeState = modeController.currentState,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to create fresh empty chat", e)
        }
    }

    fun refreshHistory() {
        try {
            _uiState.update { it.copy(isLoadingHistory = true, historyError = null) }
            val history = repository.list()
            _uiState.update {
                it.copy(
                    historyList = history,
                    isLoadingHistory = false,
                    historyError = null,
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoadingHistory = false,
                    historyError = e.message ?: "Failed to load chats",
                )
            }
        }
    }

    fun newChat(mode: ChatMode = _uiState.value.currentMode) {
        stopStreaming()
        val created = repository.create(title = "New Chat", mode = mode)
        val history = repository.list()
        modeController.reset(if (mode == ChatMode.PLAN) ModeState.IDLE else ModeState.BUILDING)
        _uiState.update {
            it.copy(
                currentChat = created,
                messages = emptyList(),
                currentMode = mode,
                chatTitle = created.title,
                streamingText = "",
                progressMessage = null,
                error = null,
                historyList = history,
                modeState = modeController.currentState,
            )
        }
    }

    fun switchChat(chatId: String) {
        if (_uiState.value.currentChat?.id == chatId) return
        stopStreaming()
        try {
            val chat = repository.get(chatId)
            if (chat == null) {
                openFreshEmptyChat()
                return
            }
            val history = repository.list()
            val state = if (chat.modeState != ModeState.IDLE) {
                chat.modeState
            } else if (chat.mode == ChatMode.PLAN) {
                if (chat.plan != null) ModeState.PLAN_READY
                else if (chat.messages.isNotEmpty()) ModeState.CLARIFYING
                else ModeState.IDLE
            } else {
                ModeState.BUILDING
            }
            modeController.reset(state)
            _uiState.update {
                it.copy(
                    currentChat = chat,
                    messages = chat.messages,
                    currentMode = chat.mode,
                    chatTitle = chat.title,
                    streamingText = "",
                    progressMessage = null,
                    error = null,
                    historyList = history,
                    modeState = modeController.currentState,
                )
            }
            if (chat.mode == ChatMode.BUILD && chat.modeState != ModeState.DONE) {
                val milestones = extractMilestones(chat.messages, "")
                val hasUnfinished = milestones.any { it.status.lowercase() != "done" }
                if (hasUnfinished && BuildLoopManager.currentJob?.isActive != true) {
                    startBuildLoop(chat)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to switch chat, opening fresh empty chat", e)
            openFreshEmptyChat()
        }
    }

    fun deleteChat(chatId: String) {
        val currentId = _uiState.value.currentChat?.id
        repository.delete(chatId)
        val history = repository.list()
        if (currentId == chatId) {
            stopStreaming()
            if (history.isNotEmpty()) {
                val next = history.first()
                val state = if (next.modeState != ModeState.IDLE) {
                    next.modeState
                } else if (next.mode == ChatMode.PLAN) {
                    if (next.plan != null) ModeState.PLAN_READY
                    else if (next.messages.isNotEmpty()) ModeState.CLARIFYING
                    else ModeState.IDLE
                } else {
                    ModeState.BUILDING
                }
                modeController.reset(state)
                _uiState.update {
                    it.copy(
                        currentChat = next,
                        messages = next.messages,
                        currentMode = next.mode,
                        chatTitle = next.title,
                        streamingText = "",
                        progressMessage = null,
                        error = null,
                        historyList = history,
                        modeState = modeController.currentState,
                    )
                }
            } else {
                val fresh = repository.create(title = "New Chat", mode = ChatMode.PLAN)
                modeController.reset(ModeState.IDLE)
                _uiState.update {
                    it.copy(
                        currentChat = fresh,
                        messages = emptyList(),
                        currentMode = ChatMode.PLAN,
                        chatTitle = fresh.title,
                        streamingText = "",
                        progressMessage = null,
                        error = null,
                        historyList = listOf(fresh),
                        modeState = modeController.currentState,
                    )
                }
            }
        } else {
            _uiState.update { it.copy(historyList = history) }
        }
    }

    fun switchMode(mode: ChatMode) {
        val chat = _uiState.value.currentChat ?: return
        val updated = repository.setMode(chat.id, mode) ?: chat.copy(mode = mode)
        modeController.reset(if (mode == ChatMode.PLAN) ModeState.IDLE else ModeState.BUILDING)
        _uiState.update {
            it.copy(
                currentChat = updated,
                currentMode = mode,
                modeState = modeController.currentState,
            )
        }
        refreshHistory()
    }

    fun renameChat(chatId: String, newTitle: String) {
        val updated = repository.rename(chatId, newTitle.trim())
        if (updated != null && _uiState.value.currentChat?.id == chatId) {
            _uiState.update { it.copy(currentChat = updated, chatTitle = updated.title) }
        }
        refreshHistory()
    }

    fun sendMessage(text: String, hidden: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _uiState.value.isStreaming) return

        val initialChat = _uiState.value.currentChat ?: repository.create(
            title = "New Chat",
            mode = _uiState.value.currentMode,
        )

        // Plan mode Phase 1 transition: When mode is PLAN and user sends an idea, move to CLARIFYING
        if (!hidden && initialChat.mode == ChatMode.PLAN) {
            if (modeController.currentState == ModeState.IDLE || modeController.currentState == ModeState.REJECTED) {
                if (modeController.canTransitionTo(ModeState.CLARIFYING)) {
                    modeController.transition(ModeState.CLARIFYING)
                }
            }
        }

        // Auto-title if it's the first message or title is default
        val titledChat = if (!hidden && (initialChat.messages.isEmpty() || initialChat.title == "New Chat")) {
            val autoTitle = generateTitle(trimmed)
            repository.rename(initialChat.id, autoTitle) ?: initialChat.copy(title = autoTitle)
        } else {
            initialChat
        }

        val isCompilerOutput = !hidden && trimmed.contains(".kt:") && (trimmed.contains("error", ignoreCase = true) || trimmed.contains("e: "))
        var currentChat = titledChat
        if (isCompilerOutput) {
            val hiddenInstructionMsg = ChatMessage.user("These are compiler errors from the IDE build. Find the files and fix them.", hidden = true)
            currentChat = repository.append(currentChat.id, hiddenInstructionMsg) ?: currentChat.copy(messages = currentChat.messages + hiddenInstructionMsg)
        }

        val userMessage = ChatMessage.user(trimmed, hidden = hidden)
        currentChat = repository.append(currentChat.id, userMessage) ?: currentChat.copy(
            messages = currentChat.messages + userMessage,
        )

        _uiState.update {
            it.copy(
                currentChat = currentChat,
                messages = currentChat.messages,
                chatTitle = currentChat.title,
                isStreaming = true,
                streamingText = "",
                progressMessage = "Thinking...",
                error = null,
                modeState = modeController.currentState,
            )
        }

        val hasApprovedPlan = currentChat.plan != null || currentChat.mode == ChatMode.BUILD || modeController.currentState.isBuildPhase || modeController.currentState == ModeState.DONE
        if (hasApprovedPlan) {
            if (modeController.currentState == ModeState.DONE) {
                try {
                    if (modeController.canTransitionTo(ModeState.BUILDING)) {
                        modeController.transition(ModeState.BUILDING)
                        repository.setModeState(currentChat.id, ModeState.BUILDING)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Transition error on sendMessage", e)
                    _uiState.update { it.copy(error = e.message ?: "State transition failed") }
                }
            }
            startBuildLoop(currentChat)
        } else {
            startLlmStream(currentChat)
        }
    }

    fun retryLastMessage() {
        clearError()
        val chat = _uiState.value.currentChat ?: return
        if (chat.messages.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    streamingText = "",
                    progressMessage = "Thinking...",
                    error = null,
                )
            }
            val hasApprovedPlan = chat.plan != null || chat.mode == ChatMode.BUILD || modeController.currentState.isBuildPhase || modeController.currentState == ModeState.DONE
            if (hasApprovedPlan) {
                if (modeController.currentState == ModeState.DONE) {
                    try {
                        if (modeController.canTransitionTo(ModeState.BUILDING)) {
                            modeController.transition(ModeState.BUILDING)
                            repository.setModeState(chat.id, ModeState.BUILDING)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Transition error on retry", e)
                        _uiState.update { it.copy(error = e.message ?: "State transition failed") }
                    }
                }
                startBuildLoop(chat)
            } else {
                startLlmStream(chat)
            }
        }
    }

    private fun startLlmStream(chat: Chat, autoRetryCount: Int = 0) {
        streamJob?.cancel()
        val parser = ProtocolParser()
        val assembledParts = mutableListOf<ParsedPart>()
        var isCancelled = false
        var hasReceivedData = false

        streamJob = scope.launch(Dispatchers.IO + exceptionHandler) {
            val tokenBuffer = StringBuilder()
            var lastEmittedTime = 0L

            fun flushBuffer(force: Boolean = false) {
                try {
                    val text = tokenBuffer.toString()
                    if (text.isEmpty() && !force) return
                    val now = System.currentTimeMillis()
                    if (force || now - lastEmittedTime >= 80) {
                        lastEmittedTime = now
                        val capped = text.take(100_000)
                        _uiState.update {
                            it.copy(
                                streamingText = capped,
                                progressMessage = if (capped.isBlank()) "Thinking..." else null,
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error flushing buffer", e)
                }
            }

            val rootDir = try {
                context?.let { com.copilotenhanced.app.tools.ProjectTools.resolveProjectRoot(it) } ?: java.io.File(".")
            } catch (e: Exception) {
                java.io.File(".")
            }
            val systemPrompt = modeController.systemPromptFor(modeController.currentState, chat, rootDir, getEnabledSkills())
            val messagesToSend = prepareMessagesForLlm(chat)
            try {
                llmClient.stream(messages = messagesToSend, system = systemPrompt).collect { event ->
                    when (event) {
                        is LlmEvent.Delta -> {
                            hasReceivedData = true
                            tokenBuffer.append(event.text)
                            try {
                                val newParts = parser.feed(event.text)
                                assembledParts.addAll(newParts)
                            } catch (e: Exception) {
                                android.util.Log.e("ChatViewModel", "ProtocolParser feed error", e)
                            }
                            flushBuffer(force = false)
                        }
                        is LlmEvent.Reasoning -> {
                            hasReceivedData = true
                            _uiState.update {
                                it.copy(
                                    progressMessage = event.text,
                                )
                            }
                        }
                        is LlmEvent.Done -> {}
                        is LlmEvent.Error -> {
                            _uiState.update { it.copy(error = event.message) }
                        }
                        is LlmEvent.ToolCall -> {}
                        else -> {}
                    }
                }
                flushBuffer(force = true)
                try {
                    val flushedParts = parser.flush()
                    assembledParts.addAll(flushedParts)
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "ProtocolParser flush error", e)
                }
            } catch (e: CancellationException) {
                isCancelled = true
                flushBuffer(force = true)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Stream collection error", e)
                _uiState.update { it.copy(error = e.message ?: "Streaming failed") }
                flushBuffer(force = true)
            } finally {
                try {
                    if (assembledParts.isEmpty()) {
                        val fullText = tokenBuffer.toString().take(100_000).trim()
                        if (fullText.isNotEmpty()) {
                            assembledParts.add(ParsedPart.Text(fullText))
                        }
                    }

                    val planPart = assembledParts.filterIsInstance<ParsedPart.Plan>().lastOrNull()
                    var planUpdatedChat: Chat? = null
                    if (planPart != null) {
                        val newVersion = computePlanVersion(chat.planVersion, planPart.version)
                        planUpdatedChat = repository.setPlan(chat.id, planPart.content, newVersion)
                        try {
                            if (!modeController.canTransitionTo(ModeState.PLAN_READY)) {
                                throw IllegalStateException("Cannot transition from ${modeController.currentState} to PLAN_READY")
                            }
                            modeController.transition(ModeState.PLAN_READY)
                            if (planUpdatedChat != null) {
                                planUpdatedChat = repository.setModeState(planUpdatedChat.id, ModeState.PLAN_READY) ?: planUpdatedChat
                            }
                        } catch (e: IllegalStateException) {
                            android.util.Log.e("ChatViewModel", "Illegal state transition on PLAN_READY", e)
                            _uiState.update { it.copy(error = e.message ?: "Illegal state transition") }
                        }
                    }

                    val targetChat = planUpdatedChat ?: chat
                    val finalText = assembledParts.joinToString("") { it.toRawText() }.take(100_000).trim()
                    val hasInvalidQuestions = assembledParts.any { it is ParsedPart.InvalidQuestions }

                    if (!isCancelled && hasInvalidQuestions && autoRetryCount < 1 && _uiState.value.error == null) {
                        val assistantMsg = ChatMessage.assistant(finalText)
                        val savedWithAssistant = repository.append(targetChat.id, assistantMsg) ?: targetChat.copy(
                            messages = targetChat.messages + assistantMsg
                        )
                        val hiddenRetryMsg = ChatMessage.user(
                            content = INVALID_QUESTIONS_RETRY_MESSAGE,
                            hidden = true
                        )
                        val savedWithRetry = repository.append(savedWithAssistant.id, hiddenRetryMsg) ?: savedWithAssistant.copy(
                            messages = savedWithAssistant.messages + hiddenRetryMsg
                        )
                        _uiState.update {
                            it.copy(
                                currentChat = savedWithRetry,
                                messages = savedWithRetry.messages,
                                isStreaming = true,
                                streamingText = "",
                                progressMessage = "Thinking...",
                                error = null,
                                modeState = modeController.currentState,
                            )
                        }
                        startLlmStream(savedWithRetry, autoRetryCount = autoRetryCount + 1)
                    } else {
                        if (_uiState.value.error == null && finalText.isEmpty() && !hasReceivedData) {
                            _uiState.update { it.copy(error = "The model returned nothing") }
                        }

                        if (finalText.isNotEmpty() && _uiState.value.error == null) {
                            val assistantMsg = ChatMessage.assistant(finalText)
                            val saved = repository.append(targetChat.id, assistantMsg)
                            _uiState.update {
                                it.copy(
                                    currentChat = saved ?: (planUpdatedChat ?: it.currentChat),
                                    messages = saved?.messages ?: (it.messages + assistantMsg),
                                    isStreaming = false,
                                    streamingText = "",
                                    progressMessage = null,
                                    modeState = modeController.currentState,
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    currentChat = planUpdatedChat ?: it.currentChat,
                                    isStreaming = false,
                                    streamingText = "",
                                    progressMessage = null,
                                    modeState = modeController.currentState,
                                )
                            }
                        }
                        refreshHistory()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error in stream finally block", e)
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            progressMessage = null,
                            error = e.message ?: "Stream completion error",
                        )
                    }
                }
            }
        }
    }

    fun revisePlan(comment: String) {
        if (_uiState.value.isStreaming) return
        val trimmed = comment.trim()
        if (trimmed.isBlank()) return

        try {
            if (!modeController.canTransitionTo(ModeState.REVISING)) {
                throw IllegalStateException("Cannot transition from ${modeController.currentState} to REVISING")
            }
            modeController.transition(ModeState.REVISING)
            val chat = _uiState.value.currentChat
            if (chat != null) {
                val updated = repository.setModeState(chat.id, ModeState.REVISING) ?: chat
                _uiState.update {
                    it.copy(
                        currentChat = updated,
                        modeState = modeController.currentState,
                        error = null,
                    )
                }
            }
            refreshHistory()
        } catch (e: IllegalStateException) {
            android.util.Log.e("ChatViewModel", "Illegal state transition on revisePlan", e)
            _uiState.update { it.copy(error = e.message ?: "Illegal state transition") }
            return
        }

        val formatted = formatRevisionMessage(trimmed)
        sendMessage(formatted)
    }

    fun proceedPlan() {
        try {
            if (!modeController.canTransitionTo(ModeState.APPROVED)) {
                throw IllegalStateException("Cannot transition from ${modeController.currentState} to APPROVED")
            }
            val chat = _uiState.value.currentChat
            if (chat != null && !chat.filePermissionGranted) {
                _uiState.update { it.copy(showFilePermissionCard = true) }
                return
            }
            executeProceedPlan()
        } catch (e: IllegalStateException) {
            android.util.Log.e("ChatViewModel", "Illegal state transition on proceedPlan", e)
            _uiState.update { it.copy(error = e.message ?: "Illegal state transition") }
        }
    }

    private fun executeProceedPlan() {
        try {
            modeController.transition(ModeState.APPROVED)
            if (modeController.canTransitionTo(ModeState.BUILDING)) {
                modeController.transition(ModeState.BUILDING)
            }
            val chat = _uiState.value.currentChat ?: return
            val stateUpdated = repository.setModeState(chat.id, modeController.currentState) ?: chat
            val updated = repository.setMode(stateUpdated.id, ChatMode.BUILD) ?: stateUpdated.copy(mode = ChatMode.BUILD)
            
            val startMsg = ChatMessage.user("The plan is approved. Start building now, beginning with milestone M1.")
            val savedWithStart = repository.append(updated.id, startMsg) ?: updated.copy(messages = updated.messages + startMsg)

            _uiState.update {
                it.copy(
                    currentChat = savedWithStart,
                    messages = savedWithStart.messages,
                    currentMode = ChatMode.BUILD,
                    modeState = modeController.currentState,
                    error = null,
                    showFilePermissionCard = false,
                )
            }
            refreshHistory()
            startBuildLoop(savedWithStart)
        } catch (e: IllegalStateException) {
            android.util.Log.e("ChatViewModel", "Illegal state transition on proceedPlan", e)
            _uiState.update { it.copy(error = e.message ?: "Illegal state transition") }
        }
    }

    fun allowFilePermission() {
        val chat = _uiState.value.currentChat
        if (chat != null) {
            val updated = repository.setFilePermission(chat.id, true) ?: chat.copy(filePermissionGranted = true)
            _uiState.update { it.copy(currentChat = updated, showFilePermissionCard = false) }
            executeProceedPlan()
        } else {
            _uiState.update { it.copy(showFilePermissionCard = false) }
        }
    }

    fun denyFilePermission() {
        val chat = _uiState.value.currentChat
        if (chat != null) {
            repository.setFilePermission(chat.id, false)
        }
        _uiState.update {
            it.copy(
                showFilePermissionCard = false,
                error = "File permission denied: AI Studio Mode is not allowed to create or edit files in this project."
            )
        }
    }

    private fun compactHistoryForLlm(messages: List<ChatMessage>): List<ChatMessage> {
        data class ToolRoundInfo(
            val assistantIndex: Int,
            val toolIndices: List<Int>
        )

        val rounds = mutableListOf<ToolRoundInfo>()
        var idx = 0
        while (idx < messages.size) {
            val msg = messages[idx]
            if (msg.role == ChatMessage.ROLE_ASSISTANT && !msg.tool_calls.isNullOrEmpty()) {
                val tIndices = mutableListOf<Int>()
                var j = idx + 1
                while (j < messages.size && messages[j].role == ChatMessage.ROLE_TOOL) {
                    tIndices.add(j)
                    j++
                }
                rounds.add(ToolRoundInfo(idx, tIndices))
                idx = j
            } else {
                idx++
            }
        }

        val totalRounds = rounds.size
        val mutableMessages = messages.map { it.copy(tool_calls = it.tool_calls?.map { tc -> tc.copy(function = tc.function.copy()) }) }.toMutableList()

        for ((rIndex, round) in rounds.withIndex()) {
            val distance = totalRounds - rIndex

            if (distance > 6) {
                val assistantMsg = mutableMessages[round.assistantIndex]
                if (assistantMsg.tool_calls != null) {
                    val updatedCalls = assistantMsg.tool_calls.map { tc ->
                        if (tc.function.name == "write_file") {
                            try {
                                val json = org.json.JSONObject(tc.function.arguments)
                                val path = json.optString("path", "")
                                val sanitizedJson = org.json.JSONObject()
                                sanitizedJson.put("path", path)
                                sanitizedJson.put("content", "(content omitted)")
                                tc.copy(function = tc.function.copy(arguments = sanitizedJson.toString()))
                            } catch (_: Exception) {
                                tc
                            }
                        } else {
                            tc
                        }
                    }
                    mutableMessages[round.assistantIndex] = assistantMsg.copy(tool_calls = updatedCalls)
                }
            }

            if (distance > 12) {
                val assistantMsg = mutableMessages[round.assistantIndex]
                for (tIdx in round.toolIndices) {
                    val toolMsg = mutableMessages[tIdx]
                    val callId = toolMsg.tool_call_id
                    val matchingCall = assistantMsg.tool_calls?.find { it.id == callId }
                    if (matchingCall != null && matchingCall.function.name == "read_file") {
                        mutableMessages[tIdx] = toolMsg.copy(content = "(file was read earlier; read it again if you need it)")
                    }
                }
            }
        }

        return mutableMessages
    }

    private fun ensurePairSafe(messages: List<ChatMessage>): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        var i = 0
        while (i < messages.size) {
            val msg = messages[i]
            if (msg.role == ChatMessage.ROLE_ASSISTANT && !msg.tool_calls.isNullOrEmpty()) {
                val toolMessages = mutableListOf<ChatMessage>()
                var j = i + 1
                while (j < messages.size && messages[j].role == ChatMessage.ROLE_TOOL) {
                    toolMessages.add(messages[j])
                    j++
                }
                val isLastTurn = (j >= messages.size)
                if (toolMessages.isNotEmpty() || isLastTurn) {
                    result.add(msg)
                    result.addAll(toolMessages)
                    i = j
                } else {
                    i = j
                }
            } else if (msg.role == ChatMessage.ROLE_TOOL) {
                // Drop tool message whose assistant message was dropped
                i++
            } else {
                result.add(msg)
                i++
            }
        }
        return result
    }

    private fun parseCompilerErrorDigest(content: String): String {
        val lines = content.lines()
        val fileCounts = mutableMapOf<String, Int>()
        val unresolvedPerFile = mutableMapOf<String, MutableSet<String>>()
        val otherMessages = mutableListOf<Pair<String, String>>()

        val regex = Regex("""^(?:e:\s+|error:\s*)?(.+?):(\d+):(?:\d+:)?\s*(?:error:\s*)?(.*)$""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = regex.find(trimmed)
            if (match != null) {
                val path = match.groupValues[1].trim().removePrefix("./")
                val lineNum = match.groupValues[2].toIntOrNull() ?: 1
                val msg = match.groupValues[3].trim()

                fileCounts[path] = (fileCounts[path] ?: 0) + 1

                val unresolvedMatch = Regex("""Unresolved reference ['"]?([a-zA-Z0-9_.]+)['"]?""", RegexOption.IGNORE_CASE).find(msg)
                if (unresolvedMatch != null) {
                    val refName = unresolvedMatch.groupValues[1]
                    unresolvedPerFile.getOrPut(path) { mutableSetOf() }.add(refName)
                } else {
                    val loc = "$path:$lineNum"
                    if (!otherMessages.any { it.first == loc && it.second == msg }) {
                        otherMessages.add(loc to msg)
                    }
                }
            } else if (trimmed.startsWith("error:", ignoreCase = true) || trimmed.startsWith("e:", ignoreCase = true)) {
                val msg = trimmed.removePrefix("error:").removePrefix("e:").trim()
                val loc = "unknown:1"
                if (!otherMessages.any { it.second == msg }) {
                    otherMessages.add(loc to msg)
                }
            }
        }

        val totalCount = fileCounts.values.sum()
        if (totalCount == 0 && otherMessages.isEmpty()) {
            return content.take(6000)
        }

        val sb = StringBuilder()
        sb.append("Compiler Error Digest (Total errors: $totalCount):\n")
        sb.append("Files with errors: ").append(fileCounts.entries.joinToString(", ") { "${it.key} (${it.value})" }).append("\n")

        if (unresolvedPerFile.isNotEmpty()) {
            sb.append("Unresolved references:\n")
            for ((file, refs) in unresolvedPerFile) {
                sb.append("  - $file: Unresolved reference: ${refs.joinToString(", ")}\n")
            }
        }

        if (otherMessages.isNotEmpty()) {
            sb.append("Other errors (showing up to 40):\n")
            for ((loc, msg) in otherMessages.take(40)) {
                sb.append("  - $loc: $msg\n")
            }
        }

        val result = sb.toString()
        return if (result.length > 6000) result.take(6000) else result
    }

    private fun isCompilerOutput(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.contains(".kt:") && (trimmed.contains("error", ignoreCase = true) || trimmed.contains("e: "))
    }

    private fun prepareMessagesForLlm(chat: Chat, maxTail: Int = 25): List<ChatMessage> {
        val processedMessages = chat.messages.map { msg ->
            if (msg.role == "user" && (isCompilerOutput(msg.content) || msg.content.lines().any { it.trim().startsWith("error:") || it.trim().startsWith("e:") })) {
                val digest = parseCompilerErrorDigest(msg.content)
                val prefix = "These are compiler errors from the IDE build. Find the files and fix them. Missing R.string.* entries must be added to app/src/main/res/values/strings.xml.\n\n"
                msg.copy(content = prefix + digest)
            } else {
                msg
            }
        }

        val planApprovedMsg = processedMessages.firstOrNull { it.role == "user" && it.content.contains("The plan is approved") }
        val latestRealUserMsg = processedMessages.lastOrNull { it.role == "user" && !it.hidden && it.content != "Continue with the next milestone." }

        val tail = if (processedMessages.size > maxTail) processedMessages.takeLast(maxTail) else processedMessages
        val compacted = compactHistoryForLlm(tail)
        val pairSafe = ensurePairSafe(compacted)

        val result = mutableListOf<ChatMessage>()
        if (planApprovedMsg != null && !pairSafe.any { it.role == "user" && it.content == planApprovedMsg.content }) {
            result.add(planApprovedMsg)
        }
        result.addAll(pairSafe)
        if (latestRealUserMsg != null && !result.any { it.role == "user" && it.content == latestRealUserMsg.content }) {
            result.add(latestRealUserMsg)
        }
        return result
    }

    private fun startBuildLoop(initialChat: Chat) {
        BuildLoopManager.cancel()
        BuildLoopManager.currentJob = BuildLoopManager.scope.launch(exceptionHandler) {
            if (context == null) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        isPaused = true,
                        pauseMessage = "Project root not set. Set it in AI Studio Mode settings.",
                        error = "Project root not set. Set it in AI Studio Mode settings."
                    )
                }
                return@launch
            }

            val rootDir = try {
                com.copilotenhanced.app.tools.ProjectTools.resolveProjectRoot(context)
            } catch (e: Exception) {
                val err = "Project root error: ${e.message}"
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        isPaused = true,
                        pauseMessage = err,
                        error = err
                    )
                }
                return@launch
            }

            val fileList = rootDir.list()
            val entriesCount = fileList?.size ?: 0
            var probeResult = "ok"
            var probeException: Exception? = null
            val probeFile = java.io.File(rootDir, ".aistudio_probe")
            try {
                probeFile.writeText("probe", Charsets.UTF_8)
                probeFile.delete()
            } catch (e: Exception) {
                probeResult = "${e.javaClass.simpleName}: ${e.message}"
                probeException = e
            }

            if (probeException != null) {
                val errorCard = "root=${rootDir.absolutePath} entries=$entriesCount probe=$probeResult"
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        isPaused = true,
                        pauseMessage = errorCard,
                        error = errorCard
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    buildRootPath = rootDir.absolutePath,
                    buildEntriesCount = entriesCount,
                    buildProbeResult = probeResult
                )
            }

            val toolExecutor = com.copilotenhanced.app.tools.ProjectToolExecutor(rootDir)
            var currentChat = initialChat
            var round = 0
            var continueCount = 0
            val maxRounds = 60
            val maxContinue = 10

            val pathErrorOrNoChangeCounts = mutableMapOf<String, Int>()
            var warningCount = 0

            while (round < maxRounds && currentCoroutineContext().isActive) {
                round++
                _uiState.update {
                    it.copy(
                        isStreaming = true,
                        streamingText = "",
                        progressMessage = "Building (round $round of $maxRounds)...",
                        error = null,
                        isPaused = false,
                        pauseMessage = null,
                    )
                }

                val systemPrompt = modeController.systemPromptFor(modeController.currentState, currentChat, rootDir, getEnabledSkills())
                val messagesToSend = prepareMessagesForLlm(currentChat)

                val parser = ProtocolParser()
                val tokenBuffer = StringBuilder()
                val toolCallsCollected = mutableListOf<com.copilotenhanced.app.llm.ToolCallDto>()

                try {
                    llmClient.stream(
                        messages = messagesToSend,
                        system = systemPrompt,
                        tools = toolExecutor.definitions()
                    ).collect { event ->
                        when (event) {
                            is LlmEvent.Delta -> {
                                tokenBuffer.append(event.text)
                                try {
                                    parser.feed(event.text)
                                } catch (_: Exception) {}
                                _uiState.update { it.copy(streamingText = tokenBuffer.toString().take(100_000)) }
                            }
                            is LlmEvent.Reasoning -> {
                                _uiState.update { it.copy(progressMessage = event.text) }
                            }
                            is LlmEvent.ToolCall -> {
                                toolCallsCollected.add(
                                    com.copilotenhanced.app.llm.ToolCallDto(
                                        id = event.id,
                                        type = "function",
                                        function = com.copilotenhanced.app.llm.FunctionCallDto(
                                            name = event.name,
                                            arguments = event.argumentsJson
                                        )
                                    )
                                )
                            }
                            is LlmEvent.Done -> {}
                            is LlmEvent.Error -> {
                                _uiState.update { it.copy(error = event.message) }
                            }
                            else -> {}
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message ?: "Build stream failed") }
                    break
                }

                parser.flush()
                val finalText = tokenBuffer.toString().take(100_000).trim()

                if (toolCallsCollected.isEmpty() && finalText.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            isPaused = false,
                            streamingText = "",
                            progressMessage = null,
                            error = "The model returned nothing",
                        )
                    }
                    break
                }

                if (toolCallsCollected.isNotEmpty()) {
                    val assistantMsg = ChatMessage.assistantToolCalls(toolCallsCollected)
                    currentChat = repository.append(currentChat.id, assistantMsg) ?: currentChat.copy(messages = currentChat.messages + assistantMsg)

                    var updatedMessages = currentChat.messages
                    var pausedByLoopGuard = false
                    var pauseMsgText: String? = null

                    for (tc in toolCallsCollected) {
                        var toolPath: String? = null
                        try {
                            val json = org.json.JSONObject(tc.function.arguments)
                            if (json.has("path")) {
                                toolPath = json.optString("path", "").trim()
                            }
                        } catch (_: Exception) {}

                        val consecutiveErrorsOrNoChanges = if (toolPath != null) pathErrorOrNoChangeCounts.getOrDefault(toolPath, 0) else 0
                        val isRepeated = consecutiveErrorsOrNoChanges >= 3

                        val toolResult: String
                        if (isRepeated) {
                            warningCount++
                            if (toolPath != null) pathErrorOrNoChangeCounts[toolPath] = 0

                            if (warningCount >= 2) {
                                pausedByLoopGuard = true
                                pauseMsgText = "Build paused: repeated tool calls or excessive writes detected."
                            }
                            toolResult = "You already did this. Move on to the next step."
                        } else {
                            _uiState.update { it.copy(progressMessage = "Running ${tc.function.name}...") }
                            toolResult = toolExecutor.execute(tc.function.name, tc.function.arguments)

                            val isErrorOrNoChange = toolResult.startsWith("Error:") ||
                                                    toolResult.startsWith("Not found:") ||
                                                    toolResult.startsWith("Not allowed:") ||
                                                    toolResult.startsWith("No change:")

                            if (toolPath != null) {
                                if (isErrorOrNoChange) {
                                    val count = pathErrorOrNoChangeCounts.getOrDefault(toolPath, 0) + 1
                                    pathErrorOrNoChangeCounts[toolPath] = count
                                } else {
                                    pathErrorOrNoChangeCounts[toolPath] = 0
                                }
                            }
                        }

                        val toolMsg = ChatMessage.toolResult(tc.id, toolResult).copy(hidden = true)
                        updatedMessages = updatedMessages + toolMsg
                    }

                    currentChat = repository.updateMessages(currentChat.id, updatedMessages) ?: currentChat.copy(messages = updatedMessages)
                    // Save chat.json after every round
                    repository.updateMessages(currentChat.id, currentChat.messages)

                    _uiState.update {
                        it.copy(
                            currentChat = currentChat,
                            messages = currentChat.messages,
                            streamingText = "",
                            progressMessage = null,
                        )
                    }

                    if (pausedByLoopGuard) {
                        _uiState.update {
                            it.copy(
                                isStreaming = false,
                                isPaused = true,
                                pauseMessage = pauseMsgText ?: "Build paused due to repetition."
                            )
                        }
                        break
                    }

                    continue
                } else {
                    if (finalText.isNotEmpty()) {
                        val assistantMsg = ChatMessage.assistant(finalText)
                        currentChat = repository.append(currentChat.id, assistantMsg) ?: currentChat
                    }

                    // Save chat.json after every round
                    repository.updateMessages(currentChat.id, currentChat.messages)

                    val milestones = extractMilestones(currentChat.messages, finalText)
                    val hasUnfinishedMilestones = milestones.any { it.status.lowercase() != "done" }

                    if (hasUnfinishedMilestones && continueCount < maxContinue) {
                        continueCount++
                        val continueMsg = ChatMessage.user("Continue with the next milestone.", hidden = true)
                        currentChat = repository.append(currentChat.id, continueMsg) ?: currentChat
                        _uiState.update {
                            it.copy(
                                currentChat = currentChat,
                                messages = currentChat.messages,
                                streamingText = "",
                                progressMessage = "Continuing next milestone...",
                            )
                        }
                        continue
                    } else if (round >= maxRounds) {
                        _uiState.update {
                            it.copy(
                                currentChat = currentChat,
                                messages = currentChat.messages,
                                isStreaming = false,
                                isPaused = true,
                                pauseMessage = "Paused after 60 steps, Continue?",
                                streamingText = "",
                                progressMessage = null,
                            )
                        }
                        break
                    } else {
                        _uiState.update {
                            it.copy(
                                currentChat = currentChat,
                                messages = currentChat.messages,
                                isStreaming = false,
                                streamingText = "",
                                progressMessage = null,
                            )
                        }
                        break
                    }
                }
            }

            if (round >= maxRounds && !_uiState.value.isPaused) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        isPaused = true,
                        pauseMessage = "Paused after 60 steps, Continue?",
                        streamingText = "",
                        progressMessage = null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        progressMessage = null,
                    )
                }
            }
            refreshHistory()
        }
    }

    fun rejectPlan() {
        try {
            if (!modeController.canTransitionTo(ModeState.REJECTED)) {
                throw IllegalStateException("Cannot transition from ${modeController.currentState} to REJECTED")
            }
            modeController.transition(ModeState.REJECTED)
            val chat = _uiState.value.currentChat ?: return
            val updated = repository.setModeState(chat.id, ModeState.REJECTED) ?: chat
            _uiState.update {
                it.copy(
                    currentChat = updated,
                    modeState = modeController.currentState,
                    error = null,
                )
            }
            refreshHistory()
        } catch (e: IllegalStateException) {
            android.util.Log.e("ChatViewModel", "Illegal state transition on rejectPlan", e)
            _uiState.update { it.copy(error = e.message ?: "Illegal state transition") }
        }
    }

    fun continueBuild() {
        val chat = _uiState.value.currentChat ?: return
        _uiState.update { it.copy(isPaused = false, pauseMessage = null) }
        startBuildLoop(chat)
    }

    fun stopBuild() {
        BuildLoopManager.cancel()
        _uiState.update {
            it.copy(
                isStreaming = false,
                isPaused = false,
                pauseMessage = null,
                progressMessage = null,
            )
        }
    }

    private fun getEnabledSkills(): List<com.copilotenhanced.app.chat.Skill> {
        return try {
            val ctx = context ?: return emptyList()
            val dataDir = java.io.File(ctx.filesDir, "ai-studio")
            val store = com.copilotenhanced.app.chat.SkillStore(dataDir)
            store.getEnabledSkills()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        BuildLoopManager.cancel()
        if (_uiState.value.isStreaming) {
            streamJob = null
            val currentStreamingText = _uiState.value.streamingText.trim().take(100_000)
            val chat = _uiState.value.currentChat
            if (chat != null && currentStreamingText.isNotEmpty()) {
                val assistantMsg = ChatMessage.assistant(currentStreamingText)
                val saved = repository.append(chat.id, assistantMsg)
                _uiState.update {
                    it.copy(
                        currentChat = saved ?: chat,
                        messages = saved?.messages ?: (it.messages + assistantMsg),
                        isStreaming = false,
                        isPaused = false,
                        pauseMessage = null,
                        streamingText = "",
                        progressMessage = null,
                        modeState = modeController.currentState,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        isPaused = false,
                        pauseMessage = null,
                        streamingText = "",
                        progressMessage = null,
                        modeState = modeController.currentState,
                    )
                }
            }
            refreshHistory()
        } else {
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    isPaused = false,
                    pauseMessage = null,
                    streamingText = "",
                    progressMessage = null,
                )
            }
        }
    }

    fun setProgressMessage(msg: String?) {
        _uiState.update { it.copy(progressMessage = msg) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun generateTitle(firstMessage: String): String {
        val firstLine = firstMessage.lineSequence().firstOrNull()?.trim() ?: "New Chat"
        return if (firstLine.length > 36) {
            firstLine.take(33).trim() + "..."
        } else {
            firstLine
        }
    }
}
