package com.copilotenhanced.app.chat

import java.io.ByteArrayInputStream
import java.io.InputStream

class ModeControllerTest {

    private class FakePromptLoader(
        private val planPromptText: String = "PLAN PROMPT:\n{{PLAN}}\n{{MEMORY}}\n{{SKILLS}}",
        private val buildPromptText: String = "BUILD PROMPT:\n{{PLAN}}\n{{MEMORY}}\n{{SKILLS}}"
    ) : PromptLoader() {
        override fun loadPlanPrompt(): String = planPromptText
        override fun loadBuildPrompt(): String = buildPromptText
        override fun loadPrompt(path: String): String = when {
            path.contains("plan") -> planPromptText
            path.contains("build") -> buildPromptText
            else -> "UNKNOWN PROMPT"
        }
    }

    fun testInitialState() {
        val controller = ModeController()
        check(controller.currentState == ModeState.IDLE) { "Default state must be IDLE" }

        val custom = ModeController(initialState = ModeState.PLAN_READY)
        check(custom.currentState == ModeState.PLAN_READY) { "Custom initial state failed" }
    }

    fun testEveryAllowedTransition() {
        val controller = ModeController()

        val expectedAllowed = listOf(
            ModeState.IDLE to ModeState.CLARIFYING, ModeState.IDLE to ModeState.PLAN_READY,
            ModeState.CLARIFYING to ModeState.CLARIFYING, ModeState.CLARIFYING to ModeState.PLAN_READY,
            ModeState.CLARIFYING to ModeState.REJECTED, ModeState.CLARIFYING to ModeState.IDLE,
            ModeState.PLAN_READY to ModeState.APPROVED, ModeState.PLAN_READY to ModeState.REVISING,
            ModeState.PLAN_READY to ModeState.PLAN_READY, ModeState.PLAN_READY to ModeState.REJECTED,
            ModeState.REVISING to ModeState.PLAN_READY, ModeState.REVISING to ModeState.CLARIFYING,
            ModeState.REVISING to ModeState.REVISING, ModeState.REVISING to ModeState.REJECTED,
            ModeState.APPROVED to ModeState.BUILDING, ModeState.APPROVED to ModeState.REVISING,
            ModeState.APPROVED to ModeState.PLAN_READY, ModeState.APPROVED to ModeState.REJECTED,
            ModeState.BUILDING to ModeState.VERIFYING, ModeState.BUILDING to ModeState.BUILDING,
            ModeState.BUILDING to ModeState.REVISING, ModeState.BUILDING to ModeState.REJECTED,
            ModeState.BUILDING to ModeState.DONE,
            ModeState.VERIFYING to ModeState.DONE, ModeState.VERIFYING to ModeState.BUILDING,
            ModeState.VERIFYING to ModeState.VERIFYING, ModeState.VERIFYING to ModeState.REVISING,
            ModeState.VERIFYING to ModeState.REJECTED,
            ModeState.DONE to ModeState.IDLE, ModeState.DONE to ModeState.REVISING, ModeState.DONE to ModeState.BUILDING,
            ModeState.REJECTED to ModeState.IDLE, ModeState.REJECTED to ModeState.CLARIFYING, ModeState.REJECTED to ModeState.REVISING,
            ModeState.REJECTED to ModeState.PLAN_READY
        )

        for ((from, to) in expectedAllowed) {
            check(controller.canTransition(from, to)) {
                "Expected transition from $from to $to to be allowed"
            }

            controller.reset(from)
            check(controller.canTransitionTo(to)) {
                "canTransitionTo($to) should be true when at $from"
            }

            val next = controller.transition(to)
            check(next == to) { "Transition result should be $to, got $next" }
            check(controller.currentState == to) { "Current state should be $to, got ${controller.currentState}" }

            controller.reset(from)
            val nextDirect = controller.transition(from, to)
            check(nextDirect == to) { "Direct transition result should be $to, got $nextDirect" }
            check(controller.currentState == to) { "Current state should be $to after direct transition" }
        }
    }

    fun testAllIllegalTransitionsRejected() {
        val controller = ModeController()
        val allStates = ModeState.values()

        var illegalCount = 0
        for (from in allStates) {
            val allowedForFrom = controller.allowedTransitionsFrom(from)
            for (to in allStates) {
                if (!allowedForFrom.contains(to)) {
                    illegalCount++
                    check(!controller.canTransition(from, to)) {
                        "Transition from $from to $to must NOT be allowed"
                    }

                    controller.reset(from)
                    var threw = false
                    try {
                        controller.transition(to)
                    } catch (e: IllegalStateException) {
                        threw = true
                    }
                    check(threw) { "transition($to) from $from should have thrown IllegalStateException" }
                    check(controller.currentState == from) {
                        "State should not change after illegal transition attempt"
                    }

                    var directThrew = false
                    try {
                        controller.transition(from, to)
                    } catch (e: IllegalStateException) {
                        directThrew = true
                    }
                    check(directThrew) {
                        "transition($from, $to) should have thrown IllegalStateException"
                    }
                }
            }
        }

        check(illegalCount > 0) { "Should have verified multiple illegal transitions" }
    }

    fun testHappyPathWorkflow() {
        val controller = ModeController()
        check(controller.currentState == ModeState.IDLE)

        controller.transition(ModeState.CLARIFYING)
        check(controller.currentState == ModeState.CLARIFYING)

        controller.transition(ModeState.PLAN_READY)
        check(controller.currentState == ModeState.PLAN_READY)

        controller.transition(ModeState.APPROVED)
        check(controller.currentState == ModeState.APPROVED)

        controller.transition(ModeState.BUILDING)
        check(controller.currentState == ModeState.BUILDING)

        controller.transition(ModeState.VERIFYING)
        check(controller.currentState == ModeState.VERIFYING)

        controller.transition(ModeState.DONE)
        check(controller.currentState == ModeState.DONE)

        controller.transition(ModeState.IDLE)
        check(controller.currentState == ModeState.IDLE)
    }

    fun testRevisionAndRejectWorkflow() {
        val controller = ModeController()
        controller.transition(ModeState.PLAN_READY)

        controller.transition(ModeState.REVISING)
        check(controller.currentState == ModeState.REVISING)

        controller.transition(ModeState.CLARIFYING)
        check(controller.currentState == ModeState.CLARIFYING)

        controller.transition(ModeState.REJECTED)
        check(controller.currentState == ModeState.REJECTED)

        controller.transition(ModeState.IDLE)
        check(controller.currentState == ModeState.IDLE)
    }

    fun testSystemPromptForPlanMode() {
        val fakeLoader = FakePromptLoader(
            planPromptText = "PLAN TEMPLATE:\nPlan={{PLAN}}\nMem={{MEMORY}}\nSkills={{SKILLS}}",
            buildPromptText = "BUILD TEMPLATE:\nPlan={{PLAN}}\nMem={{MEMORY}}\nSkills={{SKILLS}}"
        )
        val controller = ModeController(promptLoader = fakeLoader)
        val tempDir = java.nio.file.Files.createTempDirectory("test_root").toFile()

        val chat = Chat(
            id = "c1",
            title = "App",
            mode = ChatMode.PLAN,
            plan = "# My Plan",
            memory = "Prefers MVVM"
        )
        val skills = listOf(
            Skill(name = "Room", description = "DB guide", content = "Use DAOs")
        )

        val prompt = controller.systemPromptFor(ModeState.CLARIFYING, chat, tempDir, skills, chat.memory)
        check(prompt.startsWith("PLAN TEMPLATE:")) { "Expected PLAN template, got:\n$prompt" }
        check(prompt.contains("Plan=# My Plan")) { "Expected plan substituted, got:\n$prompt" }
        check(prompt.contains("Mem=Prefers MVVM")) { "Expected memory substituted, got:\n$prompt" }
        check(prompt.contains("### Room")) { "Expected skill name substituted, got:\n$prompt" }
        check(prompt.contains("DB guide")) { "Expected skill description substituted, got:\n$prompt" }
    }

    fun testSystemPromptForBuildMode() {
        val fakeLoader = FakePromptLoader(
            planPromptText = "PLAN TEMPLATE:\nPlan={{PLAN}}\nMem={{MEMORY}}\nSkills={{SKILLS}}",
            buildPromptText = "BUILD TEMPLATE:\nPlan={{PLAN}}\nMem={{MEMORY}}\nSkills={{SKILLS}}"
        )
        val controller = ModeController(promptLoader = fakeLoader)
        val tempDir = java.nio.file.Files.createTempDirectory("test_root").toFile()

        val chat = Chat(
            id = "c2",
            title = "Build App",
            mode = ChatMode.BUILD,
            plan = "# Approved App Plan",
            memory = "SDK 26 min"
        )
        val skills = listOf(
            Skill(name = "Compose", content = "Use LazyColumn")
        )

        val prompt = controller.systemPromptFor(ModeState.BUILDING, chat, tempDir, skills, chat.memory)
        check(prompt.startsWith("BUILD TEMPLATE:")) { "Expected BUILD template, got:\n$prompt" }
        check(prompt.contains("Plan=# Approved App Plan")) { "Expected plan substituted" }
        check(prompt.contains("Mem=SDK 26 min")) { "Expected memory substituted" }
        check(prompt.contains("### Compose")) { "Expected skill substituted" }
    }

    fun testSystemPromptEmptyPlaceholders() {
        val fakeLoader = FakePromptLoader(
            planPromptText = "PLAN:\n{{PLAN}}\n{{MEMORY}}\n{{SKILLS}}",
            buildPromptText = "BUILD:\n{{PLAN}}\n{{MEMORY}}\n{{SKILLS}}"
        )
        val controller = ModeController(promptLoader = fakeLoader)
        val tempDir = java.nio.file.Files.createTempDirectory("test_root").toFile()

        val chat = Chat(id = "c3", title = "Empty Chat")
        val prompt = controller.systemPromptFor(ModeState.IDLE, chat, tempDir, emptyList<Skill>(), "")

        check(prompt.contains("(No plan approved yet)")) { "Missing plan placeholder fallback" }
        check(prompt.contains("(None)")) { "Missing memory placeholder fallback" }

        val defaultPrompt = controller.systemPromptFor(ModeState.IDLE, chat, tempDir)
        check(defaultPrompt.contains("(No plan approved yet)")) { "Default arguments prompt failed" }
    }

    fun testSystemPromptOverloads() {
        val fakeLoader = FakePromptLoader(
            planPromptText = "PLAN:\n{{SKILLS}}",
            buildPromptText = "BUILD:\n{{SKILLS}}"
        )
        val controller = ModeController(promptLoader = fakeLoader)
        val tempDir = java.nio.file.Files.createTempDirectory("test_root").toFile()
        val chat = Chat(id = "c4", title = "Overload Chat")

        val stringPrompt = controller.systemPromptFor(ModeState.BUILDING, chat, tempDir, listOf("Ktor", "Room"), "mem")
        check(stringPrompt.contains("### Ktor")) { "String list overload failed" }
        check(stringPrompt.contains("### Room")) { "String list overload failed" }

        val rawPrompt = controller.systemPromptFor(ModeState.BUILDING, chat, tempDir, "Some raw skills", "mem")
        check(rawPrompt.contains("Some raw skills")) { "Raw string overload failed" }
    }

    fun testSkillFromMarkdown() {
        val markdownWithFrontmatter = """
            ---
            name: Compose Guidelines
            description: Best practices for Jetpack Compose
            ---
            # Compose Guidelines
            Always use remember and StateFlow.
        """.trimIndent()

        val skill = Skill.fromMarkdown(markdownWithFrontmatter)
        check(skill.name == "Compose Guidelines") { "Expected name Compose Guidelines, got ${skill.name}" }
        check(skill.description == "Best practices for Jetpack Compose") { "Expected description match" }
        check(skill.content.contains("Always use remember and StateFlow.")) { "Expected content match" }

        val plainMarkdown = "# Just Markdown\nContent without frontmatter"
        val plainSkill = Skill.fromMarkdown(plainMarkdown, fallbackName = "Fallback")
        check(plainSkill.name == "Fallback") { "Expected fallback name" }
        check(plainSkill.description == null) { "Expected null description" }
        check(plainSkill.content.contains("Just Markdown")) { "Expected content preserved" }
    }

    fun testPromptLoaderWithCustomClassLoader() {
        val testContent = "Test prompt content from resource"
        val customClassLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String): InputStream? {
                return if (name.endsWith("test_prompt.md")) {
                    ByteArrayInputStream(testContent.toByteArray(Charsets.UTF_8))
                } else {
                    null
                }
            }
        }

        val loader = PromptLoader(customClassLoader)
        val loaded = loader.loadPrompt("test_prompt.md")
        check(loaded == testContent) { "Expected loaded content match, got $loaded" }

        var missingThrew = false
        try {
            loader.loadPrompt("missing_prompt.md")
        } catch (e: IllegalStateException) {
            missingThrew = true
        }
        check(missingThrew) { "Expected IllegalStateException for missing prompt" }
    }

    fun runAllTests() {
        testInitialState()
        testEveryAllowedTransition()
        testAllIllegalTransitionsRejected()
        testHappyPathWorkflow()
        testRevisionAndRejectWorkflow()
        testSystemPromptForPlanMode()
        testSystemPromptForBuildMode()
        testSystemPromptEmptyPlaceholders()
        testSystemPromptOverloads()
        testSkillFromMarkdown()
        testPromptLoaderWithCustomClassLoader()
        println("All ModeController tests passed successfully!")
    }
}
