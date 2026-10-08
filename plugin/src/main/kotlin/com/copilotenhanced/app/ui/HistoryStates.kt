package com.copilotenhanced.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HistoryLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Loading chats" },
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Loading history...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HistoryErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "⚠️",
                fontSize = 36.sp,
                modifier = Modifier.semantics { contentDescription = "Error loading chats" },
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = "Retry loading" },
                )
            }
        }
    }
}

@Composable
fun HistoryEmptyState(
    searchQuery: String,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = if (searchQuery.isNotBlank()) "🔍" else "💬",
                fontSize = 40.sp,
                modifier = Modifier.semantics { contentDescription = "No chats found" },
            )
            Text(
                text = if (searchQuery.isNotBlank()) "No matching chats" else "No conversations yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (searchQuery.isNotBlank()) {
                    "Try adjusting your search query."
                } else {
                    "Start a new chat to begin planning or building."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNewChat,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "+ Start New Chat",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { contentDescription = "Start new chat" },
                    )
                }
            }
        }
    }
}
