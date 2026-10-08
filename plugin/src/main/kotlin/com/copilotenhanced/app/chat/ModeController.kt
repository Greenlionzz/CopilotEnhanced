package com.copilotenhanced.app.chat

import java.io.File

class ModeController(
    initialState: ModeState = ModeState.IDLE,
    private val promptLoader: PromptLoader = PromptLoader()
) {

    var currentState: ModeState = initialState
        private set

    companion object {
        private val ALLOWED_TRANSITIONS: Map<ModeState, Set<ModeState>> = mapOf(
            ModeState.IDLE to setOf(
                ModeState.CLARIFYING,
                ModeState.PLAN_READY
            ),
            ModeState.CLARIFYING to setOf(
                ModeState.CLARIFYING,
                ModeState.PLAN_READY,
                ModeState.REJECTED,
                ModeState.IDLE
            ),
            ModeState.PLAN_READY to setOf(
                ModeState.APPROVED,
                ModeState.REVISING,
                ModeState.PLAN_READY,
                ModeState.REJECTED
            ),
            ModeState.REVISING to setOf(
                ModeState.PLAN_READY,
                ModeState.CLARIFYING,
                ModeState.REVISING,
                ModeState.REJECTED
            ),
            ModeState.APPROVED to setOf(
                ModeState.BUILDING,
                ModeState.REVISING,
                ModeState.PLAN_READY,
                ModeState.REJECTED
            ),
            ModeState.BUILDING to setOf(
                ModeState.VERIFYING,
                ModeState.BUILDING,
                ModeState.REVISING,
                ModeState.REJECTED,
                ModeState.DONE
            ),
            ModeState.VERIFYING to setOf(
                ModeState.DONE,
                ModeState.BUILDING,
                ModeState.VERIFYING,
                ModeState.REVISING,
                ModeState.REJECTED
            ),
            ModeState.DONE to setOf(
                ModeState.IDLE,
                ModeState.REVISING,
                ModeState.BUILDING
            ),
            ModeState.REJECTED to setOf(
                ModeState.IDLE,
                ModeState.CLARIFYING,
                ModeState.REVISING,
                ModeState.PLAN_READY
            )
        )
    }

    fun allowedTransitionsFrom(state: ModeState): Set<ModeState> {
        return ALLOWED_TRANSITIONS[state] ?: emptySet()
    }

    fun canTransition(from: ModeState, to: ModeState): Boolean {
        return ALLOWED_TRANSITIONS[from]?.contains(to) == true
    }

    fun canTransitionTo(target: ModeState): Boolean {
        return canTransition(currentState, target)
    }

    fun transition(to: ModeState): ModeState {
        if (!canTransition(currentState, to)) {
            throw IllegalStateException("Illegal state transition from $currentState to $to")
        }
        currentState = to
        return currentState
    }

    fun transition(from: ModeState, to: ModeState): ModeState {
        if (!canTransition(from, to)) {
            throw IllegalStateException("Illegal state transition from $from to $to")
        }
        currentState = to
        return currentState
    }

    fun reset(state: ModeState = ModeState.IDLE) {
        currentState = state
    }

    fun systemPromptFor(
        state: ModeState,
        chat: Chat,
        rootDir: File,
        skills: List<Skill> = emptyList(),
        memory: String = chat.memory
    ): String {
        val rawTemplate = if (state.isBuildPhase || chat.mode == ChatMode.BUILD) {
            promptLoader.loadBuildPrompt()
        } else {
            promptLoader.loadPlanPrompt()
        }
        val template = "PLUGIN_BUILD: ${com.copilotenhanced.app.settings.ProviderConfigStore.BUILD_STAMP}\n" + rawTemplate

        val planText = chat.plan?.takeIf { it.isNotBlank() } ?: "(No plan approved yet)"
        val memoryText = memory.ifBlank { chat.memory }.ifBlank { "(None)" }
        val lastUserMessage = chat.messages.lastOrNull { it.role == "user" }?.content ?: ""
        val skillsText = if (skills.isEmpty()) {
            "(None)"
        } else {
            skills.joinToString("\n\n") { skill ->
                val name = skill.name
                val desc = skill.description ?: "(no description)"
                val planStr = chat.plan ?: ""
                val shouldIncludeBody = planStr.contains(name, ignoreCase = true) || lastUserMessage.contains(name, ignoreCase = true)
                buildString {
                    append("- **").append(name).append("**: ").append(desc)
                    if (shouldIncludeBody && skill.content.isNotBlank()) {
                        append("\n").append(skill.content.take(3000).trim())
                    }
                }
            }
        }
        val fileTreeText = getProjectFileTree(rootDir, maxDepth = 8, maxEntries = 300)
        val baseConventions = promptLoader.loadPrompt("PROJECT_CONVENTIONS.md")
        
        val tomlFiles = mutableMapOf<String, String>()
        val rootToml = File(rootDir, "module.toml")
        if (rootToml.exists() && rootToml.isFile) {
            tomlFiles["module.toml"] = rootToml.readText(Charsets.UTF_8).take(4000)
        }
        rootDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            val subToml = File(subDir, "module.toml")
            if (subToml.exists() && subToml.isFile) {
                tomlFiles["${subDir.name}/module.toml"] = subToml.readText(Charsets.UTF_8).take(4000)
            }
        }
        val conventionsText = buildString {
            append(baseConventions)
            for ((path, text) in tomlFiles) {
                append("\n\nCURRENT $path:\n")
                append(text)
            }
        }.ifBlank { "(none)" }

        val promptResult = template
            .replace("{{PLAN}}", planText)
            .replace("{{MEMORY}}", memoryText)
            .replace("{{SKILLS}}", skillsText)
            .replace("{{FILE_TREE}}", fileTreeText)
            .replace("{{TREE}}", fileTreeText)
            .replace("{{CONVENTIONS}}", conventionsText)

        val hasApprovedPlan = chat.plan != null && chat.plan!!.isNotBlank()
        val fixModeSuffix = if (hasApprovedPlan && (state == ModeState.DONE || state == ModeState.BUILDING)) {
            "\n\nFix mode: the app was already built. The user reports problems or asks for changes. Read only the files the errors or request mention, fix them with minimal edits using write_file, and then stop with a short summary."
        } else if (state == ModeState.DONE) {
            val createdFiles = getFilesCreatedInChat(chat)
            val filesListText = if (createdFiles.isEmpty()) "(None)" else createdFiles.joinToString("\n") { "- $it" }
            "\n\nFix mode: the app was already built. The user reports problems or asks for changes. Read the relevant files, fix them with minimal edits, do not rewrite unrelated files.\n\nFILES CREATED IN THIS CHAT:\n$filesListText"
        } else {
            ""
        }

        val latestRealUserMessage = chat.messages.lastOrNull { it.role == "user" && !it.hidden && it.content != "Continue with the next milestone." }?.content ?: ""
        val currentUserRequestSuffix = if (latestRealUserMessage.isNotBlank()) {
            "\n\nCURRENT USER REQUEST:\n" + latestRealUserMessage.take(6000)
        } else {
            ""
        }

        return promptResult + fixModeSuffix + currentUserRequestSuffix
    }

    fun getFilesCreatedInChat(chat: Chat): List<String> {
        val files = mutableSetOf<String>()
        for (msg in chat.messages) {
            msg.tool_calls?.forEach { tc ->
                if (tc.function.name == "write_file") {
                    try {
                        val json = org.json.JSONObject(tc.function.arguments)
                        val path = json.optString("path", "").trim()
                        if (path.isNotEmpty()) {
                            files.add(path)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        return files.sorted()
    }

    private fun getProjectFileTree(rootDir: File, maxDepth: Int = 8, maxEntries: Int = 300): String {
        val sb = StringBuilder()
        var count = 0
        fun listFiles(dir: File, indent: String = "", depth: Int = 0) {
            if (depth > maxDepth || count >= maxEntries) return
            val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
            for (f in files) {
                if (count >= maxEntries) break
                val name = f.name
                if (name == "build" || (name.startsWith(".") && name.length > 1)) continue
                sb.append(indent).append(name).append(if (f.isDirectory) "/" else "").append("\n")
                count++
                if (f.isDirectory && depth < maxDepth) {
                    listFiles(f, indent + "  ", depth + 1)
                }
            }
        }
        listFiles(rootDir)
        return sb.toString().ifBlank { "(None)" }
    }

    @JvmName("systemPromptForSkillStrings")
    fun systemPromptFor(
        state: ModeState,
        chat: Chat,
        rootDir: File,
        skills: List<String>,
        memory: String = chat.memory
    ): String = systemPromptFor(
        state = state,
        chat = chat,
        rootDir = rootDir,
        skills = skills.map { Skill(name = it, content = it) },
        memory = memory
    )

    fun systemPromptFor(
        state: ModeState,
        chat: Chat,
        rootDir: File,
        skills: String,
        memory: String = chat.memory
    ): String = systemPromptFor(
        state = state,
        chat = chat,
        rootDir = rootDir,
        skills = if (skills.isBlank()) emptyList() else listOf(Skill(name = "Skill", content = skills)),
        memory = memory
    )

    fun currentSystemPrompt(
        chat: Chat,
        rootDir: File,
        skills: List<Skill> = emptyList(),
        memory: String = chat.memory
    ): String = systemPromptFor(currentState, chat, rootDir, skills, memory)
}
