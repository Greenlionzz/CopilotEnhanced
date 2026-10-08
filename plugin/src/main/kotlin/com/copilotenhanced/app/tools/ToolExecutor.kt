package com.copilotenhanced.app.tools

import com.copilotenhanced.app.llm.ToolDef

interface ToolExecutor {
    fun definitions(): List<ToolDef>
    suspend fun execute(name: String, argsJson: String): String
}
