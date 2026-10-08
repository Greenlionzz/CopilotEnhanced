package com.copilotenhanced.app.ui

/**
 * Data model for a collapsible section in the implementation plan.
 */
data class PlanSection(
    val id: String,
    val title: String,
    val content: String,
    val level: Int = 2,
)

/**
 * State representing the Plan Card view.
 */
sealed interface PlanCardState {
    object Loading : PlanCardState
    data class Empty(val message: String = "No implementation plan available") : PlanCardState
    data class Error(val message: String, val onRetry: () -> Unit) : PlanCardState
    data class Content(
        val title: String,
        val version: Int,
        val rawContent: String,
        val sections: List<PlanSection>,
    ) : PlanCardState
}

/**
 * Formats a revision comment into the standard prompt string for Plan Mode.
 */
fun formatRevisionMessage(comment: String): String {
    return "Revise the plan: ${comment.trim()}"
}

/**
 * Computes the plan version, prioritizing explicit parsed versions or incrementing existing versions.
 */
fun computePlanVersion(existingVersion: Int, parsedVersion: Int?): Int {
    return when {
        parsedVersion != null && parsedVersion > 0 -> parsedVersion
        existingVersion > 0 -> existingVersion + 1
        else -> 1
    }
}

/**
 * Parses markdown plan text into collapsible sections based on Markdown headers (#, ##, ###).
 */
fun parsePlanSections(content: String): List<PlanSection> {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return emptyList()

    val lines = trimmed.lines()
    val sections = mutableListOf<PlanSection>()

    var currentTitle = "Overview"
    var currentLevel = 2
    val currentBodyLines = mutableListOf<String>()
    var sectionIndex = 0

    fun commitCurrentSection() {
        val body = currentBodyLines.joinToString("\n").trim()
        if (body.isNotEmpty() || currentTitle != "Overview" || sectionIndex > 0) {
            sections.add(
                PlanSection(
                    id = "section_$sectionIndex",
                    title = currentTitle,
                    content = body,
                    level = currentLevel,
                )
            )
            sectionIndex++
        }
        currentBodyLines.clear()
    }

    for (line in lines) {
        val lineTrimmed = line.trim()
        val isHeader = lineTrimmed.startsWith("#") && lineTrimmed.takeWhile { it == '#' }.length <= 3
                && lineTrimmed.indexOf(' ') in 1..4

        if (isHeader) {
            val level = lineTrimmed.takeWhile { it == '#' }.length
            val title = lineTrimmed.drop(level).trim()
            if (title.isNotEmpty()) {
                // If we had accumulated content or a previous section, commit it
                if (currentBodyLines.isNotEmpty() || (sections.isNotEmpty() || sectionIndex > 0)) {
                    commitCurrentSection()
                }
                currentTitle = title
                currentLevel = level
                continue
            }
        }
        currentBodyLines.add(line)
    }

    // Commit any trailing section
    if (currentBodyLines.isNotEmpty() || sections.isEmpty()) {
        commitCurrentSection()
    }

    // If only one section was parsed and title is Overview with empty content, return empty
    if (sections.size == 1 && sections.first().content.isEmpty() && sections.first().title == "Overview") {
        return emptyList()
    }

    return sections
}
