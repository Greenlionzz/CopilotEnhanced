package com.copilotenhanced.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.copilotenhanced.app.chat.Chat

@Composable
fun HistoryRenameDialog(
    chat: Chat,
    currentInput: String,
    onInputChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = currentInput,
                onValueChange = onInputChange,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = currentInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = currentInput.isNotBlank(),
            ) {
                Text(
                    text = "Rename",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = "Confirm rename" },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = "Cancel rename" },
                )
            }
        },
    )
}

@Composable
fun HistoryDeleteDialog(
    chat: Chat,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${chat.title.ifBlank { "Untitled" }}\"? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { contentDescription = "Confirm delete" },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = "Cancel delete" },
                )
            }
        },
    )
}
