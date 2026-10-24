package io.github.spectordev9.opencode.feature.chat

import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.MessageInfo
import io.github.spectordev9.opencode.core.model.Part
import io.github.spectordev9.opencode.ui.util.longFrom
import io.github.spectordev9.opencode.ui.util.longOrNull
import io.github.spectordev9.opencode.ui.util.objectOrNull
import io.github.spectordev9.opencode.ui.util.stringOrNull

fun Message.toChatMessageUi(): ChatMessageUi {
    val role = when (info.role.lowercase()) {
        "user" -> ChatRole.USER
        "assistant" -> ChatRole.ASSISTANT
        "system" -> ChatRole.SYSTEM
        else -> ChatRole.UNKNOWN
    }
    val createdAt = info.time.longFrom("created", "start")
    val completedAt = info.time.longFrom("completed", "end")
    return ChatMessageUi(
        id = info.id,
        role = role,
        parts = parts.mapNotNull(Part::toChatPartUi),
        createdAtMillis = createdAt,
        completedAtMillis = completedAt,
        cost = info.cost,
        tokens = info.tokens?.toTokenUsage(),
        agent = info.agent,
        modelLabel = info.modelLabel(),
        isStreaming = role == ChatRole.ASSISTANT && completedAt == null,
    )
}

fun List<Message>.latestReportedUsage(): ReportedUsage? {
    val message = asReversed().firstOrNull { it.info.role.equals("assistant", ignoreCase = true) }
        ?: return null
    val tokens = message.info.tokens?.toTokenUsage()?.total
    val cost = message.info.cost
    if (tokens == null && cost == null) return null
    return ReportedUsage(
        reportedTokens = tokens,
        cost = cost,
        modelLabel = message.info.modelLabel(),
    )
}

fun MessageInfo.modelLabel(): String? = when {
    providerID != null && modelID != null -> "$providerID/$modelID"
    modelID != null -> modelID
    else -> null
}

private fun Part.toChatPartUi(): ChatPartUi? {
    if (synthetic == true || ignored == true) return null
    return when (type) {
        "text" -> text?.takeIf { it.isNotEmpty() }?.let { ChatPartUi.Text(id = id, text = it) }
        "reasoning" -> text?.takeIf { it.isNotEmpty() }?.let { ChatPartUi.Reasoning(id = id, text = it) }
        "tool" -> {
            val state = state
            val status = when (state?.stringOrNull("status")?.lowercase()) {
                "running", "pending" -> ToolRunStatus.RUNNING
                "completed" -> ToolRunStatus.COMPLETED
                "error", "failed" -> ToolRunStatus.ERROR
                else -> ToolRunStatus.UNKNOWN
            }
            val title = state?.stringOrNull("title")
            val detail = state?.stringOrNull("output")
                ?: state?.objectOrNull("metadata")?.stringOrNull("detail")
                ?: state?.objectOrNull("error")?.stringOrNull("message")
                ?: state?.objectOrNull("input")?.toString()?.take(240)
            ChatPartUi.Tool(
                id = id,
                name = tool ?: "tool",
                status = status,
                title = title,
                detail = detail,
            )
        }
        "file" -> ChatPartUi.FileAttachment(
            id = id,
            filename = filename ?: "Attachment",
            mime = mime,
        )
        "step-start", "step-finish", "snapshot", "patch" -> null
        else -> ChatPartUi.Other(id = id, type = type)
    }
}

private fun kotlinx.serialization.json.JsonObject.toTokenUsage(): TokenUsage {
    val cache = objectOrNull("cache")
    return TokenUsage(
        input = longOrNull("input"),
        output = longOrNull("output"),
        reasoning = longOrNull("reasoning"),
        cacheRead = cache?.longOrNull("read") ?: longOrNull("cacheRead"),
        cacheWrite = cache?.longOrNull("write") ?: longOrNull("cacheWrite"),
    )
}
