package io.github.spectordev9.opencode.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownLiteTest {
    @Test
    fun `splits paragraphs and fenced code`() {
        val blocks = MarkdownLite.parse(
            """
            First paragraph.

            ```kotlin
            val answer = 42
            ```

            Last paragraph.
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        assertEquals("First paragraph.", (blocks[0] as MarkdownBlock.Paragraph).text)
        val code = assertIs<MarkdownBlock.Code>(blocks[1])
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42", code.code)
        assertEquals("Last paragraph.", (blocks[2] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun `keeps an unterminated code block`() {
        val blocks = MarkdownLite.parse("before\n```\nunfinished")

        assertEquals(2, blocks.size)
        val code = assertIs<MarkdownBlock.Code>(blocks[1])
        assertEquals("unfinished", code.code)
        assertEquals(null, code.language)
    }

    @Test
    fun `empty text produces no blocks`() {
        assertTrue(MarkdownLite.parse("").isEmpty())
    }
}
