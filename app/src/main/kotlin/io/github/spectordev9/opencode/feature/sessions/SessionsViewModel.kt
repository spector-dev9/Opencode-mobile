package io.github.spectordev9.opencode.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Session
import io.github.spectordev9.opencode.ui.port.ChatRepository
import io.github.spectordev9.opencode.ui.util.stringOrNull
import io.github.spectordev9.opencode.ui.util.toJsonElement
import io.github.spectordev9.opencode.ui.util.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val isCreating: Boolean = false,
    val openSessionId: String? = null,
) {
    val visibleSessions: List<Session>
        get() {
            val filtered = sessions.filter { it.time.archived == null }
            if (query.isBlank()) return filtered
            val needle = query.trim()
            return filtered.filter { session ->
                session.title.contains(needle, ignoreCase = true) ||
                    session.slug.contains(needle, ignoreCase = true) ||
                    session.agent?.contains(needle, ignoreCase = true) == true
            }
        }

    val isSearching: Boolean
        get() = query.isNotBlank()
}

class SessionsViewModel(
    private val repository: ChatRepository,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val mutableState = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = mutableState.asStateFlow()

    init {
        refresh()
        observeEvents()
    }

    fun refresh() {
        scope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.listSessions()) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        sessions = result.value.sortedByDescending { session -> session.time.updated },
                    )
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, error = result.error.toUserMessage())
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        mutableState.update { it.copy(query = value) }
    }

    fun showCreateDialog() {
        mutableState.update { it.copy(showCreateDialog = true) }
    }

    fun dismissCreateDialog() {
        if (mutableState.value.isCreating) return
        mutableState.update { it.copy(showCreateDialog = false) }
    }

    fun createSession(title: String) {
        val current = mutableState.value
        if (current.isCreating) return
        mutableState.update { it.copy(isCreating = true, error = null) }
        scope.launch {
            when (val result = repository.createSession(title)) {
                is ApiResult.Success -> mutableState.update { state ->
                    state.copy(
                        isCreating = false,
                        showCreateDialog = false,
                        sessions = upsert(state.sessions, result.value),
                        openSessionId = result.value.id,
                    )
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(isCreating = false, error = result.error.toUserMessage())
                }
            }
        }
    }

    fun consumeOpenSession() {
        mutableState.update { it.copy(openSessionId = null) }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun observeEvents() {
        scope.launch {
            repository.events()
                .catch { error ->
                    mutableState.update {
                        it.copy(error = error.message ?: "The session stream closed unexpectedly.")
                    }
                }
                .collect(::onEvent)
        }
    }

    private fun onEvent(event: ServerEvent) {
        when (event.type) {
            "session.created", "session.updated" -> {
                val session = event.sessionInfo() ?: return
                mutableState.update { it.copy(sessions = upsert(it.sessions, session)) }
            }
            "session.deleted" -> {
                val id = event.payload.stringOrNull("sessionID")
                    ?: (event.payload["info"]?.toJsonElement() as? JsonObject)?.stringOrNull("id")
                    ?: return
                mutableState.update { state ->
                    state.copy(sessions = state.sessions.filterNot { it.id == id })
                }
            }
        }
    }

    private fun ServerEvent.sessionInfo(): Session? {
        val info = payload["info"]?.toJsonElement() ?: return null
        return try {
            json.decodeFromJsonElement(Session.serializer(), info)
        } catch (_: Exception) {
            null
        }
    }

    private fun upsert(sessions: List<Session>, session: Session): List<Session> {
        val merged = sessions.filterNot { it.id == session.id } + session
        return merged
            .filter { it.time.archived == null }
            .sortedByDescending { it.time.updated }
    }
}
