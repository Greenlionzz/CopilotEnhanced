package com.copilotenhanced.app.chat

import org.json.JSONObject
import java.io.File

data class SkillRecord(
    val file: File,
    val filename: String,
    val skill: Skill,
    var enabled: Boolean = true
)

class SkillStore(val dataDir: File) {

    val skillsDir: File = File(if (dataDir.name == "ai-studio" || dataDir.name == "skills") dataDir else File(dataDir, "ai-studio"), "skills").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val stateFile: File = File(skillsDir, "skills_state.json")
    private val sourcesFile: File = File(skillsDir, "skills_sources.json")

    private fun loadSources(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            if (sourcesFile.exists()) {
                val json = JSONObject(sourcesFile.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val url = json.optString(key, "")
                    if (url.isNotBlank()) {
                        map[key] = url
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return map
    }

    private fun saveSources(map: Map<String, String>) {
        try {
            val json = JSONObject()
            for ((k, v) in map) {
                json.put(k, v)
            }
            sourcesFile.writeText(json.toString())
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getSourceUrl(filename: String): String? {
        return loadSources()[filename]
    }

    fun setSourceUrl(filename: String, url: String) {
        val sources = loadSources()
        sources[filename] = url
        saveSources(sources)
    }

    private fun loadState(): MutableMap<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        try {
            if (stateFile.exists()) {
                val json = JSONObject(stateFile.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.optBoolean(key, true)
                }
            }
        } catch (e: Exception) {
            // ignore corrupt state
        }
        return map
    }

    private fun saveState(map: Map<String, Boolean>) {
        try {
            val json = JSONObject()
            for ((k, v) in map) {
                json.put(k, v)
            }
            stateFile.writeText(json.toString())
        } catch (e: Exception) {
            // ignore save error
        }
    }

    fun list(): List<SkillRecord> {
        val state = loadState()
        val files = skillsDir.listFiles { f -> f.isFile && f.extension.lowercase() == "md" && f.name != "skills_state.json" }
            ?: emptyArray()
        
        return files.sortedBy { it.name }.map { file ->
            val content = try { file.readText() } catch (e: Exception) { "" }
            val fallback = file.nameWithoutExtension
            val skill = Skill.fromMarkdown(content, fallbackName = fallback)
            val isEnabled = state[file.name] ?: true
            SkillRecord(
                file = file,
                filename = file.name,
                skill = skill,
                enabled = isEnabled
            )
        }
    }

    fun setEnabled(filename: String, enabled: Boolean) {
        val state = loadState()
        state[filename] = enabled
        saveState(state)
    }

    fun delete(filename: String) {
        try {
            val file = File(skillsDir, filename)
            if (file.exists()) {
                file.delete()
            }
            val state = loadState()
            state.remove(filename)
            saveState(state)

            val sources = loadSources()
            sources.remove(filename)
            saveSources(sources)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun importMarkdown(filename: String, markdown: String): SkillRecord {
        return importMarkdownWithSource(filename, markdown, null)
    }

    fun importMarkdownWithSource(filename: String, markdown: String, sourceUrl: String?): SkillRecord {
        val safeName = if (filename.endsWith(".md", ignoreCase = true)) filename else "$filename.md"
        val target = File(skillsDir, safeName)
        target.writeText(markdown)
        setEnabled(safeName, true)
        if (!sourceUrl.isNullOrBlank()) {
            setSourceUrl(safeName, sourceUrl)
        }
        val skill = Skill.fromMarkdown(markdown, fallbackName = target.nameWithoutExtension)
        return SkillRecord(
            file = target,
            filename = safeName,
            skill = skill,
            enabled = true
        )
    }

    fun importFile(sourceFile: File): SkillRecord {
        val name = sourceFile.name
        val markdown = sourceFile.readText()
        return importMarkdown(name, markdown)
    }

    fun getEnabledSkills(): List<Skill> {
        return list().filter { it.enabled }.map { it.skill }
    }
}
