package com.copilotenhanced.app.chat

data class Skill(
    val name: String,
    val description: String? = null,
    val content: String = ""
) {

    fun format(): String = buildString {
        append("### ").append(name)
        if (!description.isNullOrBlank()) {
            append("\n").append(description)
        }
        if (content.isNotBlank()) {
            append("\n").append(content.trim())
        }
    }

    companion object {
        fun fromMarkdown(raw: String, fallbackName: String = "Skill"): Skill {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("---")) {
                return Skill(name = fallbackName, content = trimmed)
            }

            val secondSeparator = trimmed.indexOf("---", 3)
            if (secondSeparator == -1) {
                return Skill(name = fallbackName, content = trimmed)
            }

            val frontMatter = trimmed.substring(3, secondSeparator).trim()
            val body = trimmed.substring(secondSeparator + 3).trim()

            var name = fallbackName
            var description: String? = null

            for (line in frontMatter.lines()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    when (key) {
                        "name" -> if (value.isNotEmpty()) name = value
                        "description" -> if (value.isNotEmpty()) description = value
                    }
                }
            }

            return Skill(name = name, description = description, content = body)
        }
    }
}
