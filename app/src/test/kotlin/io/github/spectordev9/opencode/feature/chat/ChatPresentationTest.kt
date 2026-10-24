package io.github.spectordev9.opencode.feature.chat

import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.Part
import io.github.spectordev9.opencode.testing.FakeChatRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatPresentationTest {
    private val sessionId = "ses_1"

    @Test
    fun `maps message metadata and filters hidden parts`() {
        val info = FakeChatRepository.info(
            id = "msg_1",
            sessionId = sessionId,
            role = "assistant",
            created = 1_000,
            completed = 5_000,
            cost = 0.02,
            tokens = buildJsonObject {
                put("input", 100)
                put("output", 20)
                put("cache", buildJsonObject {
                    put("read", 5)
                    put("write", 1)
                })
            },
        )
        val parts = listOf(
            FakeChatRepository.textPart("prt_text", sessionId, "msg_1", "hello"),
            FakeChatRepository.textPart("prt_hidden", sessionId, "msg_1", "hidden").copy(synthetic = true),
            Part(
                id = "prt_reasoning",
                sessionID = sessionId,
                messageID = "msg_1",
                type = "reasoning",
                text = "thinking",
            ),
            FakeChatRepository.toolPart(
                id = "prt_tool",
                sessionId = sessionId,
                messageId = "msg_1",
                tool = "read",
                status = "completed",
                title = "src/Main.kt",
                output = "42 lines",
            ),
            Part(
                id = "prt_file",
                sessionID = sessionId,
                messageID = "msg_1",
                type = "file",
                filename = "notes.md",
                mime = "text/markdown",
            ),
            Part(
                id = "prt_step",
                sessionID = sessionId,
                messageID = "msg_1",
                type = "step-start",
            ),
        )

        val ui = Message(info, parts).toChatMessageUi()

        assertEquals(ChatRole.ASSISTANT, ui.role)
        assertEquals(1_000L, ui.createdAtMillis)
        assertEquals(5_000L, ui.completedAtMillis)
        assertFalse(ui.isStreaming)
        assertEquals("provider/model", ui.modelLabel)
        assertEquals(0.02, ui.cost)
        assertEquals(126L, ui.tokens?.total)
        assertEquals(listOf("prt_text", "prt_reasoning", "prt_tool", "prt_file"), ui.parts.map { it.id })
        assertEquals("hello", (ui.parts[0] as ChatPartUi.Text).text)
        assertEquals("thinking", (ui.parts[1] as ChatPartUi.Reasoning).text)
        val tool = ui.parts[2] as ChatPartUi.Tool
        assertEquals(ToolRunStatus.COMPLETED, tool.status)
        assertEquals("read", tool.name)
        assertEquals("src/Main.kt", tool.title)
        assertEquals("42 lines", tool.detail)
        val file = ui.parts[3] as ChatPartUi.FileAttachment
        assertEquals("notes.md", file.filename)
        assertEquals("text/markdown", file.mime)
    }

    @Test
    fun `supports start and end timestamps and running tools`() {
        val info = FakeChatRepository.info(
            id = "msg_2",
            sessionId = sessionId,
            role = "assistant",
        ).copy(
            time = buildJsonObject {
                put("start", 2_000)
            },
            modelID = null,
            providerID = null,
        )
        val part = FakeChatRepository.toolPart(
            id = "prt_tool",
            sessionId = sessionId,
            messageId = "msg_2",
            tool = "bash",
            status = "running",
        )

        val ui = Message(info, listOf(part)).toChatMessageUi()

        assertEquals(2_000L, ui.createdAtMillis)
        assertNull(ui.completedAtMillis)
        assertTrue(ui.isStreaming)
        assertNull(ui.modelLabel)
        assertEquals(ToolRunStatus.RUNNING, (ui.parts.single() as ChatPartUi.Tool).status)
    }

    @Test
    fun `latest reported usage uses the last assistant message`() {
        val first = Message(
            info = FakeChatRepository.info(
                id = "msg_1",
                sessionId = sessionId,
                role = "assistant",
                tokens = buildJsonObject { put("input", 10) },
            ),
            parts = emptyList(),
        )
        val last = Message(
            info = FakeChatRepository.info(
                id = "msg_2",
                sessionId = sessionId,
                role = "assistant",
                cost = 0.04,
                tokens = buildJsonObject {
                    put("input", 100)
                    put("output", 50)
                },
            ),
            parts = emptyList(),
        )

        val usage = listOf(first, last).latestReportedUsage()

        assertEquals(150L, usage?.reportedTokens)
        assertEquals(0.04, usage?.cost)
        assertEquals("provider/model", usage?.modelLabel)
    }
}
