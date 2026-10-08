package com.copilotenhanced.app.tools

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ProjectTools {

    private val readHistory = mutableMapOf<String, Pair<Int, Int>>()

    fun resolveProjectRoot(context: android.content.Context): File {
        val prefs = context.getSharedPreferences(com.copilotenhanced.app.settings.ProviderConfigStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(com.copilotenhanced.app.settings.ProviderConfigStore.KEY_PROJECT_ROOT, "") ?: ""
        var s = raw.trim { it <= ' ' || it == '"' || it == '\'' }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length - 1).trim { it <= ' ' || it == '"' || it == '\'' }
        }
        if (s.isEmpty()) {
            throw IllegalArgumentException("Project root folder not set in settings")
        }
        val f = File(s).absoluteFile
        if (!f.exists()) {
            throw IllegalArgumentException("Project root does not exist: ${f.absolutePath}")
        }
        if (!f.isDirectory) {
            throw IllegalArgumentException("Project root is not a directory: ${f.absolutePath}")
        }
        if (!f.canRead()) {
            throw IllegalArgumentException("Project root is not readable: ${f.absolutePath}")
        }
        return f
    }

    fun findProjectRoot(startDir: File, context: android.content.Context? = null): File {
        if (context != null) {
            try {
                return resolveProjectRoot(context)
            } catch (e: Exception) {
                // fallback
            }
        }
        var curr: File? = startDir.absoluteFile
        while (curr != null) {
            val moduleToml = File(curr, "module.toml")
            val hasSubmoduleToml = curr.listFiles()?.any { File(it, "module.toml").exists() } == true
            val hasPlatform = File(curr, ".platform").exists()
            if ((moduleToml.exists() && moduleToml.isFile) || hasSubmoduleToml || hasPlatform) {
                return curr
            }
            curr = curr.parentFile
        }
        return startDir.absoluteFile
    }

    fun getToolDefinitions(): List<JSONObject> {
        return listOf(
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "list_directory")
                    put("description", "List directory entries at path relative to project root")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply {
                                put("type", "string")
                                put("description", "Relative directory path (e.g. '' or 'src')")
                            })
                        })
                        put("required", JSONArray().apply { put("path") })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "read_file")
                    put("description", "Read file contents (max 30,000 characters, optional start_line and end_line)")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply {
                                put("type", "string")
                                put("description", "Relative file path")
                            })
                            put("start_line", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Optional 1-based start line")
                            })
                            put("end_line", JSONObject().apply {
                                put("type", "integer")
                                put("description", "Optional 1-based end line")
                            })
                        })
                        put("required", JSONArray().apply { put("path") })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "search_files")
                    put("description", "Case-insensitive text search over project's text files (skip hidden folders and build/)")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("query", JSONObject().apply {
                                put("type", "string")
                                put("description", "Text to search for")
                            })
                        })
                        put("required", JSONArray().apply { put("query") })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "write_file")
                    put("description", "Write complete file content atomically, creating parent directories")
                    put("parameters", JSONObject().apply {
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
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "create_directory")
                    put("description", "Create directory and parent directories")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply {
                                put("type", "string")
                                put("description", "Relative directory path")
                            })
                        })
                        put("required", JSONArray().apply { put("path") })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "delete_file")
                    put("description", "Delete file or directory")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("path", JSONObject().apply {
                                put("type", "string")
                                put("description", "Relative path to file or directory")
                            })
                        })
                        put("required", JSONArray().apply { put("path") })
                    })
                })
            }
        )
    }

    fun validatePath(rootDir: File, rawPath: String, isMutating: Boolean = false, toolName: String = ""): File {
        val trimmed = rawPath.trim()
        val normalized = if (trimmed.isEmpty() || trimmed == "." || trimmed == "./" || trimmed == "/") "" else trimmed
        if (normalized.startsWith("/") || normalized.startsWith("\\") || normalized.contains("..") || normalized.contains(":\\")) {
            throw IllegalArgumentException("Invalid path: absolute paths and '..' are not allowed.")
        }
        val segments = normalized.split("/").filter { it.isNotEmpty() }
        if (isMutating) {
            for (seg in segments) {
                if (seg.startsWith(".") && seg.length > 1) {
                    throw IllegalArgumentException("Not allowed: cannot write/delete inside hidden folder '$seg'")
                }
            }
        }
        val target = if (normalized.isEmpty()) {
            rootDir
        } else {
            File(rootDir, normalized)
        }
        val canonicalRoot = rootDir.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalRoot.path)) {
            throw IllegalArgumentException("Path traversal outside project root is not allowed.")
        }
        return target
    }

    fun executeTool(rootDir: File, name: String, argumentsJson: String): String {
        val actualRoot = rootDir
        val prefix = "[root=${actualRoot.absolutePath}] "
        return try {
            val args = JSONObject(argumentsJson)
            when (name) {
                "list_directory" -> {
                    val pathStr = args.optString("path", "")
                    val file = validatePath(actualRoot, pathStr, isMutating = false, toolName = name)
                    if (!file.exists()) {
                        return "${prefix}Not found: $pathStr"
                    }
                    if (!file.isDirectory) {
                        return "${prefix}Error: Not a directory: $pathStr"
                    }
                    val children = file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
                    if (children.isEmpty()) {
                        return "(empty)"
                    }
                    val sb = StringBuilder()
                    for (child in children) {
                        val suffix = if (child.isDirectory) "/" else ""
                        sb.append("${child.name}$suffix\n")
                    }
                    val res = sb.toString().trimEnd()
                    if (res.isEmpty()) "(empty)" else res
                }
                "read_file" -> {
                    val pathStr = args.optString("path", "")
                    val file = validatePath(actualRoot, pathStr, isMutating = false, toolName = name)
                    if (!file.exists() || file.isDirectory) {
                        return "${prefix}Not found: $pathStr"
                    }
                    val text = file.readText(Charsets.UTF_8)
                    val contentHash = text.hashCode()
                    val entry = readHistory[pathStr]
                    if (entry != null && entry.second == contentHash) {
                        val newCount = entry.first + 1
                        readHistory[pathStr] = Pair(newCount, contentHash)
                        if (newCount >= 3) {
                            return "You already read this file twice and it has not changed. Act on it now."
                        }
                    } else {
                        readHistory[pathStr] = Pair(1, contentHash)
                    }

                    val startLine = args.optInt("start_line", 1).coerceAtLeast(1)
                    val endLine = args.optInt("end_line", 0)
                    val lines = text.lines()
                    val totalLines = lines.size

                    val sb = StringBuilder()
                    var currentChars = 0
                    var lastIncludedLine = startLine - 1
                    val maxChars = 30_000

                    for (idx in (startLine - 1) until totalLines) {
                        if (endLine > 0 && (idx + 1) > endLine) break
                        val lineText = lines[idx]
                        val lineLen = lineText.length + 1
                        if (sb.isNotEmpty() && currentChars + lineLen > maxChars) {
                            break
                        }
                        if (sb.isNotEmpty()) {
                            sb.append("\n")
                        }
                        sb.append(lineText)
                        currentChars += lineLen
                        lastIncludedLine = idx + 1
                    }

                    val resultText = sb.toString()
                    val isTruncated = lastIncludedLine < totalLines || (startLine > 1) || (endLine > 0 && lastIncludedLine < endLine && lastIncludedLine < totalLines)

                    val finalContent = if (isTruncated) {
                        val x = startLine
                        val y = if (lastIncludedLine < x) x else lastIncludedLine
                        val n = totalLines
                        "$resultText\n[truncated: showing lines $x-$y of $n. Call read_file again with start_line=${y + 1}]"
                    } else {
                        resultText
                    }
                    "Read $pathStr, lines $startLine-$lastIncludedLine of $totalLines (${resultText.length} chars):\n$finalContent"
                }
                "search_files" -> {
                    val query = args.optString("query", "").trim()
                    if (query.isEmpty()) {
                        return "Error: query cannot be empty"
                    }
                    val matches = mutableListOf<String>()
                    val binaryExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "jar", "dex", "zip", "so", "keystore", "pdf")

                    fun walk(dir: File) {
                        val children = dir.listFiles() ?: return
                        for (child in children) {
                            val childName = child.name
                            if (childName.startsWith(".") || childName == "build") continue
                            if (child.isDirectory) {
                                walk(child)
                            } else if (child.isFile) {
                                val ext = child.extension.lowercase()
                                if (ext in binaryExtensions || child.length() > 1_000_000) continue
                                try {
                                    val lines = child.readLines(Charsets.UTF_8)
                                    val relPath = child.toRelativeString(actualRoot)
                                    for ((idx, lineText) in lines.withIndex()) {
                                        if (lineText.contains(query, ignoreCase = true)) {
                                            matches.add("$relPath:${idx + 1}: $lineText")
                                            if (matches.size >= 40) return
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            if (matches.size >= 40) return
                        }
                    }
                    walk(actualRoot)
                    if (matches.isEmpty()) {
                        return "No matches found for '$query'"
                    }
                    matches.joinToString("\n")
                }
                "write_file" -> {
                    val pathStr = args.optString("path", "")
                    val fileName = File(pathStr).name.lowercase()
                    if (fileName == "build.gradle" || fileName == "build.gradle.kts" ||
                        fileName == "settings.gradle" || fileName == "settings.gradle.kts") {
                        return "${prefix}Error: This IDE does not use Gradle; edit project files instead."
                    }
                    val content = args.optString("content", "")
                    if (content.isEmpty() || content == "(content omitted)" || content.startsWith("[file written")) {
                        return "Rejected: that is placeholder text, not file content. Send the full real file content."
                    }
                    val file = validatePath(actualRoot, pathStr, isMutating = true, toolName = name)
                    
                    val existed = file.exists() && file.isFile
                    val oldLines = if (existed) countLines(file.readText(Charsets.UTF_8)) else 0
                    if (existed && file.readText(Charsets.UTF_8) == content) {
                        return "No change: file already has this exact content."
                    }

                    file.parentFile?.mkdirs()
                    val tempFile = File(file.parentFile ?: actualRoot, "${file.name}.tmp.${UUID.randomUUID()}")
                    tempFile.writeText(content, Charsets.UTF_8)
                    if (file.exists()) {
                        file.delete()
                    }
                    if (!tempFile.renameTo(file)) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                    if (!file.exists() || !file.isFile) {
                        return "${prefix}Error: Failed to write $pathStr"
                    }
                    val lines = countLines(content)
                    if (!existed) {
                        "Created $pathStr ($lines lines)"
                    } else {
                        "Overwrote $pathStr ($lines lines, was $oldLines lines)"
                    }
                }
                "create_directory" -> {
                    val pathStr = args.optString("path", "")
                    val trimmedPath = pathStr.trim()
                    if (trimmedPath.isEmpty() || trimmedPath == "." || trimmedPath == "./" || trimmedPath == "/") {
                        return "Already exists, nothing to do"
                    }
                    val file = validatePath(actualRoot, pathStr, isMutating = true, toolName = name)
                    if (file.exists() && file.isDirectory) {
                        return "Already exists, nothing to do"
                    }
                    file.mkdirs()
                    if (!file.exists() || !file.isDirectory) {
                        return "${prefix}Error: Failed to create directory $pathStr"
                    }
                    "Created $pathStr"
                }
                "delete_file" -> {
                    val pathStr = args.optString("path", "")
                    val file = validatePath(actualRoot, pathStr, isMutating = true, toolName = name)
                    if (!file.exists()) {
                        return "${prefix}Not found: $pathStr"
                    }
                    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (!deleted) {
                        return "${prefix}Error: Failed to delete $pathStr"
                    }
                    "Deleted $pathStr"
                }
                else -> "${prefix}Error: Unknown tool $name"
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            if (msg.startsWith("[root=")) msg else "$prefix$msg"
        }
    }

    private fun countLines(text: String): Int {
        if (text.isEmpty()) return 0
        return text.count { it == '\n' } + 1
    }
}
