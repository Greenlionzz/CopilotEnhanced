package com.copilotenhanced.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.chat.ModeState
import com.copilotenhanced.app.llm.ChatMessage
import com.copilotenhanced.app.protocol.ParsedPart
import com.copilotenhanced.app.protocol.ProtocolParser
import com.copilotenhanced.app.protocol.toRawText
import java.io.File

const val INVALID_QUESTIONS_RETRY_MESSAGE =
    "Your last <questions> block was invalid. Resend only the corrected block in the required format."

@Composable
fun UserMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
fun AssistantMessageBubble(
    message: ChatMessage,
    modeState: ModeState = ModeState.PLAN_READY,
    isFirstBuildMessage: Boolean = false,
    onSendMessage: (String) -> Unit = {},
    onSendHiddenRetry: () -> Unit = {},
    onRevisePlan: (String) -> Unit = {},
    onProceedPlan: () -> Unit = {},
    onRejectPlan: () -> Unit = {},
    onStartOver: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val parser = remember { ProtocolParser() }
    val parts = remember(message.content) {
        parser.reset()
        parser.feed(message.content) + parser.flush()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!message.tool_calls.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (tc in message.tool_calls) {
                            val name = tc.function.name
                            val path = try {
                                val json = org.json.JSONObject(tc.function.arguments)
                                json.optString("path", json.optString("directory", ""))
                            } catch (_: Exception) {
                                ""
                            }
                            val label = if (path.isNotBlank()) "✓ $name $path" else "✓ $name"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                parts.forEach { part ->
                    when (part) {
                        is ParsedPart.Text -> {
                            if (part.content.isNotBlank()) {
                                MarkdownView(
                                    content = part.content,
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is ParsedPart.Plan -> {
                            PlanCard(
                                title = part.title,
                                version = part.version,
                                content = part.content,
                                modeState = modeState,
                                onRevise = onRevisePlan,
                                onProceed = onProceedPlan,
                                onReject = onRejectPlan,
                                onStartOver = onStartOver,
                            )
                        }
                        is ParsedPart.Progress -> {
                            MarkdownView(
                                content = part.toRawText(),
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is ParsedPart.Questions -> {
                            QuestionCard(
                                payload = part.data,
                                onContinue = { answers -> onSendMessage(answers) },
                                onSkip = { onSendMessage("Skip") },
                            )
                        }
                        is ParsedPart.InvalidQuestions -> {
                            InvalidQuestionsView(
                                rawText = part.raw,
                                onRetry = onSendHiddenRetry,
                            )
                        }
                    }
                }
                if (isFirstBuildMessage) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val rootDirResult = remember {
                        try {
                            Result.success(com.copilotenhanced.app.tools.ProjectTools.resolveProjectRoot(context))
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    if (rootDirResult.isSuccess) {
                        val rootDir = rootDirResult.getOrThrow()
                        val entryCount = remember(rootDir) { rootDir.list()?.size ?: 0 }
                        val conventionsText = remember {
                            com.copilotenhanced.app.chat.PromptLoader.loadPrompt("PROJECT_CONVENTIONS.md")
                        }
                        val conventionsMissing = conventionsText.contains("not found in resources")
                        Text(
                            text = "root=${rootDir.absolutePath} entries=$entryCount probe=ok" +
                                if (conventionsMissing) " PROJECT_CONVENTIONS.md not found in resources" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = androidx.compose.ui.graphics.Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        val reason = rootDirResult.exceptionOrNull()?.message ?: "Unknown reason"
                        val err = "Project folder not found or not set. Set it in AI Studio Mode settings. ($reason)"
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingMessageBubble(
    streamingText: String,
    progressMessage: String?,
    modeState: ModeState = ModeState.PLAN_READY,
    onSendMessage: (String) -> Unit = {},
    onRevisePlan: (String) -> Unit = {},
    onProceedPlan: () -> Unit = {},
    onRejectPlan: () -> Unit = {},
    onStartOver: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!progressMessage.isNullOrBlank()) {
                    ChatProgressLine(message = progressMessage)
                }
                if (streamingText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = streamingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        BlinkingCursor()
                    }
                } else if (progressMessage.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        BlinkingCursor()
                    }
                }
            }
        }
    }
}

@Composable
fun InvalidQuestionsView(
    rawText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkdownView(
                content = rawText,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

