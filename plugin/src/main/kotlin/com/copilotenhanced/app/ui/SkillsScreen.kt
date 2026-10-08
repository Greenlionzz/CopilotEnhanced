package com.copilotenhanced.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.copilotenhanced.app.chat.GitHubImporter
import com.copilotenhanced.app.chat.GitHubSkillItem
import com.copilotenhanced.app.chat.SkillRecord
import com.copilotenhanced.app.chat.SkillStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    skillStore: SkillStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var skills by remember { mutableStateOf<List<SkillRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var viewingSkill by remember { mutableStateOf<SkillRecord?>(null) }
    var deletingSkill by remember { mutableStateOf<SkillRecord?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Path & paste dialog state
    var manualPath by remember { mutableStateOf("") }
    var pastedMarkdown by remember { mutableStateOf("") }
    var pastedTitle by remember { mutableStateOf("") }

    // GitHub Repo import states
    var showRepoImportDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var isFetchingRepo by remember { mutableStateOf(false) }
    var fetchedRepoItems by remember { mutableStateOf<List<GitHubSkillItem>>(emptyList()) }
    var repoImportError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val loadSkills = {
        isLoading = true
        errorMessage = null
        try {
            skills = skillStore.list()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load skills"
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadSkills()
    }

    // System file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                var fileName = "imported_skill.md"
                cursor?.use {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && it.moveToFirst()) {
                        fileName = it.getString(nameIndex) ?: fileName
                    }
                }
                skillStore.importMarkdown(fileName, text)
                importStatus = "Imported via system file picker ($fileName)"
                loadSkills()
            } catch (e: Exception) {
                importStatus = "System file picker import failed: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Skills") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Text("←", fontSize = 20.sp)
                    }
                },
                actions = {
                    Button(
                        onClick = { showRepoImportDialog = true },
                        modifier = Modifier.padding(end = 4.dp).heightIn(min = 48.dp)
                    ) {
                        Text("Import from repo")
                    }
                    Button(
                        onClick = {
                            try {
                                filePickerLauncher.launch(arrayOf("*/*"))
                                importStatus = "Launched system file picker"
                            } catch (e: Exception) {
                                showImportDialog = true
                                importStatus = "System file picker unavailable; opened manual import dialog"
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp)
                    ) {
                        Text("+ Import .md")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = loadSkills,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                skills.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📄", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No skills imported yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Import markdown files to guide AI Studio Mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Import Markdown Skill")
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (importStatus != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("ℹ", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = importStatus ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { importStatus = null },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Text("✖", fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(skills, key = { it.filename }) { record ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewingSkill = record }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = record.skill.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!record.skill.description.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = record.skill.description ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = record.filename,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Switch(
                                            checked = record.enabled,
                                            onCheckedChange = { checked ->
                                                skillStore.setEnabled(record.filename, checked)
                                                loadSkills()
                                            },
                                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { deletingSkill = record },
                                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        ) {
                                            Text("🗑", fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // View text dialog
    if (viewingSkill != null) {
        val skill = viewingSkill!!
        AlertDialog(
            onDismissRequest = { viewingSkill = null },
            title = { Text(skill.skill.name) },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp).animateContentSize()) {
                    LazyColumn {
                        item {
                            if (!skill.skill.description.isNullOrBlank()) {
                                Text(
                                    text = "Description: ${skill.skill.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = skill.skill.content.ifBlank { skill.file.readText() },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewingSkill = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (deletingSkill != null) {
        val skill = deletingSkill!!
        AlertDialog(
            onDismissRequest = { deletingSkill = null },
            title = { Text("Delete skill?") },
            text = { Text("Are you sure you want to delete '${skill.skill.name}' (${skill.filename})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        skillStore.delete(skill.filename)
                        deletingSkill = null
                        loadSkills()
                        importStatus = "Deleted skill ${skill.filename}"
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingSkill = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import dialog (absolute path or paste markdown fallback)
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Markdown Skill") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    Text(
                        "System file picker was unavailable. Choose an import method:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = { Text("Absolute file path (.md)") },
                        placeholder = { Text("/storage/emulated/0/Download/skill.md") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val file = File(manualPath.trim())
                                if (file.exists() && file.isFile) {
                                    skillStore.importFile(file)
                                    importStatus = "Imported via absolute file path (${file.name})"
                                    showImportDialog = false
                                    manualPath = ""
                                    loadSkills()
                                } else {
                                    importStatus = "File not found at path: $manualPath"
                                }
                            } catch (e: Exception) {
                                importStatus = "Import from path failed: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Import from File Path")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = pastedTitle,
                        onValueChange = { pastedTitle = it },
                        label = { Text("Skill Title / Filename") },
                        placeholder = { Text("my_skill.md") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pastedMarkdown,
                        onValueChange = { pastedMarkdown = it },
                        label = { Text("Paste Markdown Content") },
                        placeholder = { Text("---\nname: My Skill\n---\nBody...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (pastedMarkdown.isNotBlank()) {
                                val fname = if (pastedTitle.isNotBlank()) pastedTitle else "pasted_skill.md"
                                skillStore.importMarkdown(fname, pastedMarkdown)
                                importStatus = "Imported via pasted markdown ($fname)"
                                showImportDialog = false
                                pastedMarkdown = ""
                                pastedTitle = ""
                                loadSkills()
                            } else {
                                importStatus = "Markdown content cannot be blank"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Save Pasted Markdown")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // GitHub repository import dialog
    if (showRepoImportDialog) {
        AlertDialog(
            onDismissRequest = { showRepoImportDialog = false },
            title = { Text("Import from GitHub Repository") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    Text(
                        "Paste a GitHub URL (e.g. https://github.com/owner/repo or with /tree/branch/path):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it },
                        label = { Text("GitHub URL") },
                        placeholder = { Text("https://github.com/owner/repo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val parsed = GitHubImporter.parseUrl(repoUrlInput)
                            if (parsed == null) {
                                repoImportError = "Invalid GitHub URL format."
                                return@Button
                            }
                            isFetchingRepo = true
                            repoImportError = null
                            fetchedRepoItems = emptyList()

                            coroutineScope.launch {
                                try {
                                    val items = withContext(Dispatchers.IO) {
                                        GitHubImporter.fetchCandidateSkills(parsed)
                                    }
                                    fetchedRepoItems = items
                                    isFetchingRepo = false
                                    if (items.isEmpty()) {
                                        repoImportError = "No SKILL.md or .md files found in this repository or path."
                                    }
                                } catch (e: Exception) {
                                    isFetchingRepo = false
                                    repoImportError = e.message ?: "Failed to fetch from repository."
                                }
                            }
                        },
                        enabled = !isFetchingRepo && repoUrlInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        if (isFetchingRepo) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Fetch Skills")
                        }
                    }

                    if (repoImportError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = repoImportError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (fetchedRepoItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Select skills to import:", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                            items(fetchedRepoItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { item.selected = !item.selected }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.selected,
                                        onCheckedChange = { checked ->
                                            item.selected = checked
                                            fetchedRepoItems = fetchedRepoItems.toList()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(item.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (fetchedRepoItems.any { it.selected }) {
                    Button(
                        onClick = {
                            val selected = fetchedRepoItems.filter { it.selected }
                            isFetchingRepo = true
                            coroutineScope.launch {
                                var importedCount = 0
                                try {
                                    withContext(Dispatchers.IO) {
                                        for (item in selected) {
                                            try {
                                                val markdown = GitHubImporter.downloadMarkdown(item.downloadUrl)
                                                skillStore.importMarkdownWithSource(item.name, markdown, item.sourceUrl)
                                                importedCount++
                                            } catch (e: Exception) {
                                                // skip failed
                                            }
                                        }
                                    }
                                    isFetchingRepo = false
                                    showRepoImportDialog = false
                                    repoUrlInput = ""
                                    fetchedRepoItems = emptyList()
                                    loadSkills()
                                    importStatus = "Successfully imported $importedCount skill(s) from repository."
                                } catch (e: Exception) {
                                    isFetchingRepo = false
                                    repoImportError = e.message ?: "Failed to import selected skills."
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Import Selected (${fetchedRepoItems.count { it.selected }})")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRepoImportDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
