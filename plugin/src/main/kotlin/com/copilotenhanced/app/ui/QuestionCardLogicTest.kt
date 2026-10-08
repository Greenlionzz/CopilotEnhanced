package com.copilotenhanced.app.ui

import com.copilotenhanced.app.protocol.QuestionItem
import com.copilotenhanced.app.protocol.QuestionOption
import com.copilotenhanced.app.protocol.QuestionsPayload

class QuestionCardLogicTest {

    fun runAllTests() {
        testToggleOptionSingle()
        testToggleOptionMulti()
        testFormatAnswersBasic()
        testFormatAnswersWithCustomAndEmpty()
        println("All QuestionCardLogic tests passed successfully!")
    }

    private fun testToggleOptionSingle() {
        var selection = emptySet<String>()
        selection = toggleOption(selection, "Option A", isMulti = false)
        check(selection == setOf("Option A")) { "Expected Option A selected" }

        selection = toggleOption(selection, "Option B", isMulti = false)
        check(selection == setOf("Option B")) { "Expected Option B selected, replacing Option A" }

        selection = toggleOption(selection, "Option B", isMulti = false)
        check(selection.isEmpty()) { "Expected deselect on re-click" }
    }

    private fun testToggleOptionMulti() {
        var selection = emptySet<String>()
        selection = toggleOption(selection, "Option A", isMulti = true)
        check(selection == setOf("Option A")) { "Expected Option A selected" }

        selection = toggleOption(selection, "Option B", isMulti = true)
        check(selection == setOf("Option A", "Option B")) { "Expected both Option A and Option B" }

        selection = toggleOption(selection, "Option A", isMulti = true)
        check(selection == setOf("Option B")) { "Expected Option A removed" }
    }

    private fun testFormatAnswersBasic() {
        val questions = listOf(
            QuestionItem(
                id = "q1",
                question = "Which target platform?",
                type = "single",
                options = listOf(QuestionOption(label = "Android"), QuestionOption(label = "Desktop")),
            )
        )
        val selected = mapOf("q1" to setOf("Android"))
        val custom = emptyMap<String, String>()

        val formatted = formatAnswers(questions, selected, custom)
        check(formatted.contains("Q: Which target platform?")) { "Missing question text" }
        check(formatted.contains("A: Android")) { "Missing answer text" }
    }

    private fun testFormatAnswersWithCustomAndEmpty() {
        val questions = listOf(
            QuestionItem(
                id = "q1",
                question = "Architecture?",
                type = "single",
                options = listOf(QuestionOption(label = "MVVM")),
            ),
            QuestionItem(
                id = "q2",
                question = "Additional libs?",
                type = "multi",
                allowCustom = true,
                options = listOf(QuestionOption(label = "Ktor")),
            )
        )
        val selected = mapOf(
            "q1" to emptySet<String>(),
            "q2" to setOf("Ktor")
        )
        val custom = mapOf("q2" to "Coil")

        val formatted = formatAnswers(questions, selected, custom)
        check(formatted.contains("A: (none)")) { "Expected (none) for unselected q1" }
        check(formatted.contains("A: Ktor, Coil")) { "Expected Ktor and Coil for q2" }
    }
}
