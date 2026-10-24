package io.github.spectordev9.opencode.feature.chat

import io.github.spectordev9.opencode.core.gateway.ServerEvent
import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.MessageInfo
import io.github.spectordev9.opencode.core.model.Part
import io.github.spectordev9.opencode.ui.util.longFrom
import io.github.spectordev9.opencode.ui.util.stringOrNull
import io.github.spectordev9.opencode.ui.util.toJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

enum class ReceiveOutcome {
    APPLIED,
    BUFFERED,
    RESYNC,
    DUPLICATE,
    IGNORED,
}

/**
 * Presentation-side transcript reducer. It applies OpenCode's SSE events to
 * a message/part model and coordinates the STR-5 resync sequence. Transport,
 * reconnect, and snapshot fetching stay in the repository.
 */
class TranscriptStore(
    private val sessionId: String,
    private val maxSeenEvents: Int = 4_096,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val messages = LinkedHashMap<String, StoredMessage>()
    private val seenEventIds = LinkedHashSet<String>()
    private val bufferedEvents = mutableListOf<ServerEvent>()
    private var buffering = false

    val isBuffering: Boolean
        get() = buffering

    fun replace(messages: List<Message>) {
        this.messages.clear()
        messages.forEach { message ->
            this.messages[message.info.id] = StoredMessage(
                info = message.info,
                parts = LinkedHashMap(message.parts.associateBy { it.id }),
            )
        }
    }

    fun receive(event: ServerEvent): ReceiveOutcome {
        val eventId = event.id
        if (eventId != null && !markSeen(eventId)) return ReceiveOutcome.DUPLICATE

        if (event.type == "server.connected") {
            buffering = true
            bufferedEvents.clear()
            return ReceiveOutcome.RESYNC
        }
        if (!belongsToSession(event)) return ReceiveOutcome.IGNORED
        if (buffering) {
            bufferedEvents += event
            return ReceiveOutcome.BUFFERED
        }
        apply(event)
        return ReceiveOutcome.APPLIED
    }

    /**
     * Completes a resync. A null snapshot means the snapshot request failed;
     * the existing transcript is kept and buffered events are replayed so
     * the stream can continue without an unbounded buffer.
     */
    fun completeResync(snapshot: List<Message>?) {
        if (snapshot != null) replace(snapshot)
        bufferedEvents.forEach(::apply)
        bufferedEvents.clear()
        buffering = false
    }

    fun snapshot(): List<Message> = messages.values.map { stored ->
        Message(
            info = stored.info,
            parts = stored.parts.values.toList(),
        )
    }

    fun message(id: String): MessageInfo? = messages[id]?.info

    private fun apply(event: ServerEvent) {
        when (event.type) {
            "message.updated" -> applyMessageUpdated(event)
            "message.part.updated" -> applyPartUpdated(event)
            "message.part.delta" -> applyPartDelta(event)
            "message.part.removed" -> applyPartRemoved(event)
            "message.removed" -> applyMessageRemoved(event)
        }
    }

    private fun applyMessageUpdated(event: ServerEvent) {
        val infoElement = event.payload["info"]?.toJsonElement() ?: return
        val info = try {
            json.decodeFromJsonElement(MessageInfo.serializer(), infoElement)
        } catch (_: Exception) {
            return
        }
        if (info.sessionID != sessionId) return
        val stored = messages.getOrPut(info.id) {
            StoredMessage(info = info, parts = LinkedHashMap())
        }
        stored.info = info
    }

    private fun applyPartUpdated(event: ServerEvent) {
        val partElement = event.payload["part"]?.toJsonElement() ?: return
        val part = try {
            json.decodeFromJsonElement(Part.serializer(), partElement)
        } catch (_: Exception) {
            return
        }
        if (part.sessionID != sessionId) return
        val stored = messages.getOrPut(part.messageID) {
            placeholderMessage(part.messageID)
        }
        val existing = stored.parts[part.id]
        val incomingRevision = part.revision()
        if (existing != null && existing.revision() > incomingRevision) return
        stored.parts[part.id] = part
    }

    private fun applyPartDelta(event: ServerEvent) {
        if (event.payload.stringValue("field") != "text") return
        val messageId = event.payload.stringValue("messageID") ?: return
        val partId = event.payload.stringValue("partID") ?: return
        val delta = event.payload.stringValue("delta") ?: return
        val stored = messages.getOrPut(messageId) { placeholderMessage(messageId) }
        val existing = stored.parts[partId]
        stored.parts[partId] = if (existing == null) {
            Part(
                id = partId,
                sessionID = sessionId,
                messageID = messageId,
                type = "text",
                text = delta,
                time = JsonObject(emptyMap()),
            )
        } else {
            existing.copy(text = (existing.text ?: "") + delta)
        }
    }

    private fun applyPartRemoved(event: ServerEvent) {
        val partId = event.payload.stringValue("partID")
            ?: (event.payload["part"]?.toJsonElement() as? JsonObject)
                ?.stringOrNull("id")
            ?: return
        messages.values.forEach { it.parts.remove(partId) }
    }

    private fun applyMessageRemoved(event: ServerEvent) {
        val messageId = event.payload.stringValue("messageID") ?: return
        messages.remove(messageId)
    }

    private fun placeholderMessage(messageId: String): StoredMessage = StoredMessage(
        info = MessageInfo(
            id = messageId,
            sessionID = sessionId,
            role = "assistant",
            time = JsonObject(emptyMap()),
        ),
        parts = LinkedHashMap(),
    )

    private fun belongsToSession(event: ServerEvent): Boolean =
        event.payload.stringValue("sessionID") == sessionId

    private fun markSeen(eventId: String): Boolean {
        if (!seenEventIds.add(eventId)) return false
        if (seenEventIds.size > maxSeenEvents) {
            val iterator = seenEventIds.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        return true
    }

    private fun Part.revision(): Long =
        time?.longFrom("updated", "end", "start") ?: 0L

    private fun Map<String, Any?>.stringValue(name: String): String? {
        val element = this[name]?.toJsonElement() ?: return null
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private data class StoredMessage(
        var info: MessageInfo,
        val parts: LinkedHashMap<String, Part>,
    )
}
