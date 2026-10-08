package com.copilotenhanced.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.copilotenhanced.app.chat.ChatRepository
import com.copilotenhanced.app.llm.OpenAiCompatibleClient
import com.copilotenhanced.app.settings.ProviderConfigStore
import com.copilotenhanced.app.ui.AiStudioScreen
import com.copilotenhanced.app.ui.ChatHistoryScreen
import com.copilotenhanced.app.ui.ChatViewModel
import dev.ide.plugin.ui.Screen
import dev.ide.plugin.ui.ToolWindow
import dev.ide.plugin.ui.ToolWindowAnchor
import dev.ide.plugin.ui.UiPlugin
import dev.ide.plugin.ui.UiRegistration
import java.io.File

class CopilotEnhancedUiPlugin : UiPlugin {

    override val id = "ai-studio-mode"

    override fun contribute(ui: UiRegistration) {
        ui.toolWindow(
            ToolWindow(
                id = "ai-studio-mode.studio",
                title = "AI Studio",
                iconId = "sparkle",
                anchor = ToolWindowAnchor.LEFT,
                content = { _ ->
                    AiStudioScreen()
                }
            )
        )

        ui.screen(
            Screen(
                id = "ai-studio-mode.history",
                title = "History",
                content = { screenCtx ->
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val chatVm = remember {
                        val dataDir = File(context.filesDir, "ai-studio")
                        val repo = ChatRepository(dataDir)
                        val configStore = ProviderConfigStore(context = context)
                        val client = OpenAiCompatibleClient(configStore = configStore)
                        ChatViewModel(
                            repository = repo,
                            llmClient = client,
                            scope = scope,
                            context = context,
                        )
                    }
                    val uiState by chatVm.uiState.collectAsState()

                    ChatHistoryScreen(
                        history = uiState.historyList,
                        currentChatId = uiState.currentChat?.id,
                        isLoading = uiState.isLoadingHistory,
                        errorMessage = uiState.historyError,
                        onSelectChat = { chatId ->
                            chatVm.switchChat(chatId)
                            screenCtx.back()
                        },
                        onDeleteChat = { chatId ->
                            chatVm.deleteChat(chatId)
                        },
                        onRenameChat = { chatId, newTitle ->
                            chatVm.renameChat(chatId, newTitle)
                        },
                        onNewChat = {
                            chatVm.newChat()
                            screenCtx.back()
                        },
                        onRefresh = {
                            chatVm.refreshHistory()
                        },
                        onBack = {
                            screenCtx.back()
                        },
                    )
                }
            )
        )

        ui.screen(
            Screen(
                id = "ai-studio-mode.skills",
                title = "Skills",
                content = { screenCtx ->
                    val context = LocalContext.current
                    val dataDir = File(context.filesDir, "ai-studio")
                    val skillStore = remember { com.copilotenhanced.app.chat.SkillStore(dataDir) }
                    com.copilotenhanced.app.ui.SkillsScreen(
                        skillStore = skillStore,
                        onBack = { screenCtx.back() }
                    )
                }
            )
        )
    }
}
