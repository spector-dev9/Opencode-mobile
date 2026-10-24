package io.github.spectordev9.opencode.data

import io.github.spectordev9.opencode.core.common.ApiError
import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.common.ErrorCode
import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.MessageInfo
import io.github.spectordev9.opencode.core.model.Part
import io.github.spectordev9.opencode.core.model.Session
import io.github.spectordev9.opencode.core.model.SessionTime
import io.github.spectordev9.opencode.ui.port.ChatRepository
import io.github.spectordev9.opencode.ui.port.PermissionDecision
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only demo source. It is never selected in a release build and is
 * clearly labelled in the UI. It exists to exercise streaming, resync
 * buffering, abort, and the presentation states without a runtime.
 */
class DemoChatRepository(
    private val clock: () -> Long = System::currentTimeMillis,
) : ChatRepository {
    private val lock = Any()
    private val eventsFlow = MutableSharedFlow<ServerEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val eventCounter = AtomicLong(0)
    private val abortRequested = AtomicBoolean(false)
    private val sessions = LinkedHashMap<String, Session>()
    private val messages = LinkedHashMap<String, MutableList<Message>>()

    init {
        val now = clock()
        DemoData.sessions(now).forEach { session ->
            sessions[session.id] = session
            messages[session.id] = DemoData.messages(session.id, now).toMutableList()
        }
    }

    override fun events(): Flow<ServerEvent> = flow {
        emit(connectedEvent())
        emitAll(eventsFlow)
    }

    override suspend fun listSessions(): ApiResult<List<Session>> = synchronized(lock) {
        ApiResult.Success(
            sessions.values
                .filter { it.time.archived == null }
                .sortedByDescending { it.time.updated },
        )
    }

    override suspend fun createSession(title: String): ApiResult<Session> {
        val cleanTitle = title.trim().ifEmpty { "New session" }
        val now = clock()
        val session = Session(
            id = "ses_demo_${UUID.randomUUID().toString().take(8)}",
            slug = cleanTitle.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
            projectID = "prj_demo",
            directory = DemoData.WORKSPACE,
            title = cleanTitle,
            version = "1.19.3",
            time = SessionTime(created = now, updated = now),
            agent = "build",
        )
        synchronized(lock) {
            sessions[session.id] = session
            messages[session.id] = mutableListOf()
        }
        emitEvent("session.created", DemoData.sessionInfoPayload(session))
        return ApiResult.Success(session)
    }

    override suspend fun listMessages(sessionId: String): ApiResult<List<Message>> = synchronized(lock) {
        val transcript = messages[sessionId]
            ?: return ApiResult.Failure(
                ApiError(ErrorCode.E_PATH_DENIED, "Unknown demo session: $sessionId"),
            )
        ApiResult.Success(transcript.toList())
    }

    override suspend fun prompt(sessionId: String, text: String): ApiResult<Unit> {
        val prompt = text.trim()
        if (prompt.isEmpty()) {
            return ApiResult.Failure(ApiError(ErrorCode.E_UPSTREAM, "Prompt must not be empty"))
        }
        val session = synchronized(lock) { sessions[sessionId] }
            ?: return ApiResult.Failure(
                ApiError(ErrorCode.E_PATH_DENIED, "Unknown demo session: $sessionId"),
            )

        abortRequested.set(false)
        val start = clock()
        val userId = "msg_demo_user_${UUID.randomUUID().toString().take(6)}"
        val assistantId = "msg_demo_assistant_${UUID.randomUUID().toString().take(6)}"
        val userPartId = "prt_demo_user_${UUID.randomUUID().toString().take(6)}"
        val assistantPartId = "prt_demo_assistant_${UUID.randomUUID().toString().take(6)}"

        val userInfo = MessageInfo(
            id = userId,
            sessionID = sessionId,
            role = "user",
            time = messageTime(start, start + 1_000),
            agent = session.agent,
            modelID = "deepseek-v4.1-flash-free",
            providerID = "opencode",
        )
        val userPart = textPart(userPartId, sessionId, userId, prompt, start, start + 1_000)
        emitEvent("message.updated", DemoData.messageInfoPayload(userInfo))
        emitEvent("message.part.updated", DemoData.partPayload(userPart))

        val assistantInfo = MessageInfo(
            id = assistantId,
            sessionID = sessionId,
            role = "assistant",
            time = messageTime(start + 1_000),
            agent = "build",
            modelID = "deepseek-v4.1-flash-free",
            providerID = "opencode",
        )
        val emptyPart = Part(
            id = assistantPartId,
            sessionID = sessionId,
            messageID = assistantId,
            type = "text",
            text = "",
            time = partTime(start + 1_000),
        )
        emitEvent("message.updated", DemoData.messageInfoPayload(assistantInfo))
        emitEvent("message.part.updated", DemoData.partPayload(emptyPart))

        val response = demoResponse(prompt)
        val chunks = response.split(' ').mapIndexed { index, word ->
            if (index == 0) word else " $word"
        }
        val built = StringBuilder()
        var aborted = false
        for (chunk in chunks) {
            if (abortRequested.get()) {
                aborted = true
                break
            }
            built.append(chunk)
            emitEvent(
                "message.part.delta",
                mapOf(
                    "sessionID" to sessionId,
                    "messageID" to assistantId,
                    "partID" to assistantPartId,
                    "field" to "text",
                    "delta" to chunk,
                ),
            )
            delay(32)
        }

        val completed = clock()
        val completedPart = emptyPart.copy(
            text = built.toString(),
            time = partTime(start + 1_000, completed),
        )
        val completedInfo = assistantInfo.copy(
            time = messageTime(start + 1_000, completed),
            cost = 0.0,
            tokens = buildJsonObject {
                put("input", prompt.length / 4)
                put("output", built.length / 4)
            },
            finish = if (aborted) "aborted" else "stop",
        )
        emitEvent("message.part.updated", DemoData.partPayload(completedPart))
        emitEvent("message.updated", DemoData.messageInfoPayload(completedInfo))

        val updatedSession = session.copy(
            time = session.time.copy(updated = completed),
            cost = (session.cost ?: 0.0) + 0.0,
        )
        synchronized(lock) {
            sessions[sessionId] = updatedSession
            messages[sessionId]?.apply {
                add(Message(userInfo, listOf(userPart)))
                add(Message(completedInfo, listOf(completedPart)))
            }
        }
        emitEvent("session.updated", DemoData.sessionInfoPayload(updatedSession))
        emitEvent("session.idle", mapOf("sessionID" to sessionId))
        return ApiResult.Success(Unit)
    }

    override suspend fun abort(sessionId: String): ApiResult<Unit> {
        abortRequested.set(true)
        return ApiResult.Success(Unit)
    }

    override suspend fun replyPermission(
        sessionId: String,
        permissionId: String,
        decision: PermissionDecision,
    ): ApiResult<Unit> {
        emitEvent(
            "permission.replied",
            mapOf(
                "sessionID" to sessionId,
                "permissionID" to permissionId,
                "response" to when (decision) {
                    PermissionDecision.ALLOW_ONCE -> "once"
                    PermissionDecision.ALLOW_ALWAYS -> "always"
                    PermissionDecision.REJECT -> "reject"
                },
            ),
        )
        return ApiResult.Success(Unit)
    }

    private fun connectedEvent(): ServerEvent {
        val seq = eventCounter.incrementAndGet()
        return ServerEvent(
            type = "server.connected",
            directory = DemoData.WORKSPACE,
            id = "evt_demo_connected_$seq",
            payload = emptyMap(),
        )
    }

    private suspend fun emitEvent(type: String, payload: Map<String, Any?>) {
        val seq = eventCounter.incrementAndGet()
        eventsFlow.emit(
            ServerEvent(
                type = type,
                directory = DemoData.WORKSPACE,
                id = "evt_demo_$seq",
                payload = payload,
            ),
        )
    }

    private fun demoResponse(prompt: String): String {
        val quoted = prompt.lineSequence().firstOrNull()?.take(120).orEmpty()
        val topic = when {
            prompt.contains("test", ignoreCase = true) -> "a deterministic test around the state transition"
            prompt.contains("file", ignoreCase = true) -> "a guarded read through the workspace service"
            prompt.contains("error", ignoreCase = true) -> "the error path and its retry affordance"
            else -> "the current request"
        }
        return """
            This is a local demo response. No runtime or model was called.

            You asked: "$quoted"

            I would approach $topic by keeping presentation state separate from the service layer. The streaming path is:

            ```kotlin
            repository.events()
                .filter { it.sessionID == sessionId }
                .collect { transcript.receive(it) }
            ```

            The reducer buffers events after `server.connected`, replaces the transcript from the snapshot, and then replays the buffer. Sending the prompt and stopping the turn are separate service calls, so the UI stays responsive while text arrives.
        """.trimIndent()
    }

    private fun messageTime(created: Long, completed: Long? = null): JsonObject = buildJsonObject {
        put("created", created)
        if (completed != null) put("completed", completed)
    }

    private fun partTime(start: Long, end: Long? = null): JsonObject = buildJsonObject {
        put("start", start)
        if (end != null) put("end", end)
    }

    private fun textPart(
        id: String,
        sessionId: String,
        messageId: String,
        text: String,
        start: Long,
        end: Long? = null,
    ): Part = Part(
        id = id,
        sessionID = sessionId,
        messageID = messageId,
        type = "text",
        text = text,
        time = partTime(start, end),
    )
}
