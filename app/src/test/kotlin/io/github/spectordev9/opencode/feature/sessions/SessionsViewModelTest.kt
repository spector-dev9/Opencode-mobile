package io.github.spectordev9.opencode.feature.sessions

import io.github.spectordev9.opencode.core.common.ApiError
import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.common.ErrorCode
import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.testing.FakeChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionsViewModelTest {
    private val viewModelScope = CoroutineScope(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        viewModelScope.cancel()
    }

    @Test
    fun `loads sessions and filters by title`() = runTest {
        val repository = FakeChatRepository().apply {
            sessions = mutableListOf(
                FakeChatRepository.session("ses_a", "Alpha task"),
                FakeChatRepository.session("ses_b", "Beta task"),
            )
        }
        val viewModel = SessionsViewModel(repository, viewModelScope)

        advanceUntilIdle()

        assertEquals(listOf("ses_a", "ses_b"), viewModel.state.value.sessions.map { it.id })
        assertFalse(viewModel.state.value.isLoading)

        viewModel.onQueryChange("beta")

        assertTrue(viewModel.state.value.isSearching)
        assertEquals(listOf("ses_b"), viewModel.state.value.visibleSessions.map { it.id })
    }

    @Test
    fun `creates a session and exposes it for navigation`() = runTest {
        val repository = FakeChatRepository()
        val viewModel = SessionsViewModel(repository, viewModelScope)
        advanceUntilIdle()

        viewModel.showCreateDialog()
        viewModel.createSession("Gamma task")
        advanceUntilIdle()

        assertEquals(listOf("Gamma task"), repository.createSessionCalls)
        assertEquals("ses_created_1", viewModel.state.value.openSessionId)
        assertFalse(viewModel.state.value.showCreateDialog)
        assertEquals(listOf("Gamma task"), viewModel.state.value.sessions.map { it.title })

        viewModel.consumeOpenSession()
        assertNull(viewModel.state.value.openSessionId)
    }

    @Test
    fun `applies created updated and deleted session events`() = runTest {
        val repository = FakeChatRepository()
        val viewModel = SessionsViewModel(repository, viewModelScope)
        advanceUntilIdle()

        repository.emit(
            repository.sessionUpdated(
                FakeChatRepository.session("ses_new", "New session", updated = 2_000),
            ),
        )
        advanceUntilIdle()
        assertEquals(listOf("ses_new"), viewModel.state.value.sessions.map { it.id })

        repository.emit(
            ServerEvent(
                type = "session.deleted",
                id = "evt_delete",
                payload = mapOf("sessionID" to "ses_new"),
            ),
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sessions.isEmpty())
    }

    @Test
    fun `reports load failures without clearing the spinner forever`() = runTest {
        val repository = FakeChatRepository().apply {
            listSessionsResult = ApiResult.Failure(
                ApiError(ErrorCode.E_RUNTIME_DOWN),
            )
        }
        val viewModel = SessionsViewModel(repository, viewModelScope)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.error.orEmpty().contains("not running"))
    }

    @Test
    fun `keeps the create dialog open after a failure`() = runTest {
        val repository = FakeChatRepository().apply {
            createSessionResult = {
                ApiResult.Failure(ApiError(ErrorCode.E_AUTH))
            }
        }
        val viewModel = SessionsViewModel(repository, viewModelScope)
        advanceUntilIdle()

        viewModel.showCreateDialog()
        viewModel.createSession("Will fail")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showCreateDialog)
        assertFalse(viewModel.state.value.isCreating)
        assertTrue(viewModel.state.value.error.orEmpty().contains("Authentication"))
    }
}
