package com.copilotenhanced.app

import com.copilotenhanced.app.chat.ChatRepositoryTest
import com.copilotenhanced.app.chat.ModeControllerTest
import com.copilotenhanced.app.llm.OpenAiSseParserTest
import com.copilotenhanced.app.protocol.ProtocolParserTest
import com.copilotenhanced.app.ui.ChatHistoryLogicTest
import com.copilotenhanced.app.ui.ChatViewModelTest
import com.copilotenhanced.app.ui.PlanCardLogicTest
import com.copilotenhanced.app.ui.QuestionCardLogicTest

fun main() {
    println("Running OpenAiSseParserTest...")
    OpenAiSseParserTest().runAllTests()
    println("Running ChatRepositoryTest...")
    ChatRepositoryTest().runAllTests()
    println("Running ChatViewModelTest...")
    ChatViewModelTest().runAllTests()
    println("Running ChatHistoryLogicTest...")
    ChatHistoryLogicTest().runAllTests()
    println("Running ModeControllerTest...")
    ModeControllerTest().runAllTests()
    println("Running ProtocolParserTest...")
    ProtocolParserTest().runAllTests()
    println("Running QuestionCardLogicTest...")
    QuestionCardLogicTest().runAllTests()
    println("Running PlanCardLogicTest...")
    PlanCardLogicTest().runAllTests()
    println("Completed!")
}
