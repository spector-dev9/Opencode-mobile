package io.github.spectordev9.opencode.data

import io.github.spectordev9.opencode.core.common.ApiError
import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.common.ErrorCode
import io.github.spectordev9.opencode.core.common.TrashId
import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.Session
import io.github.spectordev9.opencode.core.workspace.Chunk
import io.github.spectordev9.opencode.core.workspace.Entry
import io.github.spectordev9.opencode.core.workspace.Precondition
import io.github.spectordev9.opencode.core.workspace.WriteReceipt
import io.github.spectordev9.opencode.core.workspace.WorkspaceService
import io.github.spectordev9.opencode.ui.port.ChatRepository
import io.github.spectordev9.opencode.ui.port.PermissionDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private val runtimeDown = ApiError(
    code = ErrorCode.E_RUNTIME_DOWN,
    message = "The OpenCode runtime is not connected in this build.",
)

open class UnavailableChatRepository : ChatRepository {
    override fun events(): Flow<ServerEvent> = emptyFlow()

    override suspend fun listSessions(): ApiResult<List<Session>> = ApiResult.Failure(runtimeDown)

    override suspend fun createSession(title: String): ApiResult<Session> = ApiResult.Failure(runtimeDown)

    override suspend fun listMessages(sessionId: String): ApiResult<List<Message>> = ApiResult.Failure(runtimeDown)

    override suspend fun prompt(sessionId: String, text: String): ApiResult<Unit> = ApiResult.Failure(runtimeDown)

    override suspend fun abort(sessionId: String): ApiResult<Unit> = ApiResult.Failure(runtimeDown)

    override suspend fun replyPermission(
        sessionId: String,
        permissionId: String,
        decision: PermissionDecision,
    ): ApiResult<Unit> = ApiResult.Failure(runtimeDown)
}

open class UnavailableWorkspaceService : WorkspaceService {
    private fun <T> failure(): ApiResult<T> = ApiResult.Failure(runtimeDown)

    override suspend fun list(path: String): ApiResult<List<Entry>> = failure()

    override suspend fun stat(path: String): ApiResult<Entry> = failure()

    override suspend fun readRange(path: String, offset: Long, length: Int): ApiResult<Chunk> = failure()

    override suspend fun write(path: String, data: ByteArray, expect: Precondition): ApiResult<WriteReceipt> = failure()

    override suspend fun mkdir(path: String): ApiResult<Entry> = failure()

    override suspend fun rename(from: String, to: String): ApiResult<Entry> = failure()

    override suspend fun move(from: String, to: String): ApiResult<Entry> = failure()

    override suspend fun trash(path: String): ApiResult<TrashId> = failure()

    override suspend fun restore(id: TrashId): ApiResult<Entry> = failure()

    override suspend fun search(path: String, query: String, limit: Int): ApiResult<List<Entry>> = failure()
}
