package com.copilotenhanced.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.copilotenhanced.app.llm.ChatMessage
import com.copilotenhanced.app.protocol.ParsedPart
import com.copilotenhanced.app.protocol.ProtocolParser

data class MilestoneState(
    val milestone: String,
    val status: String, // "pending", "started", "done", "failed"
    val note: String
)

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

private val DoneIcon: ImageVector by lazy {
    buildVector("DoneIcon", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z")
}

private val RunIcon: ImageVector by lazy {
    buildVector("RunIcon", "M8 5v14l11-7z")
}

private val FailIcon: ImageVector by lazy {
    buildVector("FailIcon", "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z")
}

private val PendingIcon: ImageVector by lazy {
    buildVector("PendingIcon", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z")
}

fun extractMilestones(messages: List<ChatMessage>, streamingText: String): List<MilestoneState> {
    val parser = ProtocolParser()
    val allParts = mutableListOf<ParsedPart>()
    for (msg in messages) {
        if (msg.role == "assistant") {
            parser.reset()
            allParts.addAll(parser.feed(msg.content))
            allParts.addAll(parser.flush())
        }
    }
    if (streamingText.isNotBlank()) {
        parser.reset()
        allParts.addAll(parser.feed(streamingText))
        allParts.addAll(parser.flush())
    }

    val map = mutableMapOf<String, MilestoneState>()
    for (part in allParts) {
        if (part is ParsedPart.Progress) {
            val m = part.milestone ?: "M?"
            val s = part.status ?: "started"
            val note = part.note
            map[m] = MilestoneState(m, s, note)
        }
    }
    return map.values.sortedBy { it.milestone }
}

@Composable
fun MilestoneChecklist(
    milestones: List<MilestoneState>,
    modifier: Modifier = Modifier,
) {
    if (milestones.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Milestones",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (m in milestones) {
                    MilestoneChip(milestone = m)
                }
            }
        }
    }
}

@Composable
fun MilestoneChip(milestone: MilestoneState) {
    val (iconRes, tint) = when (milestone.status.lowercase()) {
        "done" -> DoneIcon to MaterialTheme.colorScheme.primary
        "started", "running" -> RunIcon to MaterialTheme.colorScheme.tertiary
        "failed" -> FailIcon to MaterialTheme.colorScheme.error
        else -> PendingIcon to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = iconRes,
            contentDescription = milestone.status,
            modifier = Modifier.size(16.dp),
            tint = tint
        )
        Text(
            text = "${milestone.milestone}: ${milestone.note.take(24)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
