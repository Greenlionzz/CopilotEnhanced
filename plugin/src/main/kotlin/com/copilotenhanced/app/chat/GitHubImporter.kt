package com.copilotenhanced.app.chat

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRepoRef(
    val owner: String,
    val repo: String,
    val branch: String,
    val path: String
)

data class GitHubSkillItem(
    val name: String,
    val path: String,
    val downloadUrl: String,
    val sourceUrl: String,
    var selected: Boolean = true
)

object GitHubImporter {

    fun parseUrl(rawUrl: String): GitHubRepoRef? {
        val trimmed = rawUrl.trim().removeSuffix("/")
        try {
            val uri = java.net.URI(trimmed)
            val segments = uri.path?.split("/")?.filter { it.isNotBlank() } ?: emptyList()
            if (segments.size < 2) return null
            val owner = segments[0]
            val repo = segments[1]
            var branch = "main"
            var path = ""

            if (segments.size >= 4 && (segments[2] == "tree" || segments[2] == "blob")) {
                branch = segments[3]
                if (segments.size > 4) {
                    path = segments.drop(4).joinToString("/")
                }
            }
            return GitHubRepoRef(owner, repo, branch, path)
        } catch (e: Exception) {
            return null
        }
    }

    fun fetchCandidateSkills(ref: GitHubRepoRef): List<GitHubSkillItem> {
        val items = mutableListOf<GitHubSkillItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. Check repo root for SKILL.md or .md files
        fetchDirectoryContents(ref.owner, ref.repo, "", ref.branch, items, seenPaths)

        // 2. Check skills/ directory if it exists (skills/*/SKILL.md)
        fetchDirectoryContents(ref.owner, ref.repo, "skills", ref.branch, items, seenPaths, recursiveSkills = true)

        // 3. Check given path if non-empty and different from root/skills
        if (ref.path.isNotBlank() && ref.path != "skills") {
            fetchDirectoryContents(ref.owner, ref.repo, ref.path, ref.branch, items, seenPaths)
        }

        return items
    }

    private fun fetchDirectoryContents(
        owner: String,
        repo: String,
        path: String,
        branch: String,
        outList: MutableList<GitHubSkillItem>,
        seenPaths: MutableSet<String>,
        recursiveSkills: Boolean = false
    ) {
        val apiUrl = if (path.isBlank()) {
            "https://api.github.com/repos/$owner/$repo/contents?ref=$branch"
        } else {
            "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
        }

        try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CodeAssist-AiStudio")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            if (responseCode == 404) {
                return
            }
            if (responseCode == 403 || responseCode == 429) {
                throw IOException("GitHub API rate limit exceeded (HTTP $responseCode). Please try again later.")
            }
            if (responseCode >= 400) {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (errorStream.contains("rate limit", ignoreCase = true)) {
                    throw IOException("GitHub API rate limit exceeded.")
                }
                throw IOException("GitHub API error: HTTP $responseCode")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonElement = body.trim()
            if (jsonElement.startsWith("[")) {
                val jsonArray = JSONArray(jsonElement)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val type = obj.optString("type", "")
                    val name = obj.optString("name", "")
                    val filePath = obj.optString("path", "")
                    val downloadUrl = obj.optString("download_url", "")
                    val htmlUrl = obj.optString("html_url", "")

                    if (type == "file") {
                        val isMarkdown = name.endsWith(".md", ignoreCase = true)
                        val isSkillMd = name.equals("SKILL.md", ignoreCase = true)
                        if (isSkillMd || isMarkdown) {
                            if (seenPaths.add(filePath)) {
                                if (downloadUrl.isNotBlank()) {
                                    outList.add(
                                        GitHubSkillItem(
                                            name = name,
                                            path = filePath,
                                            downloadUrl = downloadUrl,
                                            sourceUrl = htmlUrl.ifBlank { "https://github.com/$owner/$repo/blob/$branch/$filePath" }
                                        )
                                    )
                                }
                            }
                        }
                    } else if (type == "dir" && recursiveSkills && path.isBlank() && name == "skills") {
                        fetchDirectoryContents(owner, repo, filePath, branch, outList, seenPaths, recursiveSkills = false)
                    } else if (type == "dir" && recursiveSkills && path == "skills") {
                        val skillMdPath = "$filePath/SKILL.md"
                        if (seenPaths.add(skillMdPath)) {
                            fetchSingleFile(owner, repo, skillMdPath, branch, outList, seenPaths)
                        }
                    }
                }
            } else if (jsonElement.startsWith("{")) {
                val obj = JSONObject(jsonElement)
                val name = obj.optString("name", "")
                val filePath = obj.optString("path", "")
                val downloadUrl = obj.optString("download_url", "")
                val htmlUrl = obj.optString("html_url", "")
                if (name.endsWith(".md", ignoreCase = true) && seenPaths.add(filePath)) {
                    if (downloadUrl.isNotBlank()) {
                        outList.add(
                            GitHubSkillItem(
                                name = name,
                                path = filePath,
                                downloadUrl = downloadUrl,
                                sourceUrl = htmlUrl.ifBlank { "https://github.com/$owner/$repo/blob/$branch/$filePath" }
                            )
                        )
                    }
                }
            }
        } catch (e: java.net.UnknownHostException) {
            throw IOException("No internet connection or network error. Check your connection.")
        } catch (e: java.net.SocketTimeoutException) {
            throw IOException("Network request timed out. Please check your connection.")
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun fetchSingleFile(
        owner: String,
        repo: String,
        path: String,
        branch: String,
        outList: MutableList<GitHubSkillItem>,
        seenPaths: MutableSet<String>
    ) {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
        try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CodeAssist-AiStudio")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val name = obj.optString("name", "")
                val filePath = obj.optString("path", "")
                val downloadUrl = obj.optString("download_url", "")
                val htmlUrl = obj.optString("html_url", "")
                if (name.isNotBlank() && downloadUrl.isNotBlank() && seenPaths.add(filePath)) {
                    outList.add(
                        GitHubSkillItem(
                            name = name,
                            path = filePath,
                            downloadUrl = downloadUrl,
                            sourceUrl = htmlUrl.ifBlank { "https://github.com/$owner/$repo/blob/$branch/$filePath" }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun downloadMarkdown(downloadUrl: String): String {
        val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CodeAssist-AiStudio")
            connectTimeout = 10000
            readTimeout = 10000
        }
        if (conn.responseCode != 200) {
            throw IOException("Failed to download file: HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
