package io.github.spectordev9.opencode.ui.util

import io.github.spectordev9.opencode.core.common.ApiError
import io.github.spectordev9.opencode.core.common.ErrorCode

fun ApiError.toUserMessage(): String = when (code) {
    ErrorCode.E_AUTH -> "Authentication failed. The runtime rejected this app's credentials."
    ErrorCode.E_RUNTIME_DOWN -> "OpenCode is not running. Start the runtime and try again."
    ErrorCode.E_TIMEOUT -> "The request timed out. The operation may still be running."
    ErrorCode.E_PATH_DENIED -> message ?: "That path is outside the workspace."
    ErrorCode.E_CONFLICT -> message ?: "The file changed before the operation completed."
    ErrorCode.E_TOO_LARGE -> "The file is too large to preview."
    ErrorCode.E_UNSUPPORTED_VERSION -> "This build does not support the runtime's protocol version."
    ErrorCode.E_UPSTREAM -> message ?: "OpenCode returned an unexpected response."
    ErrorCode.E_CANCELLED -> "The operation was cancelled."
}
