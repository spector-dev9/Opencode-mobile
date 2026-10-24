package io.github.spectordev9.opencode.feature.chat

import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.PermissionRequest
import io.github.spectordev9.opencode.testing.FakeChatRepository
import io.github.spectordev9.opencode.ui.port.PermissionDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val viewModelScope = CoroutineScope(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        viewModelScope.cancel()
    }

    private val sessionId = "ses_1"

    private fun userMessage(): Message = Message(
        info = FakeChatRepository.info(
            id = "msg_user",
            sessionId = sessionId,
            role = "user",
            created = 1_000,
            completed = 1_100,
        ),
        parts = listOf(
            FakeChatRepository.textPart(
                id = "prt_user",
                sessionId = sessionId,
                messageId = "msg_user",
                text = "hello",
            ),
        ),
    )

    @Test
    fun `loads the transcript and applies a streamed reply`() = runTest {
        val repository = FakeChatRepository().apply {
            transcripts[sessionId] = mutableListOf(userMessage())
        }
        val viewModel = ChatViewModel(sessionId, "Test session", repository, viewModelScope)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.messages.size)
        assertEquals("Test session", viewModel.state.value.title)
        assertFalse(viewModel.state.value.isLoading)

        viewModel.onPromptChanged("  explain this  ")
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(sessionId to "explain this", repository.promptCalls.single())
        assertEquals("", viewModel.state.value.prompt)
        assertTrue(viewModel.state.value.isStreaming)

        val assistant = FakeChatRepository.info(
            id = "msg_assistant",
            sessionId = sessionId,
            role = "assistant",
            created = 2_000,
        )
        val emptyPart = FakeChatRepository.textPart(
            id = "prt_assistant",
            sessionId = sessionId,
            messageId = "msg_assistant",
            text = "",
            start = 2_000,
        )
        repository.emit(repository.messageUpdated(assistant))
        repository.emit(repository.partUpdated(emptyPart))
        repository.emit(
            repository.partDelta(
                sessionId = sessionId,
                messageId = "msg_assistant",
                partId = "prt_assistant",
                delta = "Hi",
            ),
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.messages.size)
        val streaming = viewModel.state.value.messages.last()
        assertEquals("Hi", (streaming.parts.single() as ChatPartUi.Text).text)
        assertTrue(streaming.isStreaming)

        val completed = assistant.copy(
            time = buildJsonObject {
                put("created", 2_000)
                put("completed", 5_000)
            },
            tokens = buildJsonObject {
                put("input", 10)
                put("output", 5)
            },
        )
        repository.emit(
            repository.partUpdated(
                FakeChatRepository.textPart(
                    id = "prt_assistant",
                    sessionId = sessionId,
                    messageId = "msg_assistant",
                    text = "Hi there",
                    start = 2_000,
                    end = 5_000,
                ),
            ),
        )
        repository.emit(repository.messageUpdated(completed))
        repository.emit(repository.sessionIdle(sessionId))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isStreaming)
        assertFalse(viewModel.state.value.messages.last().isStreaming)
        assertEquals("Hi there", (viewModel.state.value.messages.last().parts.single() as ChatPartUi.Text).text)
        assertEquals(15L, viewModel.state.value.reportedUsage?.reportedTokens)
    }

    @Test
    fun `stop aborts the active turn`() = runTest {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(sessionId, null, repository, viewModelScope)
        advanceUntilIdle()

        viewModel.onPromptChanged("stop me")
        viewModel.sendPrompt()
        advanceUntilIdle()
        viewModel.stop()
        advanceUntilIdle()

        assertEquals(listOf(sessionId), repository.abortCalls)
        assertFalse(viewModel.state.value.isStreaming)
        assertFalse(viewModel.state.value.isStopping)
    }

    @Test
    fun `permission requests can be answered`() = runTest {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(sessionId, null, repository, viewModelScope)
        advanceUntilIdle()

        repository.emit(
            repository.permissionAsked(
                PermissionRequest(
                    id = "per_1",
                    sessionID = sessionId,
                    permission = "edit",
                    patterns = listOf("src/**"),
                    metadata = JsonObject(emptyMap()),
                    always = emptyList(),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("per_1", viewModel.state.value.permission?.id)
        assertEquals("edit", viewModel.state.value.permission?.permission)

        viewModel.answerPermission(PermissionDecision.ALLOW_ONCE)
        advanceUntilIdle()

        assertNull(viewModel.state.value.permission)
        assertEquals(
            Triple(sessionId, "per_1", PermissionDecision.ALLOW_ONCE),
            repository.replyCalls.single(),
        )
    }

    @Test
    fun `session errors are surfaced`() = runTest {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(sessionId, null, repository, viewModelScope)
        advanceUntilIdle()

        repository.emit(repository.sessionError(sessionId, "provider exploded"))
        advanceUntilIdle()

        assertEquals("provider exploded", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isStreaming)
    }

    @Test
    fun `server connected triggers a transcript resync`() = runTest {
        val repository = FakeChatRepository().apply {
            transcripts[sessionId] = mutableListOf(userMessage())
        }
        val viewModel = ChatViewModel(sessionId, null, repository, viewModelScope)
        advanceUntilIdle()
        assertEquals(1, repository.listMessagesCalls)

        repository.emit(
            ServerEvent(
                type = "server.connected",
                id = "evt_connected",
            ),
        )
        advanceUntilIdle()

        assertEquals(2, repository.listMessagesCalls)
        assertTrue(viewModel.state.value.isConnected)
        assertEquals(1, viewModel.state.value.messages.size)
    }

    @Test
    fun `session updates change the title`() = runTest {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(sessionId, "Old title", repository, viewModelScope)
        advanceUntilIdle()

        repository.emit(
            repository.sessionUpdated(
                FakeChatRepository.session(sessionId, "Renamed", updated = 2_000),
            ),
        )
        advanceUntilIdle()

        assertEquals("Renamed", viewModel.state.value.title)
    }
}
