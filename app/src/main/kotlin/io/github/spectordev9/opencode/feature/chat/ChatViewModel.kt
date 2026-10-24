package io.github.spectordev9.opencode.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.PermissionRequest
import io.github.spectordev9.opencode.core.model.Session
import io.github.spectordev9.opencode.ui.port.ChatRepository
import io.github.spectordev9.opencode.ui.port.PermissionDecision
import io.github.spectordev9.opencode.ui.util.stringOrNull
import io.github.spectordev9.opencode.ui.util.toJsonElement
import io.github.spectordev9.opencode.ui.util.toJsonObject
import io.github.spectordev9.opencode.ui.util.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class ChatViewModel(
    private val sessionId: String,
    initialTitle: String?,
    private val repository: ChatRepository,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val transcript = TranscriptStore(sessionId)
    private val mutableState = MutableStateFlow(
        ChatUiState(
            sessionId = sessionId,
            title = initialTitle?.takeIf { it.isNotBlank() },
        ),
    )
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

    private var resyncJob: Job? = null
    private var resyncGeneration = 0

    init {
        reload()
        observeEvents()
    }

    fun refresh() {
        reload()
    }

    fun onPromptChanged(value: String) {
        mutableState.update { it.copy(prompt = value) }
    }

    fun sendPrompt() {
        val current = mutableState.value
        val text = current.prompt.trim()
        if (text.isEmpty() || current.isStreaming || current.isLoading) return
        mutableState.update {
            it.copy(
                prompt = "",
                isStreaming = true,
                isStopping = false,
                error = null,
                permission = null,
            )
        }
        scope.launch {
            when (val result = repository.prompt(sessionId, text)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> mutableState.update {
                    it.copy(
                        isStreaming = false,
                        error = result.error.toUserMessage(),
                        prompt = if (it.prompt.isBlank()) text else it.prompt,
                    )
                }
            }
        }
    }

    fun stop() {
        val current = mutableState.value
        if (!current.isStreaming || current.isStopping) return
        mutableState.update { it.copy(isStopping = true) }
        scope.launch {
            when (val result = repository.abort(sessionId)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(isStreaming = false, isStopping = false, permission = null)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(
                        isStopping = false,
                        error = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun answerPermission(decision: PermissionDecision) {
        val permission = mutableState.value.permission ?: return
        if (permission.busy) return
        mutableState.update { it.copy(permission = permission.copy(busy = true)) }
        scope.launch {
            when (
                val result = repository.replyPermission(
                    sessionId = sessionId,
                    permissionId = permission.id,
                    decision = decision,
                )
            ) {
                is ApiResult.Success -> mutableState.update { it.copy(permission = null) }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(
                        permission = permission.copy(busy = false),
                        error = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun reload() {
        scope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.listMessages(sessionId)) {
                is ApiResult.Success -> {
                    transcript.replace(result.value)
                    mutableState.update { it.copy(isLoading = false) }
                    publishMessages()
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        error = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    private fun observeEvents() {
        scope.launch {
            repository.events()
                .catch { error ->
                    mutableState.update {
                        it.copy(
                            isConnected = false,
                            error = error.message ?: "The OpenCode event stream closed.",
                        )
                    }
                }
                .collect(::onEvent)
        }
    }

    private fun onEvent(event: ServerEvent) {
        val outcome = transcript.receive(event)
        when (outcome) {
            ReceiveOutcome.RESYNC -> {
                mutableState.update { it.copy(isConnected = true) }
                startResync()
            }
            ReceiveOutcome.BUFFERED,
            ReceiveOutcome.DUPLICATE,
            ReceiveOutcome.IGNORED,
            -> Unit
            ReceiveOutcome.APPLIED -> {
                handleSemanticEvent(event)
                publishMessages()
            }
        }
    }

    private fun startResync() {
        resyncJob?.cancel()
        val generation = ++resyncGeneration
        resyncJob = scope.launch {
            val result = repository.listMessages(sessionId)
            if (generation != resyncGeneration) return@launch
            when (result) {
                is ApiResult.Success -> {
                    transcript.completeResync(result.value)
                    mutableState.update {
                        it.copy(
                            isConnected = true,
                            isLoading = false,
                            error = null,
                        )
                    }
                    publishMessages()
                }
                is ApiResult.Failure -> {
                    transcript.completeResync(null)
                    mutableState.update {
                        it.copy(
                            isConnected = false,
                            isLoading = false,
                            error = result.error.toUserMessage(),
                        )
                    }
                    publishMessages()
                }
            }
        }
    }

    private fun handleSemanticEvent(event: ServerEvent) {
        when (event.type) {
            "session.idle" -> {
                mutableState.update {
                    it.copy(isStreaming = false, isStopping = false)
                }
            }
            "session.error" -> {
                mutableState.update {
                    it.copy(
                        isStreaming = false,
                        isStopping = false,
                        error = event.errorMessage(),
                    )
                }
            }
            "session.updated" -> {
                val session = event.sessionInfo() ?: return
                if (session.id == sessionId) {
                    mutableState.update { it.copy(title = session.title) }
                }
            }
            "permission.asked" -> {
                val permission = event.permissionRequest() ?: return
                if (permission.sessionID == sessionId) {
                    mutableState.update {
                        it.copy(
                            permission = PermissionUi(
                                id = permission.id,
                                sessionId = permission.sessionID,
                                permission = permission.permission,
                                patterns = permission.patterns,
                                always = permission.always,
                            ),
                        )
                    }
                }
            }
            "permission.replied" -> {
                val permissionId = event.payload.stringOrNull("permissionID")
                    ?: (event.payload["permission"]?.toJsonElement() as? JsonObject)?.stringOrNull("id")
                if (permissionId != null) {
                    mutableState.update { state ->
                        if (state.permission?.id == permissionId) state.copy(permission = null) else state
                    }
                }
            }
        }
    }

    private fun publishMessages() {
        val messages = transcript.snapshot()
        mutableState.update {
            it.copy(
                messages = messages.map { message -> message.toChatMessageUi() },
                reportedUsage = messages.latestReportedUsage(),
            )
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

    private fun ServerEvent.permissionRequest(): PermissionRequest? = try {
        json.decodeFromJsonElement(
            PermissionRequest.serializer(),
            payload.toJsonObject(),
        )
    } catch (_: Exception) {
        null
    }

    private fun ServerEvent.errorMessage(): String {
        val error = payload["error"]?.toJsonElement() as? JsonObject
        val name = error?.stringOrNull("name")
        val data = error?.get("data")?.toJsonElement() as? JsonObject
        val message = data?.stringOrNull("message") ?: error?.stringOrNull("message")
        return message ?: name?.let { "OpenCode reported $it" } ?: "OpenCode reported an error."
    }
}
