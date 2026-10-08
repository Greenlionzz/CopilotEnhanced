package com.copilotenhanced.app.chat

import java.io.InputStream

open class PromptLoader(
    private val classLoader: ClassLoader? = PromptLoader::class.java.classLoader
) {

    open fun loadPrompt(path: String): String {
        val resourcePath = if (path == "PROJECT_CONVENTIONS.md") {
            "PROJECT_CONVENTIONS.md"
        } else if (path.startsWith("prompts/")) {
            path
        } else {
            "prompts/$path"
        }
        val loader = classLoader
            ?: PromptLoader::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader()
        var stream: InputStream? = loader.getResourceAsStream(resourcePath)
            ?: PromptLoader::class.java.getResourceAsStream(if (resourcePath.startsWith("/")) resourcePath else "/$resourcePath")
            ?: PromptLoader::class.java.getResourceAsStream(resourcePath)

        if (stream == null && path == "PROJECT_CONVENTIONS.md") {
            stream = loader.getResourceAsStream("prompts/PROJECT_CONVENTIONS.md")
                ?: PromptLoader::class.java.getResourceAsStream("/prompts/PROJECT_CONVENTIONS.md")
                ?: PromptLoader::class.java.getResourceAsStream("prompts/PROJECT_CONVENTIONS.md")
        }

        if (stream == null) {
            if (path == "PROJECT_CONVENTIONS.md") {
                return "PROJECT_CONVENTIONS.md not found in resources"
            }
            return if (path.contains("plan")) {
                "You are in PLAN MODE. Help the user plan their app."
            } else {
                "You are in BUILD MODE. Help the user build their app."
            }
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    open fun loadPlanPrompt(): String = loadPrompt("prompts/plan_mode.md")

    open fun loadBuildPrompt(): String = loadPrompt("prompts/build_mode.md")

    companion object {
        private val defaultLoader by lazy { PromptLoader() }

        fun loadPlanPrompt(): String = defaultLoader.loadPlanPrompt()

        fun loadBuildPrompt(): String = defaultLoader.loadBuildPrompt()

        fun loadPrompt(path: String): String = defaultLoader.loadPrompt(path)
    }
}
