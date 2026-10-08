package com.copilotenhanced.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

sealed interface MarkdownNode {
    data class Paragraph(val text: String) : MarkdownNode
    data class Header(val level: Int, val text: String) : MarkdownNode
    data class CodeBlock(val language: String, val code: String) : MarkdownNode
    data class ListItem(val text: String, val prefix: String) : MarkdownNode
    data class Quote(val text: String) : MarkdownNode
}

object MarkdownParser {
    fun parse(raw: String): List<MarkdownNode> {
        val nodes = mutableListOf<MarkdownNode>()
        val lines = raw.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Code block start
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                nodes.add(MarkdownNode.CodeBlock(language = lang, code = codeLines.joinToString("\n")))
                i++ // skip closing ```
                continue
            }

            // Headers
            if (trimmed.startsWith("### ")) {
                nodes.add(MarkdownNode.Header(3, trimmed.removePrefix("### ").trim()))
                i++
                continue
            }
            if (trimmed.startsWith("## ")) {
                nodes.add(MarkdownNode.Header(2, trimmed.removePrefix("## ").trim()))
                i++
                continue
            }
            if (trimmed.startsWith("# ")) {
                nodes.add(MarkdownNode.Header(1, trimmed.removePrefix("# ").trim()))
                i++
                continue
            }

            // Blockquote
            if (trimmed.startsWith("> ")) {
                nodes.add(MarkdownNode.Quote(trimmed.removePrefix("> ").trim()))
                i++
                continue
            }

            // Bullet list
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                val text = trimmed.substring(2).trim()
                nodes.add(MarkdownNode.ListItem(text = text, prefix = "• "))
                i++
                continue
            }

            // Numbered list
            val matchOrdered = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
            if (matchOrdered != null) {
                val num = matchOrdered.groupValues[1]
                val text = matchOrdered.groupValues[2]
                nodes.add(MarkdownNode.ListItem(text = text, prefix = "$num. "))
                i++
                continue
            }

            // Empty line
            if (trimmed.isBlank()) {
                i++
                continue
            }

            // Regular paragraph
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val current = lines[i]
                val curTrim = current.trim()
                if (curTrim.isBlank() || curTrim.startsWith("```") || curTrim.startsWith("#") ||
                    curTrim.startsWith("> ") || curTrim.startsWith("- ") || curTrim.startsWith("* ") ||
                    Regex("^(\\d+)\\.\\s+.*").matches(curTrim)
                ) {
                    break
                }
                paraLines.add(current)
                i++
            }
            nodes.add(MarkdownNode.Paragraph(paraLines.joinToString("\n")))
        }
        return nodes
    }
}

@Composable
fun MarkdownView(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val nodes = remember(content) { MarkdownParser.parse(content) }

    Column(modifier = modifier) {
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            when (node) {
                is MarkdownNode.Header -> {
                    val style = when (node.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = buildAnnotatedMarkdown(node.text, textColor),
                        style = style,
                        color = textColor,
                    )
                }
                is MarkdownNode.Paragraph -> {
                    Text(
                        text = buildAnnotatedMarkdown(node.text, textColor),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = textColor,
                    )
                }
                is MarkdownNode.ListItem -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = node.prefix,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            modifier = Modifier.padding(start = 4.dp, end = 6.dp),
                        )
                        Text(
                            text = buildAnnotatedMarkdown(node.text, textColor),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = textColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MarkdownNode.Quote -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedMarkdown(node.text, textColor.copy(alpha = 0.85f)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = textColor.copy(alpha = 0.85f),
                        )
                    }
                }
                is MarkdownNode.CodeBlock -> {
                    CodeBlockView(
                        language = node.language,
                        code = node.code,
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val codeBgColor = MaterialTheme.colorScheme.surfaceContainerHighest ?: MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        color = codeBgColor,
    ) {
        Column {
            // Code block header with language and Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = if (copied) "✓ Copied" else "📋 Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

fun buildAnnotatedMarkdown(text: String, defaultColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        // Inline code `...`
        if (text[i] == '`') {
            val endIdx = text.indexOf('`', i + 1)
            if (endIdx != -1) {
                val codeSnippet = text.substring(i + 1, endIdx)
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = defaultColor.copy(alpha = 0.1f),
                        fontWeight = FontWeight.Medium,
                    )
                )
                builder.append(" $codeSnippet ")
                builder.pop()
                i = endIdx + 1
                continue
            }
        }

        // Bold **...** or __...__
        if ((text.startsWith("**", i) || text.startsWith("__", i)) && i + 2 < text.length) {
            val delim = text.substring(i, i + 2)
            val endIdx = text.indexOf(delim, i + 2)
            if (endIdx != -1) {
                val boldText = text.substring(i + 2, endIdx)
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(boldText)
                builder.pop()
                i = endIdx + 2
                continue
            }
        }

        // Italic *...* or _..._
        if ((text[i] == '*' || text[i] == '_') && i + 1 < text.length && text.getOrNull(i + 1) != text[i]) {
            val delim = text[i]
            val endIdx = text.indexOf(delim, i + 1)
            if (endIdx != -1) {
                val italicText = text.substring(i + 1, endIdx)
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(italicText)
                builder.pop()
                i = endIdx + 1
                continue
            }
        }

        builder.append(text[i])
        i++
    }
    return builder.toAnnotatedString()
}
