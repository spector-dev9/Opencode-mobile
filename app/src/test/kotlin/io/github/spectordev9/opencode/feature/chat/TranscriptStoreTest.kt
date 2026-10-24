package io.github.spectordev9.opencode.feature.chat

import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.testing.FakeChatRepository
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptStoreTest {
    private val sessionId = "ses_1"

    @Test
    fun `server connected buffers events until the snapshot is replayed`() {
        val store = TranscriptStore(sessionId)
        val connected = ServerEvent(type = "server.connected", id = "evt_connected")

        assertEquals(ReceiveOutcome.RESYNC, store.receive(connected))
        assertTrue(store.isBuffering)

        val info = FakeChatRepository.info(
            id = "msg_live",
            sessionId = sessionId,
            role = "assistant",
        )
        assertEquals(
            ReceiveOutcome.BUFFERED,
            store.receive(FakeChatRepository().messageUpdated(info)),
        )
        assertEquals(
            ReceiveOutcome.BUFFERED,
            store.receive(
                ServerEvent(
                    type = "message.part.delta",
                    id = "evt_delta",
                    payload = mapOf(
                        "sessionID" to sessionId,
                        "messageID" to "msg_live",
                        "partID" to "prt_live",
                        "field" to "text",
                        "delta" to "streamed",
                    ),
                ),
            ),
        )

        val snapshotInfo = FakeChatRepository.info(
            id = "msg_snapshot",
            sessionId = sessionId,
            role = "assistant",
        )
        store.completeResync(
            listOf(
                Message(
                    info = snapshotInfo,
                    parts = listOf(
                        FakeChatRepository.textPart(
                            id = "prt_snapshot",
                            sessionId = sessionId,
                            messageId = "msg_snapshot",
                            text = "snapshot",
                        ),
                    ),
                ),
            ),
        )

        assertFalse(store.isBuffering)
        val transcript = store.snapshot()
        assertEquals(listOf("msg_snapshot", "msg_live"), transcript.map { it.info.id })
        assertEquals("streamed", transcript.last().parts.single().text)
    }

    @Test
    fun `failed resync keeps the old transcript and replays the buffer`() {
        val store = TranscriptStore(sessionId)
        val initialInfo = FakeChatRepository.info(
            id = "msg_initial",
            sessionId = sessionId,
            role = "assistant",
        )
        store.replace(listOf(Message(initialInfo, emptyList())))
        store.receive(ServerEvent(type = "server.connected", id = "evt_connected"))
        store.receive(
            ServerEvent(
                type = "message.part.delta",
                id = "evt_delta",
                payload = mapOf(
                    "sessionID" to sessionId,
                    "messageID" to "msg_initial",
                    "partID" to "prt_initial",
                    "field" to "text",
                    "delta" to "after",
                ),
            ),
        )

        store.completeResync(null)

        assertFalse(store.isBuffering)
        assertEquals("after", store.snapshot().single().parts.single().text)
    }

    @Test
    fun `duplicate event ids are ignored`() {
        val store = TranscriptStore(sessionId)
        val event = ServerEvent(
            type = "message.part.delta",
            id = "evt_same",
            payload = mapOf(
                "sessionID" to sessionId,
                "messageID" to "msg_1",
                "partID" to "prt_1",
                "field" to "text",
                "delta" to "once",
            ),
        )

        assertEquals(ReceiveOutcome.APPLIED, store.receive(event))
        assertEquals(ReceiveOutcome.DUPLICATE, store.receive(event))
        assertEquals("once", store.snapshot().single().parts.single().text)
    }

    @Test
    fun `events for another session are ignored`() {
        val store = TranscriptStore(sessionId)
        val event = ServerEvent(
            type = "message.updated",
            id = "evt_other",
            payload = mapOf(
                "sessionID" to "ses_other",
                "info" to JsonObject(emptyMap()),
            ),
        )

        assertEquals(ReceiveOutcome.IGNORED, store.receive(event))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `older part revisions do not overwrite newer text`() {
        val store = TranscriptStore(sessionId)
        val newer = FakeChatRepository.textPart(
            id = "prt_1",
            sessionId = sessionId,
            messageId = "msg_1",
            text = "newer",
            start = 3_000,
        )
        val older = FakeChatRepository.textPart(
            id = "prt_1",
            sessionId = sessionId,
            messageId = "msg_1",
            text = "older",
            start = 1_000,
        )

        store.receive(FakeChatRepository().partUpdated(newer))
        store.receive(FakeChatRepository().partUpdated(older))

        assertEquals("newer", store.snapshot().single().parts.single().text)
    }

    @Test
    fun `removed parts and messages update the transcript`() {
        val repo = FakeChatRepository()
        val info = FakeChatRepository.info(
            id = "msg_1",
            sessionId = sessionId,
            role = "assistant",
        )
        val part = FakeChatRepository.textPart(
            id = "prt_1",
            sessionId = sessionId,
            messageId = "msg_1",
            text = "remove me",
        )
        val store = TranscriptStore(sessionId)
        store.replace(listOf(Message(info, listOf(part))))

        assertEquals(
            ReceiveOutcome.APPLIED,
            store.receive(
                ServerEvent(
                    type = "message.part.removed",
                    id = "evt_removed_part",
                    payload = mapOf("sessionID" to sessionId, "partID" to "prt_1"),
                ),
            ),
        )
        assertTrue(store.snapshot().single().parts.isEmpty())

        assertEquals(
            ReceiveOutcome.APPLIED,
            store.receive(
                ServerEvent(
                    type = "message.removed",
                    id = "evt_removed_message",
                    payload = mapOf("sessionID" to sessionId, "messageID" to "msg_1"),
                ),
            ),
        )
        assertTrue(store.snapshot().isEmpty())
        assertNull(store.message("msg_1"))
    }

    @Test
    fun `malformed removed part payload does not throw`() {
        val store = TranscriptStore(sessionId)
        val outcome = store.receive(
            ServerEvent(
                type = "message.part.removed",
                id = "evt_bad_removed",
                payload = mapOf(
                    "sessionID" to sessionId,
                    "part" to "not-an-object",
                ),
            ),
        )

        assertEquals(ReceiveOutcome.APPLIED, outcome)
    }
}
