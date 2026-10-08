package com.copilotenhanced.app.ui

import com.copilotenhanced.app.chat.ChatMode
import com.copilotenhanced.app.chat.ChatRepository
import com.copilotenhanced.app.chat.ModeState
import com.copilotenhanced.app.llm.FakeLlmClient
import com.copilotenhanced.app.llm.LlmEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

class ChatViewModelTest {

    private fun createTempDir(): File {
        return Files.createTempDirectory("chat_vm_test").toFile()
    }

    fun runAllTests() {
        testInitialChatCreation()
        testSendMessageAndStreaming()
        testStopStreaming()
        testNewChatAndSwitchChat()
        testSwitchMode()
        testDeleteChat()
        testErrorHandling()
        testPlanModePhase1TransitionAndSystemPrompt()
        testPlanModeClarifyingQuestionsFlow()
        testPlanModePhase2PlanReadyAndSave()
        testPlanModePhase2ReviseFlow()
        testPlanModePhase2ProceedFlow()
        testPlanModePhase2RejectFlow()
        println("All ChatViewModel tests passed successfully!")
    }

    private fun testInitialChatCreation() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val fakeLlm = FakeLlmClient()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            val state = vm.uiState.value
            check(state.currentChat != null) { "Current chat should not be null" }
            check(state.messages.isEmpty()) { "Messages should initially be empty" }
            check(state.chatTitle == "New Chat") { "Title should default to New Chat" }
            check(state.currentMode == ChatMode.PLAN) { "Default mode should be PLAN" }
            check(state.historyList.size == 1) { "History should have 1 item" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testSendMessageAndStreaming() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val fakeLlm = FakeLlmClient(responseDeltas = listOf("Hello", " world", "!"))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("Create a counter app")

            // Wait for streaming coroutine on Dispatchers.IO to finish
            var waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            val state = vm.uiState.value
            println("DEBUG error: ${state.error}, streaming: ${state.isStreaming}")
            check(!state.isStreaming) { "Streaming should be finished" }
            check(state.messages.size == 2) { "Should have 2 messages (user + assistant), but got ${state.messages.size}" }
            check(state.messages[0].content == "Create a counter app") { "User message mismatch: ${state.messages[0].content}" }
            check(state.messages[1].content == "Hello world!") { "Assistant message mismatch: ${state.messages[1].content}" }
            check(state.chatTitle.contains("Create a counter app")) { "Auto-title mismatch: ${state.chatTitle}" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testStopStreaming() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val delayedFlowClient = object : com.copilotenhanced.app.llm.LlmClient {
                override fun stream(
                    messages: List<com.copilotenhanced.app.llm.ChatMessage>,
                    system: String?,
                    tools: List<com.copilotenhanced.app.llm.ToolDef>?
                ) = flow {
                    emit(LlmEvent.Delta("Chunk 1"))
                    delay(500)
                    emit(LlmEvent.Delta(" Chunk 2"))
                    delay(500)
                    emit(LlmEvent.Done)
                }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, delayedFlowClient, scope)

            vm.sendMessage("Tell me a story")
            delay(100)
            check(vm.uiState.value.isStreaming) { "Should be streaming" }

            vm.stopStreaming()
            delay(100)

            val state = vm.uiState.value
            check(!state.isStreaming) { "Streaming should be stopped" }
            check(state.messages.isNotEmpty()) { "Should have preserved messages" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testNewChatAndSwitchChat() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val fakeLlm = FakeLlmClient(responseDeltas = listOf("Response 1"))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("First chat message")
            delay(150)

            val firstChatId = vm.uiState.value.currentChat!!.id

            // Create new chat
            vm.newChat(ChatMode.BUILD)
            val secondChatId = vm.uiState.value.currentChat!!.id
            check(firstChatId != secondChatId) { "New chat should have different ID" }
            check(vm.uiState.value.messages.isEmpty()) { "New chat should have empty messages" }
            check(vm.uiState.value.currentMode == ChatMode.BUILD) { "New chat mode should be BUILD" }

            // Switch back to first chat
            vm.switchChat(firstChatId)
            check(vm.uiState.value.currentChat?.id == firstChatId) { "Should switch back to first chat" }
            check(vm.uiState.value.messages.isNotEmpty()) { "First chat messages should be restored" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testSwitchMode() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val fakeLlm = FakeLlmClient()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            check(vm.uiState.value.currentMode == ChatMode.PLAN)
            vm.switchMode(ChatMode.BUILD)
            check(vm.uiState.value.currentMode == ChatMode.BUILD)
            check(vm.uiState.value.currentChat?.mode == ChatMode.BUILD)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testDeleteChat() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val fakeLlm = FakeLlmClient()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            val firstChatId = vm.uiState.value.currentChat!!.id
            vm.newChat()
            val secondChatId = vm.uiState.value.currentChat!!.id

            check(vm.uiState.value.historyList.size == 2)
            vm.deleteChat(secondChatId)

            check(vm.uiState.value.historyList.size == 1)
            check(vm.uiState.value.currentChat?.id == firstChatId)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testErrorHandling() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val fakeLlm = FakeLlmClient(shouldError = true, errorMessage = "Rate limited")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("Trigger error")
            delay(150)

            val state = vm.uiState.value
            check(state.error == "Rate limited") { "Error message mismatch: ${state.error}" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testPlanModePhase1TransitionAndSystemPrompt() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val fakeLlm = FakeLlmClient(responseDeltas = listOf("Sure! Here are questions."))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            check(vm.uiState.value.currentMode == ChatMode.PLAN)
            check(vm.modeState == ModeState.IDLE)

            // When mode is PLAN and user sends an idea
            vm.sendMessage("I want to build a fitness tracker")

            // ModeController moves to CLARIFYING
            check(vm.modeState == ModeState.CLARIFYING) { "Mode state should be CLARIFYING but was ${vm.modeState}" }
            check(vm.uiState.value.modeState == ModeState.CLARIFYING)

            var waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            // The request uses the plan_mode system prompt
            val lastReq = fakeLlm.recordedRequests.lastOrNull()
            check(lastReq != null) { "A request should have been recorded" }
            check(lastReq.system != null) { "System prompt must not be null" }
            check(lastReq.system!!.contains("PLAN MODE") || lastReq.system!!.contains("PHASE 1: CLARIFY")) {
                "System prompt must use plan_mode prompt, but got: ${lastReq.system}"
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testPlanModeClarifyingQuestionsFlow() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val questionsXml = """<questions>{"questions":[{"id":"q1","question":"Target platform?","type":"single","options":["Phone","Tablet"],"allowCustom":true},{"id":"q2","question":"Storage?","type":"multi","options":["Room","Cloud"],"allowCustom":false}]}</questions>"""
            val fakeLlm = FakeLlmClient(responseDeltas = listOf("Let's clarify:\n", questionsXml))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("I want an offline habit tracker")

            var waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            check(vm.modeState == ModeState.CLARIFYING)
            val assistantMsg = vm.uiState.value.messages.last()
            check(assistantMsg.content.contains("<questions>")) { "Assistant message should contain questions block" }

            // Sending user answers as "Q: ... A: ..." lines
            val answerText = "Q: Target platform?\nA: Phone\nQ: Storage?\nA: Room"
            vm.sendMessage(answerText)

            check(vm.uiState.value.messages.last().content == answerText)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testPlanModePhase2PlanReadyAndSave() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val planXml = """<plan title="Habit Tracker" version="1">
## Architecture
Room + Jetpack Compose

## Implementation Steps
1. Database setup
2. UI screens
</plan>"""
            val fakeLlm = FakeLlmClient(responseDeltas = listOf("Here is the plan:\n", planXml))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("Create habit tracker plan")

            var waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            check(vm.modeState == ModeState.PLAN_READY) { "Expected PLAN_READY state, got ${vm.modeState}" }
            check(vm.uiState.value.modeState == ModeState.PLAN_READY) { "Expected PLAN_READY in uiState, got ${vm.uiState.value.modeState}" }

            val savedChat = repo.get(vm.uiState.value.currentChat!!.id)
            val nonNullChat = checkNotNull(savedChat) { "Saved chat should not be null" }
            val planContent = checkNotNull(nonNullChat.plan) { "Saved plan should not be null" }
            check(planContent.contains("Room + Jetpack Compose")) { "Plan content mismatch: $planContent" }
            check(nonNullChat.planVersion == 1) { "Plan version should be 1, got ${nonNullChat.planVersion}" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testPlanModePhase2ReviseFlow() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val plan1 = """<plan title="Habit Tracker" version="1">## Overview\nTrack habits.</plan>"""
            val plan2 = """<plan title="Habit Tracker">## Overview\nTrack habits with dark mode.</plan>"""

            val fakeLlm = FakeLlmClient(responseDeltas = listOf(plan1))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("Initial request")
            var waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            check(vm.modeState == ModeState.PLAN_READY)
            check(vm.uiState.value.currentChat?.planVersion == 1)
            val oldMessageCount = vm.uiState.value.messages.size

            // Update LLM to return plan 2
            fakeLlm.responseDeltas = listOf(plan2)

            // Trigger Revise
            vm.revisePlan("Add dark mode support")

            // Mode transitions to REVISING while processing
            check(vm.modeState == ModeState.REVISING || vm.modeState == ModeState.PLAN_READY)

            waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            // After new plan finishes, moves to PLAN_READY
            check(vm.modeState == ModeState.PLAN_READY) { "Expected PLAN_READY after revision, got ${vm.modeState}" }

            val updatedChat = repo.get(vm.uiState.value.currentChat!!.id)!!
            check(updatedChat.planVersion == 2) { "Expected planVersion 2, got ${updatedChat.planVersion}" }
            check(updatedChat.plan!!.contains("dark mode")) { "Expected updated plan content" }

            // Old plan message stays in history
            check(updatedChat.messages.size > oldMessageCount) { "History should retain old messages" }
            check(updatedChat.messages.any { it.content.contains("Revise the plan: Add dark mode support") }) {
                "Expected revision user prompt in message history"
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testPlanModePhase2ProceedFlow() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val plan1 = """<plan title="Habit Tracker" version="1">## Overview\nTrack habits.</plan>"""
            val fakeLlm = FakeLlmClient(responseDeltas = listOf(plan1))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("Generate plan")
            var waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            check(vm.modeState == ModeState.PLAN_READY)

            vm.proceedPlan()
            check(vm.modeState == ModeState.APPROVED) { "Expected APPROVED state, got ${vm.modeState}" }
            check(vm.uiState.value.modeState == ModeState.APPROVED)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun testPlanModePhase2RejectFlow() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repo = ChatRepository(tempDir)
            val plan1 = """<plan title="Habit Tracker" version="1">## Overview\nTrack habits.</plan>"""
            val fakeLlm = FakeLlmClient(responseDeltas = listOf(plan1))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val vm = ChatViewModel(repo, fakeLlm, scope)

            vm.sendMessage("Generate plan")
            var waited = 0
            while (vm.uiState.value.isStreaming && waited < 3000) {
                delay(50)
                waited += 50
            }

            check(vm.modeState == ModeState.PLAN_READY)

            vm.rejectPlan()
            check(vm.modeState == ModeState.REJECTED) { "Expected REJECTED state, got ${vm.modeState}" }
            check(vm.uiState.value.modeState == ModeState.REJECTED)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
