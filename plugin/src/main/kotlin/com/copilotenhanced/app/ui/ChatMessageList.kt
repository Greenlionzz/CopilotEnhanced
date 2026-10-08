package com.copilotenhanced.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.chat.ChatMode
import com.copilotenhanced.app.chat.ModeState
import com.copilotenhanced.app.llm.ChatMessage

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    streamingText: String,
    progressMessage: String?,
    error: String?,
    mode: ChatMode,
    modeState: ModeState = ModeState.PLAN_READY,
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onSendHiddenMessage: (String) -> Unit = {},
    onRevisePlan: (String) -> Unit = {},
    onProceedPlan: () -> Unit = {},
    onRejectPlan: () -> Unit = {},
    onStartOver: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val visibleMessages = messages.filter { !it.hidden }

    // Auto-scroll on new messages or streaming chunks
    val totalItems = visibleMessages.size + (if (isStreaming || streamingText.isNotEmpty()) 1 else 0) + (if (error != null) 1 else 0)
    LaunchedEffect(totalItems, streamingText.length) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    if (visibleMessages.isEmpty() && !isStreaming) {
        EmptyChatWelcomeView(
            mode = mode,
            onSuggestionClick = onSuggestionClick,
            modifier = modifier,
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val firstAssistantIndex = visibleMessages.indexOfFirst { it.role == ChatMessage.ROLE_ASSISTANT }
            itemsIndexed(visibleMessages) { index, message ->
                if (message.role == ChatMessage.ROLE_USER) {
                    UserMessageBubble(message = message)
                } else if (message.role == ChatMessage.ROLE_ASSISTANT) {
                    val isFirstBuild = (mode == ChatMode.BUILD && index == firstAssistantIndex)
                    AssistantMessageBubble(
                        message = message,
                        modeState = modeState,
                        isFirstBuildMessage = isFirstBuild,
                        onSendMessage = onSendMessage,
                        onSendHiddenRetry = {
                            onSendHiddenMessage(INVALID_QUESTIONS_RETRY_MESSAGE)
                        },
                        onRevisePlan = onRevisePlan,
                        onProceedPlan = onProceedPlan,
                        onRejectPlan = onRejectPlan,
                        onStartOver = onStartOver,
                    )
                }
            }

            if (isStreaming || streamingText.isNotEmpty()) {
                item(key = "streaming_bubble") {
                    StreamingMessageBubble(
                        streamingText = streamingText,
                        progressMessage = progressMessage,
                        modeState = modeState,
                        onSendMessage = onSendMessage,
                        onRevisePlan = onRevisePlan,
                        onProceedPlan = onProceedPlan,
                        onRejectPlan = onRejectPlan,
                        onStartOver = onStartOver,
                    )
                }
            }

            if (error != null) {
                item(key = "error_bubble") {
                    ErrorMessageBubble(
                        errorMessage = error,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorMessageBubble(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Retry", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun EmptyChatWelcomeView(
    mode: ChatMode,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "✨",
            fontSize = 36.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (mode == ChatMode.PLAN) "Plan Mode" else "Build Mode",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (mode == ChatMode.PLAN) {
                "Design architecture, clarify requirements, and outline implementation milestones."
            } else {
                "Generate code, implement features, and execute project tasks."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
