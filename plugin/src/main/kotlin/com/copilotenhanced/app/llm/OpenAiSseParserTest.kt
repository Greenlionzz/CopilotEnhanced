package com.copilotenhanced.app.llm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class OpenAiSseParserTest {

    fun testParseSimpleDelta() {
        val parser = OpenAiSseParser()
        val line = """data: {"id":"chatcmpl-1","choices":[{"index":0,"delta":{"content":"Hello world"}}]}"""
        val events = parser.parseLine(line)

        check(events.size == 1) { "Expected 1 event, got ${events.size}" }
        val first = events.first()
        check(first is LlmEvent.Delta && first.text == "Hello world") { "Expected Delta('Hello world'), got $first" }
        check(!parser.isDone) { "Parser should not be done yet" }
    }

    fun testParseMultipleDeltasAndDone() {
        val parser = OpenAiSseParser()
        val lines = listOf(
            ": keep-alive",
            "",
            """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
            """data: {"choices":[{"delta":{"content":"Hello"}}]}""",
            """data: {"choices":[{"delta":{"content":" there!"}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            """data: [DONE]""",
        )

        val allEvents = lines.flatMap { parser.parseLine(it) }

        check(allEvents.size == 3) { "Expected 3 events (2 deltas + Done), got ${allEvents.size}" }
        check(allEvents[0] == LlmEvent.Delta("Hello")) { "Expected Delta('Hello'), got ${allEvents[0]}" }
        check(allEvents[1] == LlmEvent.Delta(" there!")) { "Expected Delta(' there!'), got ${allEvents[1]}" }
        check(allEvents[2] == LlmEvent.Done) { "Expected Done, got ${allEvents[2]}" }
        check(parser.isDone) { "Parser should be marked done" }
    }

    fun testParseStreamError() {
        val parser = OpenAiSseParser()
        val errorLine = """data: {"error":{"message":"Rate limit exceeded for model","type":"rate_limit_error"}}"""
        val events = parser.parseLine(errorLine)

        check(events.size == 1) { "Expected 1 error event, got ${events.size}" }
        val first = events.first()
        check(first is LlmEvent.Error && first.message == "Rate limit exceeded for model") {
            "Expected Error('Rate limit exceeded for model'), got $first"
        }
    }

    fun testParseMalformedDataIgnored() {
        val parser = OpenAiSseParser()
        val events1 = parser.parseLine("data: not-a-json")
        check(events1.isEmpty()) { "Expected empty list on malformed json, got $events1" }

        val events2 = parser.parseLine("data: {}")
        check(events2.isEmpty()) { "Expected empty list on empty json object, got $events2" }
    }

    fun testFinishEmitsDoneIfNotAlreadyDone() {
        val parser = OpenAiSseParser()
        parser.parseLine("""data: {"choices":[{"delta":{"content":"Hi"}}]}""")
        check(!parser.isDone) { "Parser should not be done" }

        val finishEvents = parser.finish()
        check(finishEvents == listOf(LlmEvent.Done)) { "Expected Done event from finish()" }
        check(parser.isDone) { "Parser should now be done" }

        val finishEventsAgain = parser.finish()
        check(finishEventsAgain.isEmpty()) { "Repeated finish() should return empty list" }
    }

    fun testUrlNormalization() {
        val url1 = OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://api.openai.com/v1")
        check(url1 == "https://api.openai.com/v1/chat/completions") { "Failed for standard v1 base: $url1" }

        val url2 = OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://api.openai.com/v1/")
        check(url2 == "https://api.openai.com/v1/chat/completions") { "Failed for trailing slash: $url2" }

        val url3 = OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://api.openai.com/v1/chat/completions")
        check(url3 == "https://api.openai.com/v1/chat/completions") { "Failed for explicit completions URL: $url3" }

        val url4 = OpenAiCompatibleClient.normalizeChatCompletionsUrl("http://localhost:11434/v1")
        check(url4 == "http://localhost:11434/v1/chat/completions") { "Failed for local ollama URL: $url4" }
    }

    fun testHttpErrorFormatting() {
        val err401 = OpenAiCompatibleClient.formatHttpError(401, """{"error":{"message":"Incorrect API key provided"}}""", "https://api.openai.com/v1/chat/completions")
        check(err401.contains("401") && err401.contains("Incorrect API key provided")) { "Failed 401 format: $err401" }

        val err404 = OpenAiCompatibleClient.formatHttpError(404, """{"error":{"message":"The model does not exist"}}""", "https://api.openai.com/v1/chat/completions")
        check(err404.contains("404") && err404.contains("The model does not exist")) { "Failed 404 format: $err404" }

        val err429 = OpenAiCompatibleClient.formatHttpError(429, """{"error":{"message":"You exceeded your current quota"}}""", "https://api.openai.com/v1/chat/completions")
        check(err429.contains("429") && err429.contains("You exceeded your current quota")) { "Failed 429 format: $err429" }
    }

    fun testFakeLlmClientStream() = runBlocking {
        val fake = FakeLlmClient(responseDeltas = listOf("A", "B", "C"))
        val messages = listOf(ChatMessage.user("Test prompt"))
        val events = fake.stream(messages, system = "Test system").toList()

        check(events.size == 4) { "Expected 4 events from FakeLlmClient, got ${events.size}" }
        check(events[0] == LlmEvent.Delta("A"))
        check(events[1] == LlmEvent.Delta("B"))
        check(events[2] == LlmEvent.Delta("C"))
        check(events[3] == LlmEvent.Done)

        check(fake.recordedRequests.size == 1) { "Expected 1 recorded request" }
        check(fake.recordedRequests[0].messages == messages)
        check(fake.recordedRequests[0].system == "Test system")
    }

    fun testParseToolCallsFragmentedArguments() {
        val parser = OpenAiSseParser()
        val line1 = """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_abc","function":{"name":"write_file","arguments":"{\"path\":\"app/"}}]}}]}"""
        val line2 = """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"src/Foo.kt\",\"content\":\"line1\\nline2\"}"}}]}}]}"""

        val events1 = parser.parseLine(line1)
        check(events1.isEmpty()) { "Expected no events until stream ends" }

        val events2 = parser.parseLine(line2)
        check(events2.isEmpty()) { "Expected no events until stream ends" }

        val finishEvents = parser.finish()
        check(finishEvents.size == 2) { "Expected ToolCall and Done, got ${finishEvents.size}" }
        val toolCall = finishEvents[0] as LlmEvent.ToolCall
        check(toolCall.id == "call_abc") { "Expected id 'call_abc', got ${toolCall.id}" }
        check(toolCall.name == "write_file") { "Expected name 'write_file', got ${toolCall.name}" }
        check(toolCall.argumentsJson == "{\"path\":\"app/src/Foo.kt\",\"content\":\"line1\\nline2\"}") {
            "Unexpected accumulated arguments: ${toolCall.argumentsJson}"
        }
        check(finishEvents[1] == LlmEvent.Done) { "Expected Done event" }
    }

    fun testPathSafetyRules() {
        val tempDir = java.nio.file.Files.createTempDirectory("path_safety_test").toFile()
        try {
            val v1 = com.copilotenhanced.app.tools.ProjectTools.validatePath(tempDir, "app/src/Foo.kt")
            check(v1.name == "Foo.kt")

            val v2 = com.copilotenhanced.app.tools.ProjectTools.validatePath(tempDir, "")
            check(v2 == tempDir)

            var threwAbs = false
            try {
                com.copilotenhanced.app.tools.ProjectTools.validatePath(tempDir, "/etc/passwd")
            } catch (_: IllegalArgumentException) {
                threwAbs = true
            }
            check(threwAbs) { "Expected IllegalArgumentException for absolute path" }

            var threwDotDot = false
            try {
                com.copilotenhanced.app.tools.ProjectTools.validatePath(tempDir, "../secret.txt")
            } catch (_: IllegalArgumentException) {
                threwDotDot = true
            }
            check(threwDotDot) { "Expected IllegalArgumentException for '..' path" }

            var threwDotDotMid = false
            try {
                com.copilotenhanced.app.tools.ProjectTools.validatePath(tempDir, "app/../../secret.txt")
            } catch (_: IllegalArgumentException) {
                threwDotDotMid = true
            }
            check(threwDotDotMid) { "Expected IllegalArgumentException for nested '..' path" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testFakeLlmClientToolCall() = runBlocking {
        val toolCallEvent = LlmEvent.ToolCall("call_1", "read_file", "{\"path\":\"README.md\"}")
        val fake = FakeLlmClient(eventsToEmit = listOf(toolCallEvent, LlmEvent.Done))
        val messages = listOf(ChatMessage.user("Read README"))
        val events = fake.stream(messages).toList()

        check(events.size == 2) { "Expected 2 events, got ${events.size}" }
        check(events[0] == toolCallEvent)
        check(events[1] == LlmEvent.Done)
    }

    fun runAllTests() {
        testParseSimpleDelta()
        testParseMultipleDeltasAndDone()
        testParseStreamError()
        testParseMalformedDataIgnored()
        testFinishEmitsDoneIfNotAlreadyDone()
        testUrlNormalization()
        testHttpErrorFormatting()
        testFakeLlmClientStream()
        testParseToolCallsFragmentedArguments()
        testPathSafetyRules()
        testFakeLlmClientToolCall()
        println("All OpenAiSseParser and LlmClient tests passed successfully!")
    }
}
