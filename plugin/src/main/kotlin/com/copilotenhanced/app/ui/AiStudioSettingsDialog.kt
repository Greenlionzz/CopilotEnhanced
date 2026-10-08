package com.copilotenhanced.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.llm.OpenAiCompatibleClient
import com.copilotenhanced.app.settings.ProviderConfig
import com.copilotenhanced.app.settings.ProviderConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiStudioSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialConfig = remember { ProviderConfigStore.loadFromContext(context) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var modelName by remember { mutableStateOf(initialConfig.modelName) }
    var tempX10 by remember { mutableIntStateOf(initialConfig.temperatureX10) }
    var projectRoot by remember { mutableStateOf(initialConfig.projectRoot) }
    var showApiKey by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "AI Studio Settings", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "Hide" else "Show", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Model Name
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model name") },
                    placeholder = { Text("model-name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Project Root
                OutlinedTextField(
                    value = projectRoot,
                    onValueChange = { projectRoot = it },
                    label = { Text("Project root folder (absolute path)") },
                    placeholder = { Text("/storage/emulated/0/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Temperature x10 Slider
                Text(
                    text = "Temperature x10: $tempX10 (${String.format("%.1f", tempX10 / 10f)})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = tempX10.toFloat(),
                    onValueChange = { tempX10 = it.toInt() },
                    valueRange = 0f..20f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Test Connection Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            isTesting = true
                            testStatus = null
                            scope.launch {
                                val currentConfig = ProviderConfig(
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    modelName = modelName.trim(),
                                    temperatureX10 = tempX10,
                                    projectRoot = projectRoot.trim(),
                                )
                                val result = withContext(Dispatchers.IO) {
                                    OpenAiCompatibleClient(currentConfig).testConnection()
                                }
                                testStatus = result
                                isTesting = false
                            }
                        },
                        enabled = !isTesting,
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test connection")
                    }
                }

                testStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("HTTP") || status.startsWith("Connection failed") || status.startsWith("Authentication") || status.startsWith("Resource not found") || status.startsWith("Rate limit") || status.startsWith("Provider server error") || status.startsWith("Request to") || status.startsWith("Base URL")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Build: ${com.copilotenhanced.app.settings.ProviderConfigStore.BUILD_STAMP}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = ProviderConfig(
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        modelName = modelName.trim(),
                        temperatureX10 = tempX10,
                        projectRoot = projectRoot.trim(),
                    )
                    ProviderConfigStore.saveToContext(context, updated)
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
