package com.copilotenhanced.app.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.lang.Character

sealed interface ParsedPart {
    data class Text(val content: String) : ParsedPart
    data class Questions(val raw: String, val data: QuestionsPayload?) : ParsedPart {
        val json: String get() = raw
    }
    data class InvalidQuestions(val raw: String) : ParsedPart
    data class Plan(val title: String?, val version: Int?, val content: String) : ParsedPart
    data class Progress(val milestone: String?, val status: String?, val note: String) : ParsedPart
}

object QuestionOptionSerializer : KSerializer<QuestionOption> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("QuestionOption")

    override fun deserialize(decoder: Decoder): QuestionOption {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> QuestionOption(label = element.content)
                is JsonObject -> {
                    val emoji = (element["emoji"] as? JsonPrimitive)?.content
                    val label = (element["label"] as? JsonPrimitive)?.content.orEmpty()
                    val description = (element["description"] as? JsonPrimitive)?.content
                    QuestionOption(emoji = emoji, label = label, description = description)
                }
                else -> QuestionOption(label = element.toString())
            }
        } else {
            QuestionOption(label = decoder.decodeString())
        }
    }

    override fun serialize(encoder: Encoder, value: QuestionOption) {
        if (encoder is JsonEncoder) {
            val map = mutableMapOf<String, JsonElement>()
            value.emoji?.let { map["emoji"] = JsonPrimitive(it) }
            map["label"] = JsonPrimitive(value.label)
            value.description?.let { map["description"] = JsonPrimitive(it) }
            encoder.encodeJsonElement(JsonObject(map))
        } else {
            encoder.encodeString(value.label)
        }
    }
}

@Serializable(with = QuestionOptionSerializer::class)
data class QuestionOption(
    val emoji: String? = null,
    val label: String = "",
    val description: String? = null
)

@Serializable
data class QuestionItem(
    val id: String = "",
    val question: String = "",
    val type: String = "single",
    val options: List<QuestionOption> = emptyList(),
    val allowCustom: Boolean = false
)

@Serializable
data class QuestionsPayload(
    val questions: List<QuestionItem> = emptyList()
)

class ProtocolParser {
    private val buffer = StringBuilder()
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun feed(chunk: String): List<ParsedPart> {
        buffer.append(chunk)
        return parseBuffer(flush = false)
    }

    fun flush(): List<ParsedPart> {
        return parseBuffer(flush = true)
    }

    fun reset() {
        buffer.clear()
    }

    private fun stripStrayClosingTags(text: String): String {
        return text.replace(Regex("""</(?:questions|plan|progress)(?:\s*>)?\s*""", RegexOption.IGNORE_CASE), "")
    }

    private fun parseBuffer(flush: Boolean): List<ParsedPart> {
        var text = buffer.toString()
        buffer.clear()

        val parts = mutableListOf<ParsedPart>()

        if (!flush) {
            val lastOpen = text.lastIndexOf('<')
            val lastClose = text.lastIndexOf('>')
            if (lastOpen > lastClose) {
                val safeText = text.substring(0, lastOpen)
                val trailing = text.substring(lastOpen)
                buffer.append(trailing)
                text = safeText
            }
        }

        var currentIndex = 0
        while (currentIndex < text.length) {
            val subText = text.substring(currentIndex)

            val qMatch = Regex("""<questions\b[^>]*>""", RegexOption.IGNORE_CASE).find(subText)
            val pMatch = Regex("""<plan\b([^>]*)>""", RegexOption.IGNORE_CASE).find(subText)
            val progMatch = Regex("""<progress\b([^>]*)>""", RegexOption.IGNORE_CASE).find(subText)

            val matches = listOfNotNull(
                qMatch?.let { Triple(it.range.first, "questions", it) },
                pMatch?.let { Triple(it.range.first, "plan", it) },
                progMatch?.let { Triple(it.range.first, "progress", it) }
            ).sortedBy { it.first }

            if (matches.isEmpty()) {
                val cleanedText = stripStrayClosingTags(subText)
                if (cleanedText.isNotEmpty()) {
                    parts.add(ParsedPart.Text(cleanedText))
                }
                break
            }

            val (relativeStart, tagType, matchResult) = matches.first()
            val absoluteStart = currentIndex + relativeStart

            if (absoluteStart > currentIndex) {
                val plainText = text.substring(currentIndex, absoluteStart)
                val cleanedText = stripStrayClosingTags(plainText)
                if (cleanedText.isNotEmpty()) {
                    parts.add(ParsedPart.Text(cleanedText))
                }
            }

            val closingTag = "</$tagType>"
            val openTagEnd = absoluteStart + matchResult.value.length
            val closeIndex = text.indexOf(closingTag, openTagEnd, ignoreCase = true)

            if (closeIndex == -1) {
                if (flush) {
                    val innerContent = text.substring(openTagEnd)
                    val fullBlock = text.substring(absoluteStart)
                    when (tagType) {
                        "questions" -> {
                            val payload = parseQuestionsPayload(innerContent)
                            if (payload != null && payload.questions.isNotEmpty()) {
                                parts.add(ParsedPart.Questions(innerContent.trim(), payload))
                            } else {
                                parts.add(ParsedPart.InvalidQuestions(fullBlock))
                            }
                        }
                        "plan" -> {
                            val title = extractAttribute(matchResult.value, "title")
                            val versionStr = extractAttribute(matchResult.value, "version")
                            val version = versionStr?.toIntOrNull()
                            parts.add(ParsedPart.Plan(title, version, innerContent.trim()))
                        }
                        "progress" -> {
                            val milestone = extractAttribute(matchResult.value, "milestone")
                            val status = extractAttribute(matchResult.value, "status")
                            parts.add(ParsedPart.Progress(milestone, status, innerContent.trim()))
                        }
                        else -> {
                            parts.add(ParsedPart.Text(fullBlock))
                        }
                    }
                    currentIndex = text.length
                } else {
                    buffer.append(text.substring(absoluteStart))
                    break
                }
            } else {
                val innerContent = text.substring(openTagEnd, closeIndex)
                val openTagStr = matchResult.value

                when (tagType) {
                    "questions" -> {
                        val payload = parseQuestionsPayload(innerContent)
                        if (payload != null && payload.questions.isNotEmpty()) {
                            parts.add(ParsedPart.Questions(innerContent.trim(), payload))
                        } else {
                            val fullBlock = text.substring(absoluteStart, closeIndex + closingTag.length)
                            parts.add(ParsedPart.InvalidQuestions(fullBlock))
                        }
                    }
                    "plan" -> {
                        val title = extractAttribute(openTagStr, "title")
                        val versionStr = extractAttribute(openTagStr, "version")
                        val version = versionStr?.toIntOrNull()
                        parts.add(ParsedPart.Plan(title, version, innerContent.trim()))
                    }
                    "progress" -> {
                        val milestone = extractAttribute(openTagStr, "milestone")
                        val status = extractAttribute(openTagStr, "status")
                        parts.add(ParsedPart.Progress(milestone, status, innerContent.trim()))
                    }
                }
                currentIndex = closeIndex + closingTag.length
            }
        }

        return parts
    }

    private fun parseQuestionsPayload(content: String): QuestionsPayload? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        // 1. Try line format
        val linePayload = parseQuestionsLineFormat(trimmed)
        if (linePayload != null && linePayload.questions.isNotEmpty()) {
            return linePayload
        }

        // 2. Fallback to old JSON format
        return try {
            val jsonPayload = jsonParser.decodeFromString<QuestionsPayload>(trimmed)
            if (jsonPayload.questions.isNotEmpty()) jsonPayload else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQuestionsLineFormat(content: String): QuestionsPayload? {
        val lines = content.lines()
        val questions = mutableListOf<QuestionItem>()

        var currentQuestion: String? = null
        var currentType = "single"
        var currentAllowCustom = false
        var currentOptions = mutableListOf<QuestionOption>()

        fun saveCurrentQuestion() {
            val qText = currentQuestion ?: return
            if (qText.isNotBlank()) {
                questions.add(
                    QuestionItem(
                        id = "q${questions.size + 1}",
                        question = qText,
                        type = currentType,
                        options = currentOptions.toList(),
                        allowCustom = currentAllowCustom
                    )
                )
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue // trim blanks

            if (line.startsWith("##")) {
                saveCurrentQuestion()
                currentQuestion = line.removePrefix("##").trim()
                currentType = "single"
                currentAllowCustom = false
                currentOptions = mutableListOf()
            } else if (currentQuestion != null) {
                if (line.startsWith("-")) {
                    val opt = parseOptionLine(line.removePrefix("-").trim())
                    if (opt != null) {
                        currentOptions.add(opt)
                    }
                } else {
                    val lower = line.lowercase()
                    if (lower.startsWith("single") || lower.startsWith("multi") || lower.contains("custom")) {
                        currentType = if (lower.contains("multi")) "multi" else "single"
                        if (lower.contains("custom")) {
                            currentAllowCustom = true
                        }
                    }
                    // ignore unknown lines
                }
            }
            // lines before the first ## are ignored (ignore unknown lines)
        }

        saveCurrentQuestion()

        return if (questions.isNotEmpty()) QuestionsPayload(questions) else null
    }

    private fun parseOptionLine(content: String): QuestionOption? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split("|", limit = 2)
        val left = parts[0].trim()
        val description = if (parts.size > 1) parts[1].trim().ifBlank { null } else null

        if (left.isEmpty()) return null

        val (emoji, label) = parseEmojiAndLabel(left)
        return QuestionOption(
            emoji = emoji,
            label = label,
            description = description
        )
    }

    private fun parseEmojiAndLabel(raw: String): Pair<String?, String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Pair(null, "")

        val firstCp = trimmed.codePointAt(0)
        if (isEmoji(firstCp)) {
            val spaceIdx = trimmed.indexOf(' ')
            if (spaceIdx != -1) {
                val emojiPart = trimmed.substring(0, spaceIdx).trim()
                val labelPart = trimmed.substring(spaceIdx + 1).trim()
                if (labelPart.isNotEmpty()) {
                    return Pair(emojiPart, labelPart)
                }
            }
            var idx = java.lang.Character.charCount(firstCp)
            while (idx < trimmed.length) {
                val nextCp = trimmed.codePointAt(idx)
                if (nextCp == 0xFE0F || nextCp == 0x200D || (nextCp in 0x1F3FB..0x1F3FF) || isEmoji(nextCp)) {
                    idx += java.lang.Character.charCount(nextCp)
                } else {
                    break
                }
            }
            val emojiPart = trimmed.substring(0, idx).trim()
            val labelPart = trimmed.substring(idx).trim()
            return Pair(emojiPart.ifBlank { null }, if (labelPart.isNotEmpty()) labelPart else emojiPart)
        }
        return Pair(null, trimmed)
    }

    private fun isEmoji(codePoint: Int): Boolean {
        val type = java.lang.Character.getType(codePoint)
        if (type == java.lang.Character.OTHER_SYMBOL.toInt() ||
            type == java.lang.Character.MODIFIER_SYMBOL.toInt() ||
            type == java.lang.Character.SURROGATE.toInt()
        ) {
            return true
        }
        return (codePoint in 0x1F000..0x1FAFF) ||
                (codePoint in 0x2600..0x27BF) ||
                (codePoint in 0x2300..0x23FF) ||
                (codePoint in 0x2B00..0x2BFF)
    }

    private fun extractAttribute(tagAttributes: String, name: String): String? {
        val pattern = Regex("""$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val match = pattern.find(tagAttributes) ?: return null
        return match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value
    }
}

fun ParsedPart.toRawText(): String = when (this) {
    is ParsedPart.Text -> content
    is ParsedPart.Questions -> if (raw.startsWith("<questions")) raw else "<questions>\n$raw\n</questions>"
    is ParsedPart.InvalidQuestions -> raw
    is ParsedPart.Plan -> {
        val titleAttr = if (title != null) " title=\"$title\"" else ""
        val verAttr = if (version != null) " version=\"$version\"" else ""
        "<plan$titleAttr$verAttr>$content</plan>"
    }
    is ParsedPart.Progress -> {
        val mAttr = if (milestone != null) " milestone=\"$milestone\"" else ""
        val sAttr = if (status != null) " status=\"$status\"" else ""
        "<progress$mAttr$sAttr>$note</progress>"
    }
}
