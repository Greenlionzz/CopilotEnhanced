package com.copilotenhanced.app.llm

import org.json.JSONObject

data class ToolDef(
    val name: String,
    val description: String,
    val parametersJsonSchema: JSONObject
)
