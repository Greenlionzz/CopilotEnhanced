package com.copilotenhanced.app.tools

import com.copilotenhanced.app.llm.ToolDef
import org.json.JSONArray
import org.json.JSONObject

class FakeToolExecutor : ToolExecutor {
    private val files = mutableMapOf<String, String>()
    private val directories = mutableSetOf<String>()

    init {
        directories.add("")
    }

    override fun definitions(): List<ToolDef> {
        return listOf(
            ToolDef(
                name = "list_directory",
                description = "List directory entries at path relative to project root",
                parametersJsonSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Relative directory path (e.g. '' or 'app/src')")
                        })
                    })
                    put("required", JSONArray().apply { put("path") })
                }
            ),
            ToolDef(
                name = "read_file",
                description = "Read file contents",
                parametersJsonSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Relative file path")
                        })
                    })
                    put("required", JSONArray().apply { put("path") })
                }
            ),
            ToolDef(
                name = "write_file",
                description = "Write complete file content atomically",
                parametersJsonSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Relative file path")
                        })
                        put("content", JSONObject().apply {
                            put("type", "string")
                            put("description", "Full file content")
                        })
                    })
                    put("required", JSONArray().apply { put("path"); put("content") })
                }
            ),
            ToolDef(
                name = "create_directory",
                description = "Create directory and parent directories",
                parametersJsonSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Relative directory path")
                        })
                    })
                    put("required", JSONArray().apply { put("path") })
                }
            ),
            ToolDef(
                name = "delete_file",
                description = "Delete file or directory",
                parametersJsonSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().apply {
                            put("type", "string")
                            put("description", "Relative path to file or directory")
                        })
                    })
                    put("required", JSONArray().apply { put("path") })
                }
            )
        )
    }

    override suspend fun execute(name: String, argsJson: String): String {
        return try {
            val args = JSONObject(argsJson)
            when (name) {
                "list_directory" -> {
                    val path = normalizePath(args.optString("path", ""))
                    val entries = mutableSetOf<String>()
                    for (f in files.keys) {
                        if (matchesDir(path, f)) {
                            val rel = relativeToDir(path, f)
                            val firstSegment = rel.substringBefore('/')
                            if (firstSegment.isNotEmpty()) entries.add(firstSegment)
                        }
                    }
                    for (d in directories) {
                        if (matchesDir(path, d) && d != path) {
                            val rel = relativeToDir(path, d)
                            val firstSegment = rel.substringBefore('/')
                            if (firstSegment.isNotEmpty()) entries.add("$firstSegment/")
                        }
                    }
                    val sorted = entries.sorted()
                    val sb = StringBuilder("Listed ${sorted.size} entries in ${path.ifBlank { "." }}:\n")
                    for (e in sorted) {
                        sb.append("- $e\n")
                    }
                    sb.toString().trimEnd()
                }
                "read_file" -> {
                    val path = normalizePath(args.optString("path", ""))
                    val content = files[path] ?: return "Error: File does not exist: $path"
                    val lines = countLines(content)
                    "Read $path, $lines lines (${content.length} chars):\n$content"
                }
                "write_file" -> {
                    val path = normalizePath(args.optString("path", ""))
                    val content = args.optString("content", "")
                    if (content.isEmpty() || content == "(content omitted)" || content.startsWith("[file written")) {
                        return "Rejected: that is placeholder text, not file content. Send the full real file content."
                    }
                    files[path] = content
                    var p = path.substringBeforeLast('/', "")
                    while (p.isNotEmpty()) {
                        directories.add(p)
                        p = p.substringBeforeLast('/', "")
                    }
                    val lines = countLines(content)
                    "Wrote $path, $lines lines"
                }
                "create_directory" -> {
                    val path = normalizePath(args.optString("path", ""))
                    if (path.isNotEmpty()) {
                        directories.add(path)
                        var p = path.substringBeforeLast('/', "")
                        while (p.isNotEmpty()) {
                            directories.add(p)
                            p = p.substringBeforeLast('/', "")
                        }
                    }
                    "Created directory $path"
                }
                "delete_file" -> {
                    val path = normalizePath(args.optString("path", ""))
                    files.remove(path)
                    directories.remove(path)
                    val prefix = "$path/"
                    files.keys.filter { it.startsWith(prefix) }.forEach { files.remove(it) }
                    directories.filter { it.startsWith(prefix) }.forEach { directories.remove(it) }
                    "Deleted $path"
                }
                else -> "Error: Unknown tool $name"
            }
        } catch (e: Exception) {
            "Error executing $name: ${e.message}"
        }
    }

    private fun normalizePath(p: String): String {
        return p.trim().removePrefix("/").removePrefix("./").trimEnd('/')
    }

    private fun matchesDir(dir: String, target: String): Boolean {
        if (dir.isEmpty()) return true
        return target == dir || target.startsWith("$dir/")
    }

    private fun relativeToDir(dir: String, target: String): String {
        if (dir.isEmpty()) return target
        return target.removePrefix("$dir/")
    }

    private fun countLines(s: String): Int {
        if (s.isEmpty()) return 0
        return s.count { it == '\n' } + 1
    }
}
