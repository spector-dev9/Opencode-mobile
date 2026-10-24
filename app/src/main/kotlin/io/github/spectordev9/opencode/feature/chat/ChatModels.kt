package io.github.spectordev9.opencode.feature.chat

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
    UNKNOWN,
}

enum class ToolRunStatus {
    RUNNING,
    COMPLETED,
    ERROR,
    UNKNOWN,
}

sealed interface ChatPartUi {
    val id: String

    data class Text(
        override val id: String,
        val text: String,
    ) : ChatPartUi

    data class Reasoning(
        override val id: String,
        val text: String,
    ) : ChatPartUi

    data class Tool(
        override val id: String,
        val name: String,
        val status: ToolRunStatus,
        val title: String?,
        val detail: String?,
    ) : ChatPartUi

    data class FileAttachment(
        override val id: String,
        val filename: String,
        val mime: String?,
    ) : ChatPartUi

    data class Other(
        override val id: String,
        val type: String,
    ) : ChatPartUi
}

data class TokenUsage(
    val input: Long?,
    val output: Long?,
    val reasoning: Long?,
    val cacheRead: Long?,
    val cacheWrite: Long?,
) {
    val total: Long?
        get() {
            val values = listOfNotNull(input, output, reasoning, cacheRead, cacheWrite)
            return if (values.isEmpty()) null else values.sum()
        }
}

data class ChatMessageUi(
    val id: String,
    val role: ChatRole,
    val parts: List<ChatPartUi>,
    val createdAtMillis: Long?,
    val completedAtMillis: Long?,
    val cost: Double?,
    val tokens: TokenUsage?,
    val agent: String?,
    val modelLabel: String?,
    val isStreaming: Boolean,
)

data class ReportedUsage(
    val reportedTokens: Long?,
    val cost: Double?,
    val modelLabel: String?,
)

data class PermissionUi(
    val id: String,
    val sessionId: String,
    val permission: String,
    val patterns: List<String>,
    val always: List<String>,
    val busy: Boolean = false,
)

data class ChatUiState(
    val sessionId: String,
    val title: String?,
    val messages: List<ChatMessageUi> = emptyList(),
    val isLoading: Boolean = true,
    val isStreaming: Boolean = false,
    val isStopping: Boolean = false,
    val isConnected: Boolean = false,
    val prompt: String = "",
    val error: String? = null,
    val permission: PermissionUi? = null,
    val reportedUsage: ReportedUsage? = null,
)
