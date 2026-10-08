package com.copilotenhanced.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.copilotenhanced.app.protocol.QuestionItem
import com.copilotenhanced.app.protocol.QuestionsPayload

/**
 * State representing the Option Selection Section.
 */
sealed interface QuestionCardState {
    object Loading : QuestionCardState
    data class Empty(val message: String = "No clarifying questions needed") : QuestionCardState
    data class Error(val message: String, val onRetry: () -> Unit) : QuestionCardState
    data class Content(val payload: QuestionsPayload) : QuestionCardState
}

// Logic extracted out of composables
fun formatAnswers(
    questions: List<QuestionItem>,
    selectedMap: Map<String, Set<String>>,
    customMap: Map<String, String>,
): String {
    return questions.mapIndexed { idx, q ->
        val qId = q.id.ifBlank { "q$idx" }
        val sel = selectedMap[qId] ?: emptySet()
        val cust = customMap[qId]?.trim().orEmpty()
        val combined = mutableListOf<String>()
        combined.addAll(sel)
        if (cust.isNotBlank()) combined.add(cust)
        val answerStr = if (combined.isEmpty()) "(none)" else combined.joinToString(", ")
        "Q: ${q.question}\nA: $answerStr"
    }.joinToString("\n")
}

fun toggleOption(
    currentSelection: Set<String>,
    optionKey: String,
    isMulti: Boolean,
): Set<String> {
    return if (isMulti) {
        if (currentSelection.contains(optionKey)) {
            currentSelection - optionKey
        } else {
            currentSelection + optionKey
        }
    } else {
        if (currentSelection.contains(optionKey)) {
            emptySet()
        } else {
            setOf(optionKey)
        }
    }
}

// Built-in Material 3 Vector Icons
private fun buildVector(name: String, pathData: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(androidx.compose.ui.graphics.Color.Black),
    ).build()
}

private val ChecklistIcon: ImageVector by lazy {
    buildVector("Checklist", "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 14l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z")
}

private val ErrorOutlineIcon: ImageVector by lazy {
    buildVector("ErrorOutline", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z")
}

private val RefreshIcon: ImageVector by lazy {
    buildVector("Refresh", "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z")
}

private val CheckSmallIcon: ImageVector by lazy {
    buildVector("CheckSmall", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z")
}

private val ArrowForwardIcon: ImageVector by lazy {
    buildVector("ArrowForward", "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z")
}

private val HelpOutlineIcon: ImageVector by lazy {
    buildVector("HelpOutline", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm1.07-7.75l-.9.92C12.45 12.9 12 13.5 12 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H7c0-2.76 2.24-5 5-5s5 2.24 5 5c0 1.04-.42 1.99-1.07 2.75z")
}

/**
 * Main QuestionCard component with support for all states:
 * Loading, Empty, Error, and Content.
 */
@Composable
fun QuestionCard(
    payload: QuestionsPayload?,
    onContinue: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    val state = remember(payload, isLoading, errorMessage) {
        when {
            isLoading -> QuestionCardState.Loading
            errorMessage != null -> QuestionCardState.Error(errorMessage, onRetry)
            payload == null || payload.questions.isEmpty() -> QuestionCardState.Empty()
            else -> QuestionCardState.Content(payload)
        }
    }

    QuestionCard(
        state = state,
        onContinue = onContinue,
        onSkip = onSkip,
        modifier = modifier,
    )
}

@Composable
fun QuestionCard(
    state: QuestionCardState,
    onContinue: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is QuestionCardState.Loading -> {
            QuestionCardLoading(modifier = modifier)
        }
        is QuestionCardState.Empty -> {
            QuestionCardEmpty(
                message = state.message,
                onSkip = onSkip,
                modifier = modifier,
            )
        }
        is QuestionCardState.Error -> {
            QuestionCardError(
                message = state.message,
                onRetry = state.onRetry,
                modifier = modifier,
            )
        }
        is QuestionCardState.Content -> {
            QuestionCardContent(
                payload = state.payload,
                onContinue = onContinue,
                onSkip = onSkip,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun QuestionCardLoading(
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp),
            )
            Text(
                text = "Preparing clarifying questions...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun QuestionCardEmpty(
    message: String,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = HelpOutlineIcon,
                contentDescription = "No questions needed",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSkip,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = ArrowForwardIcon,
                    contentDescription = "Continue without questions",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun QuestionCardError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = ErrorOutlineIcon,
                    contentDescription = "Error loading questions",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = RefreshIcon,
                        contentDescription = "Retry loading questions",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCardContent(
    payload: QuestionsPayload,
    onContinue: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedMap = remember { mutableStateMapOf<String, Set<String>>() }
    val customMap = remember { mutableStateMapOf<String, String>() }
    var submitted by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Title, icon and badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = ChecklistIcon,
                        contentDescription = "Clarifying Questions Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Clarifying Questions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${payload.questions.size} ${if (payload.questions.size == 1) "question" else "questions"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Questions list
            payload.questions.forEachIndexed { index, question ->
                val qId = question.id.ifBlank { "q$index" }
                val selectedSet = selectedMap[qId] ?: emptySet()
                val customText = customMap[qId].orEmpty()
                val isMulti = question.type.equals("multi", ignoreCase = true)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Question text and type badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${index + 1}. ${question.question}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMulti) "Select multiple" else "Select one",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Options chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        question.options.forEach { option ->
                            val optionKey = option.label
                            val isSelected = selectedSet.contains(optionKey)

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (!submitted) {
                                        val current = selectedMap[qId] ?: emptySet()
                                        selectedMap[qId] = toggleOption(current, optionKey, isMulti)
                                    }
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = CheckSmallIcon,
                                            contentDescription = "Option selected",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else if (!option.emoji.isNullOrBlank()) {
                                        Text(
                                            text = option.emoji,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                },
                                label = {
                                    Column(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (!option.description.isNullOrBlank()) {
                                            Text(
                                                text = option.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }

                    // Custom input field
                    if (question.allowCustom) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { if (!submitted) customMap[qId] = it },
                            placeholder = {
                                Text(
                                    text = "Or type your own answer",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            enabled = !submitted,
                            singleLine = true,
                        )
                    }
                }
            }

            // Bottom Actions: Skip and Continue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        if (!submitted) {
                            submitted = true
                            onSkip()
                        }
                    },
                    enabled = !submitted,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (!submitted) {
                            submitted = true
                            val formatted = formatAnswers(payload.questions, selectedMap, customMap)
                            onContinue(formatted)
                        }
                    },
                    enabled = !submitted,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = CheckSmallIcon,
                        contentDescription = "Confirm options",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
