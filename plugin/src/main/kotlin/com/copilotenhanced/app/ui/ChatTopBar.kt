package com.copilotenhanced.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.chat.ChatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    currentMode: ChatMode,
    onModeToggle: (ChatMode) -> Unit,
    onNewChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // First row: Title and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "✨",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = title.ifBlank { "AI Studio" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Actions: New Chat, History, Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // New Chat
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text("➕", fontSize = 14.sp)
                    }

                    // History
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text("🕒", fontSize = 15.sp)
                    }

                    // Skills
                    IconButton(
                        onClick = onOpenSkills,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text("📜", fontSize = 15.sp)
                    }

                    // Settings
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text("⚙", fontSize = 16.sp)
                    }
                }
            }

            // Second row: Mode selector chip (Plan / Build)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                ModeToggleChips(
                    currentMode = currentMode,
                    onModeSelected = onModeToggle,
                )
            }
        }
    }
}

@Composable
fun ModeToggleChips(
    currentMode: ChatMode,
    onModeSelected: (ChatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val modes = listOf(ChatMode.PLAN to "Plan", ChatMode.BUILD to "Build")
        modes.forEach { (mode, label) ->
            val isSelected = currentMode == mode
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                )
            }
        }
    }
}
