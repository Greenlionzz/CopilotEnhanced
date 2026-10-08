package com.copilotenhanced.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.chat.ModeState

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

private val PlanIcon: ImageVector by lazy {
    buildVector("PlanIcon", "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z")
}

private val ExpandMoreIcon: ImageVector by lazy {
    buildVector("ExpandMore", "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z")
}

private val ExpandLessIcon: ImageVector by lazy {
    buildVector("ExpandLess", "M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z")
}

private val EditNoteIcon: ImageVector by lazy {
    buildVector("EditNote", "M3 10h11v2H3v-2zm0-4h11v2H3V6zm0 8h7v2H3v-2zm17.59-2.07l-1.42-1.42c-.2-.2-.51-.2-.71 0l-1.06 1.06 2.12 2.12 1.06-1.06c.2-.2.2-.51 0-.71zM11 19.5V22h2.5l7.06-7.06-2.12-2.12L11 19.5z")
}

private val CheckCircleIcon: ImageVector by lazy {
    buildVector("CheckCircle", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z")
}

private val CancelIcon: ImageVector by lazy {
    buildVector("Cancel", "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z")
}

private val ErrorOutlineIcon: ImageVector by lazy {
    buildVector("ErrorOutline", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z")
}

private val RefreshIcon: ImageVector by lazy {
    buildVector("Refresh", "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z")
}

private val SendIcon: ImageVector by lazy {
    buildVector("Send", "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z")
}

@Composable
fun PlanCard(
    title: String?,
    version: Int?,
    content: String,
    modeState: ModeState = ModeState.PLAN_READY,
    onRevise: (String) -> Unit = {},
    onProceed: () -> Unit = {},
    onReject: () -> Unit = {},
    onRetry: () -> Unit = {},
    onStartOver: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val displayTitle = title?.takeIf { it.isNotBlank() } ?: "Implementation Plan"
    val displayVersion = version ?: 1
    val sections = remember(content) { parsePlanSections(content) }

    val state: PlanCardState = when {
        content.isBlank() -> PlanCardState.Empty("Plan is currently empty")
        else -> PlanCardState.Content(
            title = displayTitle,
            version = displayVersion,
            rawContent = content,
            sections = sections,
        )
    }

    PlanCardView(
        state = state,
        modeState = modeState,
        onRevise = onRevise,
        onProceed = onProceed,
        onReject = onReject,
        onRetry = onRetry,
        onStartOver = onStartOver,
        modifier = modifier,
    )
}

@Composable
fun PlanCardView(
    state: PlanCardState,
    modeState: ModeState,
    onRevise: (String) -> Unit,
    onProceed: () -> Unit,
    onReject: () -> Unit,
    onRetry: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is PlanCardState.Loading -> {
                    PlanLoadingState()
                }
                is PlanCardState.Empty -> {
                    PlanEmptyState(message = state.message)
                }
                is PlanCardState.Error -> {
                    PlanErrorState(message = state.message, onRetry = state.onRetry)
                }
                is PlanCardState.Content -> {
                    PlanContentState(
                        state = state,
                        modeState = modeState,
                        onRevise = onRevise,
                        onProceed = onProceed,
                        onReject = onReject,
                        onRetry = onRetry,
                        onStartOver = onStartOver,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Generating Implementation Plan...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Structuring architecture and breakdown steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanEmptyState(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PlanIcon,
            contentDescription = "Empty Plan Icon",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "No Plan Found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ErrorOutlineIcon,
                contentDescription = "Error Icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = RefreshIcon,
                    contentDescription = "Retry Icon",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun PlanContentState(
    state: PlanCardState.Content,
    modeState: ModeState,
    onRevise: (String) -> Unit,
    onProceed: () -> Unit,
    onReject: () -> Unit,
    onRetry: () -> Unit,
    onStartOver: () -> Unit,
) {
    val expandedMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            state.sections.forEach { put(it.id, true) }
        }
    }
    var isReviseOpen by remember { mutableStateOf(false) }
    var revisionComment by remember { mutableStateOf("") }

    // Header: Icon + Title + Version chip
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Icon(
                imageVector = PlanIcon,
                contentDescription = "Plan Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = "v${state.version}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    // Status Banner if Approved or Rejected
    AnimatedVisibility(
        visible = modeState == ModeState.APPROVED,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CheckCircleIcon,
                    contentDescription = "Approved Checkmark",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Plan Approved",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }

    AnimatedVisibility(
        visible = modeState == ModeState.REJECTED,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = CancelIcon,
                        contentDescription = "Rejected Icon",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Plan Rejected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    text = "You can revise the plan, request a new approach, or start a new chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onStartOver,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(
                            imageVector = RefreshIcon,
                            contentDescription = "Start Over Icon",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Over")
                    }
                }
            }
        }
    }

    // Collapsible Markdown Sections Body (scrollable above buttons)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 350.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.sections.forEach { section ->
            val isExpanded = expandedMap[section.id] ?: true
            CollapsibleSectionItem(
                section = section,
                isExpanded = isExpanded,
                onToggle = { expandedMap[section.id] = !isExpanded },
            )
        }
    }

    // Revision Input Box
    AnimatedVisibility(
        visible = isReviseOpen,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = revisionComment,
                onValueChange = { revisionComment = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Describe changes (e.g. use Room, add unit tests)...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                maxLines = 4,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        isReviseOpen = false
                        revisionComment = ""
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val trimmed = revisionComment.trim()
                        if (trimmed.isNotEmpty()) {
                            onRevise(trimmed)
                            isReviseOpen = false
                            revisionComment = ""
                        }
                    },
                    enabled = revisionComment.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = SendIcon,
                        contentDescription = "Send Revision Icon",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Revision")
                }
            }
        }
    }

    // Action Buttons Stacked Vertically or Chosen Result Label
    if (modeState == ModeState.PLAN_READY) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Proceed Button (filled, primary color)
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = CheckCircleIcon,
                    contentDescription = "Proceed Plan Icon",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Proceed")
            }

            // 2. Revise Button (outlined)
            OutlinedButton(
                onClick = { isReviseOpen = !isReviseOpen },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Icon(
                    imageVector = EditNoteIcon,
                    contentDescription = "Revise Plan Icon",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Revise")
            }

            // 3. Reject Button (text button, error color)
            TextButton(
                onClick = onReject,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = CancelIcon,
                    contentDescription = "Reject Plan Icon",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reject")
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when (modeState) {
                ModeState.APPROVED -> MaterialTheme.colorScheme.primaryContainer
                ModeState.REJECTED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (modeState) {
                        ModeState.APPROVED -> CheckCircleIcon
                        ModeState.REJECTED -> CancelIcon
                        else -> EditNoteIcon
                    },
                    contentDescription = "Chosen Result Icon",
                    tint = when (modeState) {
                        ModeState.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
                        ModeState.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = when (modeState) {
                        ModeState.APPROVED -> "✓ Approved"
                        ModeState.REJECTED -> "✗ Rejected"
                        ModeState.REVISING -> "✎ Revising..."
                        else -> "Status: $modeState"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = when (modeState) {
                        ModeState.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
                        ModeState.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSectionItem(
    section: PlanSection,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (isExpanded) ExpandLessIcon else ExpandMoreIcon,
                    contentDescription = if (isExpanded) "Collapse section" else "Expand section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    MarkdownView(
                        content = section.content,
                        textColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
