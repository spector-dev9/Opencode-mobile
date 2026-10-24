package io.github.spectordev9.opencode.ui.util

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock

    data class Code(val code: String, val language: String?) : MarkdownBlock
}

/**
 * A deliberately small markdown splitter for chat rendering. It recognises
 * fenced code blocks and keeps every other line as a paragraph. Full
 * markdown rendering is presentation work that can replace this without
 * changing the transcript models.
 */
object MarkdownLite {
    fun parse(text: String): List<MarkdownBlock> {
        if (text.isEmpty()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var language: String? = null
        var inCode = false

        fun flushParagraph() {
            val value = paragraph.joinToString("\n").trim()
            if (value.isNotEmpty()) blocks += MarkdownBlock.Paragraph(value)
            paragraph.clear()
        }

        fun flushCode() {
            blocks += MarkdownBlock.Code(code.joinToString("\n"), language)
            code.clear()
            language = null
        }

        text.split('\n').forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    flushCode()
                    inCode = false
                } else {
                    flushParagraph()
                    language = trimmed.removePrefix("```").trim().ifEmpty { null }
                    inCode = true
                }
            } else if (inCode) {
                code += line
            } else {
                paragraph += line
            }
        }

        if (inCode) flushCode()
        flushParagraph()
        return blocks
    }
}
