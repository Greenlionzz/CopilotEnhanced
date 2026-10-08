package com.copilotenhanced.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.copilotenhanced.app.chat.ChatRepository
import com.copilotenhanced.app.llm.OpenAiCompatibleClient
import com.copilotenhanced.app.settings.ProviderConfigStore
import java.io.File

@Composable
fun AiStudioScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatVm = viewModel ?: remember {
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showSkillsScreen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (showSkillsScreen) {
            val dataDir = File(context.filesDir, "ai-studio")
            val skillStore = remember { com.copilotenhanced.app.chat.SkillStore(dataDir) }
            SkillsScreen(
                skillStore = skillStore,
                onBack = { showSkillsScreen = false }
            )
        } else if (showHistoryScreen) {
            ChatHistoryScreen(
                history = uiState.historyList,
                currentChatId = uiState.currentChat?.id,
                isLoading = uiState.isLoadingHistory,
                errorMessage = uiState.historyError,
                onSelectChat = { chatId ->
                    chatVm.switchChat(chatId)
                    showHistoryScreen = false
                },
                onDeleteChat = { chatId ->
                    chatVm.deleteChat(chatId)
                },
                onRenameChat = { chatId, newTitle ->
                    chatVm.renameChat(chatId, newTitle)
                },
                onNewChat = {
                    chatVm.newChat()
                    showHistoryScreen = false
                },
                onRefresh = {
                    chatVm.refreshHistory()
                },
                onBack = {
                    showHistoryScreen = false
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                ChatTopBar(
                    title = uiState.chatTitle,
                    currentMode = uiState.currentMode,
                    onModeToggle = { chatVm.switchMode(it) },
                    onNewChat = { chatVm.newChat() },
                    onOpenHistory = {
                        chatVm.refreshHistory()
                        showHistoryScreen = true
                    },
                    onOpenSkills = { showSkillsScreen = true },
                    onOpenSettings = { showSettingsDialog = true },
                )

                // Message List Body
                ChatMessageList(
                    messages = uiState.messages,
                    isStreaming = uiState.isStreaming,
                    streamingText = uiState.streamingText,
                    progressMessage = uiState.progressMessage,
                    error = uiState.error,
                    mode = uiState.currentMode,
                    modeState = uiState.modeState,
                    modifier = Modifier.weight(1f),
                    onSuggestionClick = { chatVm.sendMessage(it) },
                    onSendMessage = { chatVm.sendMessage(it) },
                    onSendHiddenMessage = { chatVm.sendMessage(it, hidden = true) },
                    onRevisePlan = { comment -> chatVm.revisePlan(comment) },
                    onProceedPlan = { chatVm.proceedPlan() },
                    onRejectPlan = { chatVm.rejectPlan() },
                    onStartOver = { chatVm.newChat() },
                    onRetry = { chatVm.retryLastMessage() },
                )

                // Milestone Checklist above composer
                if (uiState.isPaused) {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            androidx.compose.material3.Text(
                                text = uiState.pauseMessage ?: "Build paused",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = { chatVm.stopBuild() }
                                ) {
                                    androidx.compose.material3.Text("Stop")
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Button(
                                    onClick = { chatVm.continueBuild() }
                                ) {
                                    androidx.compose.material3.Text("Continue")
                                }
                            }
                        }
                    }
                }

                val milestones = remember(uiState.messages, uiState.streamingText) {
                    extractMilestones(uiState.messages, uiState.streamingText)
                }
                MilestoneChecklist(milestones = milestones)

                // Bottom Composer
                ChatComposer(
                    isStreaming = uiState.isStreaming,
                    onSendMessage = { chatVm.sendMessage(it) },
                    onStopStreaming = { chatVm.stopStreaming() },
                )
            }
        }

        // Settings Dialog
        if (showSettingsDialog) {
            AiStudioSettingsDialog(
                onDismiss = { showSettingsDialog = false },
            )
        }

        // File Permission Dialog
        if (uiState.showFilePermissionCard) {
            val rootDirResult = try {
                Result.success(com.copilotenhanced.app.tools.ProjectTools.resolveProjectRoot(context))
            } catch (e: Exception) {
                Result.failure(e)
            }
            val textContent = if (rootDirResult.isSuccess) {
                val rootDir = rootDirResult.getOrThrow()
                val rootName = rootDir.name
                "Allow AI Studio Mode to create and edit files in this project?\nProject: $rootName\nRoot: ${rootDir.absolutePath}"
            } else {
                val reason = rootDirResult.exceptionOrNull()?.message ?: "Unknown reason"
                "Project folder not found or not set. Set it in AI Studio Mode settings. ($reason)"
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { chatVm.denyFilePermission() },
                title = { androidx.compose.material3.Text("Permission Request") },
                text = { androidx.compose.material3.Text(textContent) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { chatVm.allowFilePermission() }) {
                        androidx.compose.material3.Text("Allow")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { chatVm.denyFilePermission() }) {
                        androidx.compose.material3.Text("Deny")
                    }
                }
            )
        }
    }
}
