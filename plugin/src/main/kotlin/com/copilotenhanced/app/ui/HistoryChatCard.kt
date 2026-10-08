package com.copilotenhanced.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.chat.Chat
import com.copilotenhanced.app.chat.ChatMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryChatCard(
    chat: Chat,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onMenuToggle: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onRenameRequested: () -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title & Mode Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = chat.title.ifBlank { "Untitled Chat" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (chat.mode == ChatMode.PLAN) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .semantics {
                            contentDescription = "Chat mode ${if (chat.mode == ChatMode.PLAN) "Plan" else "Build"}"
                        },
                ) {
                    Text(
                        text = if (chat.mode == ChatMode.PLAN) "PLAN" else "BUILD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (chat.mode == ChatMode.PLAN) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Last Message Preview
            Text(
                text = ChatHistoryLogic.lastMessagePreview(chat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Time ago, msgs count, and quick options menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = ChatHistoryLogic.formatTimeAgo(chat.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "${chat.messages.size} msgs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Box {
                    IconButton(
                        onClick = onMenuToggle,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text(
                            text = "⋮",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics {
                                contentDescription = "Options for ${chat.title}"
                            },
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = onDismissMenu,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Rename",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                onDismissMenu()
                                onRenameRequested()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                onDismissMenu()
                                onDeleteRequested()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
}
