package com.copilotenhanced.app.tools

import com.copilotenhanced.app.llm.ToolDef
import java.io.File

class ProjectToolExecutor(
    private val rootDir: File
) : ToolExecutor {

    override fun definitions(): List<ToolDef> {
        val jsonList = ProjectTools.getToolDefinitions()
        return jsonList.map { obj ->
            val func = obj.getJSONObject("function")
            ToolDef(
                name = func.getString("name"),
                description = func.getString("description"),
                parametersJsonSchema = func.getJSONObject("parameters")
            )
        }
    }

    override suspend fun execute(name: String, argsJson: String): String {
        return ProjectTools.executeTool(rootDir, name, argsJson)
    }
}
