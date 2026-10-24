package io.github.spectordev9.opencode.testing

import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.MessageInfo
import io.github.spectordev9.opencode.core.model.Part
import io.github.spectordev9.opencode.core.model.PermissionRequest
import io.github.spectordev9.opencode.core.model.Session
import io.github.spectordev9.opencode.core.model.SessionTime
import io.github.spectordev9.opencode.ui.port.ChatRepository
import io.github.spectordev9.opencode.ui.port.PermissionDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FakeChatRepository : ChatRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val eventFlow = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 128)

    var sessions: MutableList<Session> = mutableListOf()
    var transcripts: MutableMap<String, MutableList<Message>> = mutableMapOf()
    var listSessionsResult: ApiResult<List<Session>>? = null
    var listMessagesResult: ((String) -> ApiResult<List<Message>>)? = null
    var createSessionResult: ((String) -> ApiResult<Session>)? = null
    var promptResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var abortResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var replyResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var listSessionsCalls = 0
        private set
    var listMessagesCalls = 0
        private set
    var createSessionCalls = mutableListOf<String>()
        private set
    var promptCalls = mutableListOf<Pair<String, String>>()
        private set
    var abortCalls = mutableListOf<String>()
        private set
    var replyCalls = mutableListOf<Triple<String, String, PermissionDecision>>()
        private set

    override fun events(): Flow<ServerEvent> = eventFlow

    override suspend fun listSessions(): ApiResult<List<Session>> {
        listSessionsCalls += 1
        return listSessionsResult ?: ApiResult.Success(sessions.toList())
    }

    override suspend fun createSession(title: String): ApiResult<Session> {
        createSessionCalls += title
        return createSessionResult?.invoke(title) ?: ApiResult.Success(
            session(id = "ses_created_${createSessionCalls.size}", title = title),
        )
    }

    override suspend fun listMessages(sessionId: String): ApiResult<List<Message>> {
        listMessagesCalls += 1
        listMessagesResult?.let { return it(sessionId) }
        return ApiResult.Success(transcripts[sessionId].orEmpty().toList())
    }

    override suspend fun prompt(sessionId: String, text: String): ApiResult<Unit> {
        promptCalls += sessionId to text
        return promptResult
    }

    override suspend fun abort(sessionId: String): ApiResult<Unit> {
        abortCalls += sessionId
        return abortResult
    }

    override suspend fun replyPermission(
        sessionId: String,
        permissionId: String,
        decision: PermissionDecision,
    ): ApiResult<Unit> {
        replyCalls += Triple(sessionId, permissionId, decision)
        return replyResult
    }

    suspend fun emit(event: ServerEvent) {
        eventFlow.emit(event)
    }

    fun messageUpdated(info: MessageInfo): ServerEvent = ServerEvent(
        type = "message.updated",
        id = "evt_msg_${info.id}_${info.time}",
        payload = mapOf(
            "sessionID" to info.sessionID,
            "info" to json.encodeToJsonElement(MessageInfo.serializer(), info),
        ),
    )

    fun partUpdated(part: Part): ServerEvent = ServerEvent(
        type = "message.part.updated",
        id = "evt_part_${part.id}_${part.time}",
        payload = mapOf(
            "sessionID" to part.sessionID,
            "part" to json.encodeToJsonElement(Part.serializer(), part),
        ),
    )

    fun partDelta(
        sessionId: String,
        messageId: String,
        partId: String,
        delta: String,
        id: String = "evt_delta_${messageId}_${partId}_$delta",
    ): ServerEvent = ServerEvent(
        type = "message.part.delta",
        id = id,
        payload = mapOf(
            "sessionID" to sessionId,
            "messageID" to messageId,
            "partID" to partId,
            "field" to "text",
            "delta" to delta,
        ),
    )

    fun sessionIdle(sessionId: String): ServerEvent = ServerEvent(
        type = "session.idle",
        id = "evt_idle_$sessionId",
        payload = mapOf("sessionID" to sessionId),
    )

    fun sessionError(sessionId: String, message: String): ServerEvent = ServerEvent(
        type = "session.error",
        id = "evt_error_$sessionId",
        payload = mapOf(
            "sessionID" to sessionId,
            "error" to mapOf(
                "name" to "ProviderError",
                "data" to mapOf("message" to message),
            ),
        ),
    )

    fun permissionAsked(request: PermissionRequest): ServerEvent = ServerEvent(
        type = "permission.asked",
        id = "evt_permission_${request.id}",
        payload = buildMap {
            put("sessionID", request.sessionID)
            put("id", request.id)
            put("permission", request.permission)
            put("patterns", request.patterns)
            put("metadata", request.metadata)
            put("always", request.always)
            request.tool?.let { put("tool", it) }
        },
    )

    fun sessionUpdated(session: Session): ServerEvent = ServerEvent(
        type = "session.updated",
        id = "evt_session_${session.id}_${session.time.updated}",
        payload = mapOf(
            "sessionID" to session.id,
            "info" to json.encodeToJsonElement(Session.serializer(), session),
        ),
    )

    companion object {
        fun session(
            id: String,
            title: String = "Session",
            agent: String? = "build",
            updated: Long = 1_000L,
            archived: Long? = null,
        ): Session = Session(
            id = id,
            slug = title.lowercase().replace(" ", "-"),
            projectID = "prj_test",
            directory = "/workspace",
            title = title,
            version = "1.19.3",
            time = SessionTime(created = updated - 100, updated = updated, archived = archived),
            agent = agent,
        )

        fun info(
            id: String,
            sessionId: String,
            role: String,
            created: Long = 1_000L,
            completed: Long? = null,
            agent: String? = "build",
            modelId: String? = "model",
            providerId: String? = "provider",
            cost: Double? = null,
            tokens: JsonObject? = null,
        ): MessageInfo = MessageInfo(
            id = id,
            sessionID = sessionId,
            role = role,
            time = buildJsonObject {
                put("created", created)
                if (completed != null) put("completed", completed)
            },
            agent = agent,
            modelID = modelId,
            providerID = providerId,
            cost = cost,
            tokens = tokens,
        )

        fun textPart(
            id: String,
            sessionId: String,
            messageId: String,
            text: String,
            start: Long = 1_000L,
            end: Long? = null,
        ): Part = Part(
            id = id,
            sessionID = sessionId,
            messageID = messageId,
            type = "text",
            text = text,
            time = buildJsonObject {
                put("start", start)
                if (end != null) put("end", end)
            },
        )

        fun toolPart(
            id: String,
            sessionId: String,
            messageId: String,
            tool: String,
            status: String,
            title: String? = null,
            output: String? = null,
            start: Long = 1_000L,
            end: Long? = null,
        ): Part = Part(
            id = id,
            sessionID = sessionId,
            messageID = messageId,
            type = "tool",
            tool = tool,
            state = buildJsonObject {
                put("status", status)
                if (title != null) put("title", title)
                if (output != null) put("output", output)
            },
            time = buildJsonObject {
                put("start", start)
                if (end != null) put("end", end)
            },
        )
    }
}
