package com.copilotenhanced.app.protocol

class ProtocolParserTest {

    fun runAllTests() {
        testPlainText()
        testLineFormatQuestions()
        testLineFormatMultiAndOptions()
        testMultipleQuestionsInBlock()
        testForgivingBlanksAndUnknownLines()
        testStrayClosingTagBeforeQuestions()
        testMissingClosingTagAtStreamEnd()
        testUnparseableQuestionsBlock()
        testOldJsonFallback()
        testCompletePlanBlock()
        testCompleteProgressBlock()
        testSplitOpeningTag()
        testSplitClosingTag()
        testMultipleMixedBlocks()
        testLargePlainTextPerformance()
        println("All ProtocolParser tests passed successfully!")
    }

    private fun testPlainText() {
        val parser = ProtocolParser()
        val parts = parser.feed("Hello world! This is plain text.")
        check(parts.size == 1) { "Expected 1 part" }
        val textPart = parts[0] as ParsedPart.Text
        check(textPart.content == "Hello world! This is plain text.") { "Incorrect text content" }
    }

    private fun testLineFormatQuestions() {
        val parser = ProtocolParser()
        val input = """
            <questions>
            ## Question text?
            single | custom
            - 📝 Label | description
            </questions>
        """.trimIndent()
        val parts = parser.feed(input)
        check(parts.size == 1) { "Expected 1 part, got ${parts.size}" }
        val qPart = parts[0] as ParsedPart.Questions
        val payload = qPart.data ?: error("payload is null")
        check(payload.questions.size == 1) { "Expected 1 question, got ${payload.questions.size}" }

        val q = payload.questions[0]
        check(q.question == "Question text?") { "Expected 'Question text?', got '${q.question}'" }
        check(q.type == "single") { "Expected 'single', got '${q.type}'" }
        check(q.allowCustom) { "Expected allowCustom to be true" }
        check(q.options.size == 1) { "Expected 1 option, got ${q.options.size}" }

        val opt = q.options[0]
        check(opt.emoji == "📝") { "Expected emoji '📝', got '${opt.emoji}'" }
        check(opt.label == "Label") { "Expected label 'Label', got '${opt.label}'" }
        check(opt.description == "description") { "Expected description 'description', got '${opt.description}'" }
    }

    private fun testLineFormatMultiAndOptions() {
        val parser = ProtocolParser()
        val input = """
            <questions>
            ## Favorite fruits?
            multi
            - 🍎 Apple
            - 🍌 Banana | Sweet yellow fruit
            - Orange
            </questions>
        """.trimIndent()
        val parts = parser.feed(input)
        check(parts.size == 1) { "Expected 1 part, got ${parts.size}" }
        val qPart = parts[0] as ParsedPart.Questions
        val payload = qPart.data ?: error("payload is null")
        val q = payload.questions[0]

        check(q.question == "Favorite fruits?") { "Incorrect question text" }
        check(q.type == "multi") { "Expected multi" }
        check(!q.allowCustom) { "Expected allowCustom to be false" }
        check(q.options.size == 3) { "Expected 3 options, got ${q.options.size}" }

        check(q.options[0].emoji == "🍎" && q.options[0].label == "Apple" && q.options[0].description == null)
        check(q.options[1].emoji == "🍌" && q.options[1].label == "Banana" && q.options[1].description == "Sweet yellow fruit")
        check(q.options[2].emoji == null && q.options[2].label == "Orange" && q.options[2].description == null)
    }

    private fun testMultipleQuestionsInBlock() {
        val parser = ProtocolParser()
        val input = """
            <questions>
            ## First question?
            single
            - A
            - B

            ## Second question?
            multi | custom
            - 🚀 C | Launch
            - D
            </questions>
        """.trimIndent()
        val parts = parser.feed(input)
        check(parts.size == 1) { "Expected 1 part, got ${parts.size}" }
        val qPart = parts[0] as ParsedPart.Questions
        val payload = qPart.data ?: error("payload is null")
        check(payload.questions.size == 2) { "Expected 2 questions, got ${payload.questions.size}" }

        check(payload.questions[0].question == "First question?")
        check(payload.questions[0].type == "single")
        check(!payload.questions[0].allowCustom)
        check(payload.questions[0].options.size == 2)

        check(payload.questions[1].question == "Second question?")
        check(payload.questions[1].type == "multi")
        check(payload.questions[1].allowCustom)
        check(payload.questions[1].options.size == 2)
        check(payload.questions[1].options[0].emoji == "🚀")
    }

    private fun testForgivingBlanksAndUnknownLines() {
        val parser = ProtocolParser()
        val input = """
            <questions>
            Some arbitrary intro line that should be ignored
            
            ## Target audience?
            single | custom
            Unknown line before options
            - Beginners | New users
            Another random comment
            - Experts
            </questions>
        """.trimIndent()
        val parts = parser.feed(input)
        check(parts.size == 1) { "Expected 1 part, got ${parts.size}" }
        val qPart = parts[0] as ParsedPart.Questions
        val payload = qPart.data ?: error("payload is null")
        check(payload.questions.size == 1)
        val q = payload.questions[0]
        check(q.question == "Target audience?")
        check(q.type == "single")
        check(q.allowCustom)
        check(q.options.size == 2)
    }

    private fun testStrayClosingTagBeforeQuestions() {
        // Broken sample: stray "</questions" before "<questions>"
        val parser = ProtocolParser()
        val brokenInput = "</questions\n<questions>\n## Which architecture?\nsingle\n- MVVM\n- MVI\n</questions>"
        val parts = parser.feed(brokenInput)
        check(parts.size == 1) { "Expected stray tag to be ignored, got ${parts.size} parts" }
        val qPart = parts[0] as ParsedPart.Questions
        val questions = qPart.data?.questions ?: error("questions is null")
        check(questions.size == 1) { "Expected 1 question" }
        check(questions[0].question == "Which architecture?")

        // Stray "</questions>" with text
        val parser2 = ProtocolParser()
        val brokenInput2 = "Please review: </questions> <questions>\n## Choice?\nsingle\n- A\n</questions>"
        val parts2 = parser2.feed(brokenInput2)
        check(parts2.size == 2) { "Expected 2 parts (Text and Questions), got ${parts2.size}" }
        val textPart = parts2[0] as ParsedPart.Text
        check(textPart.content.trim() == "Please review:")
        check(parts2[1] is ParsedPart.Questions)
    }

    private fun testMissingClosingTagAtStreamEnd() {
        val parser = ProtocolParser()
        // Stream without closing tag </questions>
        val streamChunk = "<questions>\n## Stream end question?\nsingle | custom\n- 📝 Option 1 | desc"
        val fedParts = parser.feed(streamChunk)
        check(fedParts.isEmpty()) { "Should buffer unclosed tag until flush" }

        val flushedParts = parser.flush()
        check(flushedParts.size == 1) { "Expected 1 part on flush, got ${flushedParts.size}" }
        val qPart = flushedParts[0] as ParsedPart.Questions
        val q = qPart.data?.questions?.get(0) ?: error("No question in payload")
        check(q.question == "Stream end question?")
        check(q.type == "single")
        check(q.allowCustom)
        check(q.options.size == 1)
        check(q.options[0].emoji == "📝")
    }

    private fun testUnparseableQuestionsBlock() {
        val parser = ProtocolParser()
        val input = "<questions>not valid line format or json</questions>"
        val parts = parser.feed(input)
        check(parts.size == 1) { "Expected 1 part" }
        val invalidPart = parts[0] as ParsedPart.InvalidQuestions
        check(invalidPart.raw.contains("not valid line format or json"))
    }

    private fun testOldJsonFallback() {
        val parser = ProtocolParser()
        val input = "Please answer:\n<questions>{\"questions\":[{\"id\":\"q1\",\"question\":\"Which?\",\"type\":\"single\",\"options\":[\"A\",\"B\"]}]}</questions>\nThanks!"
        val parts = parser.feed(input)
        check(parts.size == 3) { "Expected 3 parts, got ${parts.size}" }
        check(parts[0] is ParsedPart.Text)
        val qPart = parts[1] as ParsedPart.Questions
        val data = qPart.data ?: error("data is null")
        check(data.questions.size == 1) { "Expected 1 question" }
        check(data.questions[0].id == "q1") { "Expected question id q1" }
        check(data.questions[0].options.size == 2) { "Expected 2 options" }
        check(data.questions[0].options[0].label == "A") { "Expected option A" }
        check(data.questions[0].options[1].label == "B") { "Expected option B" }
        check(parts[2] is ParsedPart.Text)
    }

    private fun testCompletePlanBlock() {
        val parser = ProtocolParser()
        val input = "<plan title=\"My App\" version=\"2\">\n# Plan\n> pitch\n</plan>"
        val parts = parser.feed(input)
        check(parts.size == 1) { "Expected 1 part" }
        val planPart = parts[0] as ParsedPart.Plan
        check(planPart.title == "My App") { "Expected title 'My App', got ${planPart.title}" }
        check(planPart.version == 2) { "Expected version 2, got ${planPart.version}" }
        check(planPart.content.contains("# Plan")) { "Expected plan content" }
    }

    private fun testCompleteProgressBlock() {
        val parser = ProtocolParser()
        val input = "<progress milestone=\"M1\" status=\"done\">Skeleton built</progress>"
        val parts = parser.feed(input)
        check(parts.size == 1) { "Expected 1 part" }
        val progPart = parts[0] as ParsedPart.Progress
        check(progPart.milestone == "M1") { "Expected milestone M1" }
        check(progPart.status == "done") { "Expected status done" }
        check(progPart.note == "Skeleton built") { "Expected note 'Skeleton built'" }
    }

    private fun testSplitOpeningTag() {
        val parser = ProtocolParser()
        val p1 = parser.feed("Starting <ques")
        check(p1.size == 1)
        check((p1[0] as ParsedPart.Text).content == "Starting ")

        val p2 = parser.feed("tions>{\"questions\":[{\"id\":\"q1\",\"question\":\"Test?\",\"options\":[\"Yes\"]}]}</questions>")
        check(p2.size == 1)
        check(p2[0] is ParsedPart.Questions)
    }

    private fun testSplitClosingTag() {
        val parser2 = ProtocolParser()
        parser2.feed("<plan title=\"App\" version=\"1\"># Content")
        val pChunk = parser2.feed(" here</plan>")
        check(pChunk.size == 1)
        check(pChunk[0] is ParsedPart.Plan)
        val plan = pChunk[0] as ParsedPart.Plan
        check(plan.title == "App")
        check(plan.content.contains("# Content here"))
    }

    private fun testMultipleMixedBlocks() {
        val parser = ProtocolParser()
        val input = "Intro\n<progress milestone=\"M1\" status=\"started\">Starting M1</progress>\nMiddle\n<plan title=\"Test\" version=\"1\">Plan body</plan>\nOutro"
        val parts = parser.feed(input)
        check(parts.size == 5) { "Expected 5 parts, got ${parts.size}" }
        check(parts[0] is ParsedPart.Text)
        check(parts[1] is ParsedPart.Progress)
        check(parts[2] is ParsedPart.Text)
        check(parts[3] is ParsedPart.Plan)
        check(parts[4] is ParsedPart.Text)
    }

    private fun testLargePlainTextPerformance() {
        val parser = ProtocolParser()
        val largeText = "A".repeat(50_000)
        val chunkSize = 20
        var i = 0
        val startTime = System.currentTimeMillis()
        while (i < largeText.length) {
            val end = minOf(i + chunkSize, largeText.length)
            parser.feed(largeText.substring(i, end))
            i = end
        }
        val parts = parser.flush()
        val duration = System.currentTimeMillis() - startTime
        check(parts.isNotEmpty()) { "Expected parts" }
        check(duration < 2000) { "Performance test took too long: ${duration}ms" }
    }
}
