package com.arcana.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Renders a tightly-scoped subset of markdown — what the rule-based interpreter
 * and most Claude responses produce — directly into Compose composables.
 *
 * Supported:
 *   - `### Heading` (h3) — bigger weight
 *   - `## Heading` / `# Heading` — title-sized
 *   - `---` on its own line — horizontal divider
 *   - `**bold**` and `_italic_` inline
 *   - blank line = paragraph break
 *   - bare lines = single paragraph (newlines collapsed within)
 *
 * Anything fancier is rendered as plain text so we don't surprise the reader.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = block.annotated,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (idx == 0) 0.dp else 12.dp, bottom = 4.dp),
                )
                is MarkdownBlock.Paragraph -> Text(
                    text = block.annotated,
                    style = baseStyle,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                MarkdownBlock.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val annotated: AnnotatedString) : MarkdownBlock
    data class Paragraph(val annotated: AnnotatedString) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private fun parseBlocks(text: String): List<MarkdownBlock> {
    val out = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        when {
            line.isBlank() -> { i++ }
            line == "---" || line == "***" -> { out.add(MarkdownBlock.Divider); i++ }
            line.startsWith("### ") -> { out.add(MarkdownBlock.Heading(3, parseInline(line.removePrefix("### ")))); i++ }
            line.startsWith("## ") -> { out.add(MarkdownBlock.Heading(2, parseInline(line.removePrefix("## ")))); i++ }
            line.startsWith("# ") -> { out.add(MarkdownBlock.Heading(1, parseInline(line.removePrefix("# ")))); i++ }
            else -> {
                // Collect contiguous non-blank, non-special lines as one paragraph.
                val buf = StringBuilder()
                while (i < lines.size) {
                    val l = lines[i].trimEnd()
                    if (l.isBlank() || l == "---" || l == "***" ||
                        l.startsWith("# ") || l.startsWith("## ") || l.startsWith("### ")) break
                    if (buf.isNotEmpty()) buf.append(' ')
                    buf.append(l)
                    i++
                }
                if (buf.isNotEmpty()) out.add(MarkdownBlock.Paragraph(parseInline(buf.toString())))
            }
        }
    }
    return out
}

private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // **bold**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) { append(text.substring(i)); i = text.length }
                else {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                }
            }
            // _italic_ (only at word boundary so we don't mangle snake_case if it appears)
            text[i] == '_' && (i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                val end = text.indexOf('_', i + 1)
                if (end == -1) { append(text[i]); i++ }
                else {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
