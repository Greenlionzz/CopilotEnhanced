package com.copilotenhanced.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.chat.Chat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    history: List<Chat>,
    currentChatId: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onNewChat: () -> Unit,
    onRefresh: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var chatPendingDelete by remember { mutableStateOf<Chat?>(null) }
    var chatPendingRename by remember { mutableStateOf<Chat?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    val filteredChats = remember(history, searchQuery) {
        ChatHistoryLogic.filterChats(history, searchQuery)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text(
                                text = "←",
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { contentDescription = "Back" },
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(
                    onClick = onNewChat,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "+ New Chat",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { contentDescription = "New chat" },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .animateContentSize(),
                placeholder = {
                    Text(
                        text = "Search chats...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Text(
                        text = "🔍",
                        fontSize = 14.sp,
                        modifier = Modifier.semantics { contentDescription = "Search chats" },
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = "Clear search" },
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // State Handling: Loading, Error, Empty, Content
            when {
                isLoading && history.isEmpty() -> {
                    HistoryLoadingState(modifier = Modifier.weight(1f))
                }

                errorMessage != null && history.isEmpty() -> {
                    HistoryErrorState(
                        errorMessage = errorMessage,
                        onRetry = onRefresh,
                        modifier = Modifier.weight(1f),
                    )
                }

                filteredChats.isEmpty() -> {
                    HistoryEmptyState(
                        searchQuery = searchQuery,
                        onNewChat = onNewChat,
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredChats, key = { it.id }) { chat ->
                            val isCurrent = chat.id == currentChatId
                            var showItemMenu by remember { mutableStateOf(false) }

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            chatPendingDelete = chat
                                            false
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            chatPendingRename = chat
                                            renameInputText = chat.title
                                            false
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val isDelete = direction == SwipeToDismissBoxValue.EndToStart
                                    val bgColor = if (isDelete) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    }
                                    val textColor = if (isDelete) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(bgColor)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart,
                                    ) {
                                        Text(
                                            text = if (isDelete) "🗑 Delete" else "✏ Rename",
                                            color = textColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                },
                            ) {
                                HistoryChatCard(
                                    chat = chat,
                                    isCurrent = isCurrent,
                                    onSelect = { onSelectChat(chat.id) },
                                    onLongClick = { showItemMenu = true },
                                    onMenuToggle = { showItemMenu = !showItemMenu },
                                    showMenu = showItemMenu,
                                    onDismissMenu = { showItemMenu = false },
                                    onRenameRequested = {
                                        chatPendingRename = chat
                                        renameInputText = chat.title
                                    },
                                    onDeleteRequested = {
                                        chatPendingDelete = chat
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    chatPendingRename?.let { chat ->
        HistoryRenameDialog(
            chat = chat,
            currentInput = renameInputText,
            onInputChange = { renameInputText = it },
            onConfirm = { newTitle ->
                onRenameChat(chat.id, newTitle)
                chatPendingRename = null
            },
            onDismiss = { chatPendingRename = null },
        )
    }

    // Delete Confirmation Dialog
    chatPendingDelete?.let { chat ->
        HistoryDeleteDialog(
            chat = chat,
            onConfirm = {
                onDeleteChat(chat.id)
                chatPendingDelete = null
            },
            onDismiss = { chatPendingDelete = null },
        )
    }
}
