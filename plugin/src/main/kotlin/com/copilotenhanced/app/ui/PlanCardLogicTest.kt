package com.copilotenhanced.app.ui

class PlanCardLogicTest {

    fun runAllTests() {
        testParsePlanSectionsWithHeaders()
        testParsePlanSectionsWithoutHeaders()
        testParsePlanSectionsEmpty()
        testFormatRevisionMessage()
        testComputePlanVersion()
        println("All PlanCardLogic tests passed successfully!")
    }

    private fun testParsePlanSectionsWithHeaders() {
        val markdown = """
            Here is the plan overview.
            
            ## Architecture & Design
            - MVVM Pattern
            - Room Database
            
            ## Implementation Steps
            1. Step one
            2. Step two
            
            ### Testing
            Write unit tests.
        """.trimIndent()

        val sections = parsePlanSections(markdown)
        check(sections.size == 4) { "Expected 4 sections, got ${sections.size}" }

        check(sections[0].title == "Overview") { "Expected Overview, got ${sections[0].title}" }
        check(sections[0].content.contains("Here is the plan overview.")) { "Content mismatch in section 0" }

        check(sections[1].title == "Architecture & Design") { "Expected Architecture & Design, got ${sections[1].title}" }
        check(sections[1].content.contains("- MVVM Pattern")) { "Content mismatch in section 1" }
        check(sections[1].level == 2) { "Expected level 2" }

        check(sections[2].title == "Implementation Steps") { "Expected Implementation Steps, got ${sections[2].title}" }
        check(sections[2].content.contains("1. Step one")) { "Content mismatch in section 2" }

        check(sections[3].title == "Testing") { "Expected Testing, got ${sections[3].title}" }
        check(sections[3].content.contains("Write unit tests.")) { "Content mismatch in section 3" }
        check(sections[3].level == 3) { "Expected level 3" }
    }

    private fun testParsePlanSectionsWithoutHeaders() {
        val plainText = "This is a simple plan without any markdown headers."
        val sections = parsePlanSections(plainText)
        check(sections.size == 1) { "Expected 1 section for plain text, got ${sections.size}" }
        check(sections[0].title == "Overview") { "Expected title Overview, got ${sections[0].title}" }
        check(sections[0].content == plainText) { "Expected plain text content match" }
    }

    private fun testParsePlanSectionsEmpty() {
        val emptySections = parsePlanSections("")
        check(emptySections.isEmpty()) { "Expected empty list for blank string" }

        val whitespaceSections = parsePlanSections("   \n\n  ")
        check(whitespaceSections.isEmpty()) { "Expected empty list for whitespace string" }
    }

    private fun testFormatRevisionMessage() {
        val msg = formatRevisionMessage("Use Room instead of SQLDelight")
        check(msg == "Revise the plan: Use Room instead of SQLDelight") { "Revision message mismatch: $msg" }

        val msgWithSpaces = formatRevisionMessage("   Add dark mode   ")
        check(msgWithSpaces == "Revise the plan: Add dark mode") { "Trim mismatch: $msgWithSpaces" }
    }

    private fun testComputePlanVersion() {
        check(computePlanVersion(0, null) == 1) { "Initial version should be 1" }
        check(computePlanVersion(1, null) == 2) { "Existing v1 without parsed should increment to 2" }
        check(computePlanVersion(1, 3) == 3) { "Explicit parsed version 3 should take precedence" }
        check(computePlanVersion(2, 0) == 3) { "Parsed 0 or invalid should increment existing 2 to 3" }
    }
}
