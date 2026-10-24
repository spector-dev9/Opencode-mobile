package io.github.spectordev9.opencode.ui.port

import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.Session
import kotlinx.coroutines.flow.Flow

enum class PermissionDecision {
    ALLOW_ONCE,
    ALLOW_ALWAYS,
    REJECT,
}

/**
 * Use-case port shared by the Compose chat screens and the runtime service
 * layer. The production implementation must wrap the authenticated
 * OpenCode client and the existing bridge commands. It must not add a
 * second transport. See docs/parallel-contract.md.
 */
interface ChatRepository {
    fun events(): Flow<ServerEvent>

    suspend fun listSessions(): ApiResult<List<Session>>

    suspend fun createSession(title: String): ApiResult<Session>

    suspend fun listMessages(sessionId: String): ApiResult<List<Message>>

    suspend fun prompt(sessionId: String, text: String): ApiResult<Unit>

    suspend fun abort(sessionId: String): ApiResult<Unit>

    suspend fun replyPermission(
        sessionId: String,
        permissionId: String,
        decision: PermissionDecision,
    ): ApiResult<Unit>
}
