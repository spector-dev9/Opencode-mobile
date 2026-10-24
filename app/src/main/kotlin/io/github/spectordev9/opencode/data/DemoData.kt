package io.github.spectordev9.opencode.data

import io.github.spectordev9.opencode.core.model.Message
import io.github.spectordev9.opencode.core.model.MessageInfo
import io.github.spectordev9.opencode.core.model.Part
import io.github.spectordev9.opencode.core.model.Session
import io.github.spectordev9.opencode.core.model.SessionTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DemoData {
    const val WORKSPACE = "/workspace"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun sessions(now: Long = System.currentTimeMillis()): List<Session> = listOf(
        Session(
            id = "ses_demo_build",
            slug = "fix-android-build",
            projectID = "prj_demo",
            directory = WORKSPACE,
            title = "Fix failing Android build",
            version = "1.19.3",
            time = SessionTime(created = now - 3 * HOUR, updated = now - 95 * MINUTE),
            agent = "build",
            cost = 0.08,
            tokens = buildJsonObject {
                put("input", 18_400)
                put("output", 3_120)
            },
        ),
        Session(
            id = "ses_demo_sse",
            slug = "sse-reconnect-tests",
            projectID = "prj_demo",
            directory = WORKSPACE,
            title = "Add SSE reconnection tests",
            version = "1.19.3",
            time = SessionTime(created = now - 2 * DAY, updated = now - 22 * HOUR),
            agent = "test",
            cost = 0.14,
            tokens = buildJsonObject {
                put("input", 31_200)
                put("output", 6_400)
            },
        ),
        Session(
            id = "ses_demo_workspace",
            slug = "workspace-path-guards",
            projectID = "prj_demo",
            directory = WORKSPACE,
            title = "Review workspace path guards",
            version = "1.19.3",
            time = SessionTime(created = now - 4 * DAY, updated = now - 3 * DAY),
            agent = "explore",
            cost = 0.03,
            tokens = buildJsonObject {
                put("input", 9_800)
                put("output", 1_250)
            },
        ),
        Session(
            id = "ses_demo_release",
            slug = "release-checklist",
            projectID = "prj_demo",
            directory = WORKSPACE,
            title = "Prepare the release checklist",
            version = "1.19.3",
            time = SessionTime(created = now - 8 * DAY, updated = now - 6 * DAY),
            agent = "plan",
        ),
    )

    fun messages(sessionId: String, now: Long = System.currentTimeMillis()): List<Message> =
        when (sessionId) {
            "ses_demo_build" -> buildMessages(now)
            "ses_demo_sse" -> sseMessages(now)
            "ses_demo_workspace" -> workspaceMessages(now)
            "ses_demo_release" -> releaseMessages(now)
            else -> emptyList()
        }

    fun sessionJson(session: Session): JsonObject =
        json.encodeToJsonElement(Session.serializer(), session) as JsonObject

    fun messageInfoJson(info: MessageInfo): JsonObject =
        json.encodeToJsonElement(MessageInfo.serializer(), info) as JsonObject

    fun partJson(part: Part): JsonObject =
        json.encodeToJsonElement(Part.serializer(), part) as JsonObject

    fun sessionInfoPayload(session: Session): Map<String, Any?> = mapOf(
        "sessionID" to session.id,
        "info" to sessionJson(session),
    )

    fun messageInfoPayload(info: MessageInfo): Map<String, Any?> = mapOf(
        "sessionID" to info.sessionID,
        "info" to messageInfoJson(info),
    )

    fun partPayload(part: Part): Map<String, Any?> = mapOf(
        "sessionID" to part.sessionID,
        "part" to partJson(part),
        "time" to (part.time?.let { (it["end"] ?: it["start"]) } ?: 0L),
    )

    private fun buildMessages(now: Long): List<Message> {
        val userId = "msg_demo_build_user"
        val assistantId = "msg_demo_build_assistant"
        val userCreated = now - 92 * MINUTE
        val assistantCreated = now - 90 * MINUTE
        val completed = now - 84 * MINUTE
        return listOf(
            Message(
                info = MessageInfo(
                    id = userId,
                    sessionID = "ses_demo_build",
                    role = "user",
                    time = time(created = userCreated, completed = userCreated + 1_000),
                    agent = "build",
                    modelID = "deepseek-v4.1-flash-free",
                    providerID = "opencode",
                ),
                parts = listOf(
                    textPart(
                        id = "prt_demo_build_user_1",
                        sessionId = "ses_demo_build",
                        messageId = userId,
                        text = "The debug build fails after the Compose upgrade. Can you find the cause and fix it?",
                        start = userCreated,
                        end = userCreated + 1_000,
                    ),
                ),
            ),
            Message(
                info = MessageInfo(
                    id = assistantId,
                    sessionID = "ses_demo_build",
                    role = "assistant",
                    time = time(created = assistantCreated, completed = completed),
                    agent = "build",
                    modelID = "deepseek-v4.1-flash-free",
                    providerID = "opencode",
                    cost = 0.08,
                    tokens = tokens(input = 18_400, output = 3_120),
                    finish = "stop",
                ),
                parts = listOf(
                    reasoningPart(
                        id = "prt_demo_build_reasoning_1",
                        sessionId = "ses_demo_build",
                        messageId = assistantId,
                        text = "The failure is in the Java toolchain, not Compose. The app module asks for Java 21 while the local environment is still on Java 17.",
                        start = assistantCreated,
                        end = assistantCreated + 4_000,
                    ),
                    textPart(
                        id = "prt_demo_build_text_1",
                        sessionId = "ses_demo_build",
                        messageId = assistantId,
                        text = "I found the mismatch. The app module targets Java 21, but the build environment is still using Java 17. The toolchain resolution fails before Compose is involved.",
                        start = assistantCreated + 4_000,
                        end = assistantCreated + 12_000,
                    ),
                    toolPart(
                        id = "prt_demo_build_tool_1",
                        sessionId = "ses_demo_build",
                        messageId = assistantId,
                        tool = "read",
                        callId = "call_demo_build_1",
                        title = "app/build.gradle.kts",
                        status = "completed",
                        detail = "63 lines read",
                        start = assistantCreated + 12_000,
                        end = assistantCreated + 14_000,
                    ),
                    textPart(
                        id = "prt_demo_build_text_2",
                        sessionId = "ses_demo_build",
                        messageId = assistantId,
                        text = "The relevant block is:\n\n```kotlin\ncompileOptions {\n    sourceCompatibility = JavaVersion.VERSION_21\n    targetCompatibility = JavaVersion.VERSION_21\n}\n```\n\nAlign the Gradle toolchain and the IDE JDK, then run `assembleDebug` again.",
                        start = assistantCreated + 14_000,
                        end = assistantCreated + 28_000,
                    ),
                    textPart(
                        id = "prt_demo_build_text_3",
                        sessionId = "ses_demo_build",
                        messageId = assistantId,
                        text = "After aligning the toolchain, `assembleDebug` completes and the Compose previews compile. I did not change any runtime or UI code.",
                        start = assistantCreated + 28_000,
                        end = completed,
                    ),
                ),
            ),
        )
    }

    private fun sseMessages(now: Long): List<Message> {
        val userId = "msg_demo_sse_user"
        val assistantId = "msg_demo_sse_assistant"
        val userCreated = now - 24 * HOUR
        val start = now - 24 * HOUR + 5_000
        val completed = now - 23 * HOUR
        return listOf(
            Message(
                info = MessageInfo(
                    id = userId,
                    sessionID = "ses_demo_sse",
                    role = "user",
                    time = time(created = userCreated, completed = userCreated + 1_000),
                    agent = "test",
                    modelID = "deepseek-v4.1-flash-free",
                    providerID = "opencode",
                ),
                parts = listOf(
                    textPart(
                        id = "prt_demo_sse_user_1",
                        sessionId = "ses_demo_sse",
                        messageId = userId,
                        text = "Add a reconnect test that covers a dropped SSE connection during a streamed reply.",
                        start = userCreated,
                        end = userCreated + 1_000,
                    ),
                ),
            ),
            Message(
                info = MessageInfo(
                    id = assistantId,
                    sessionID = "ses_demo_sse",
                    role = "assistant",
                    time = time(created = start, completed = completed),
                    agent = "test",
                    modelID = "deepseek-v4.1-flash-free",
                    providerID = "opencode",
                    cost = 0.14,
                    tokens = tokens(input = 31_200, output = 6_400),
                    finish = "stop",
                ),
                parts = listOf(
                    textPart(
                        id = "prt_demo_sse_text_1",
                        sessionId = "ses_demo_sse",
                        messageId = assistantId,
                        text = "I added a deterministic reconnect scenario around the existing transcript store. The test drops the event flow after the first text delta, reconnects with a new `server.connected`, fetches the snapshot, and replays the buffered event.",
                        start = start,
                        end = start + 20_000,
                    ),
                    toolPart(
                        id = "prt_demo_sse_tool_1",
                        sessionId = "ses_demo_sse",
                        messageId = assistantId,
                        tool = "edit",
                        callId = "call_demo_sse_1",
                        title = "TranscriptStoreTest.kt",
                        status = "completed",
                        detail = "+82 lines",
                        start = start + 20_000,
                        end = start + 24_000,
                    ),
                    textPart(
                        id = "prt_demo_sse_text_2",
                        sessionId = "ses_demo_sse",
                        messageId = assistantId,
                        text = "The test asserts that the final transcript equals the authoritative snapshot and that no duplicate event is applied:\n\n```kotlin\nassertEquals(snapshot, store.snapshot())\nassertEquals(ReceiveOutcome.DUPLICATE, store.receive(duplicate))\n```",
                        start = start + 24_000,
                        end = completed,
                    ),
                ),
            ),
        )
    }

    private fun workspaceMessages(now: Long): List<Message> {
        val userId = "msg_demo_workspace_user"
        val assistantId = "msg_demo_workspace_assistant"
        val userCreated = now - 3 * DAY - HOUR
        val start = now - 3 * DAY
        val completed = now - 3 * DAY + 30 * MINUTE
        return listOf(
            Message(
                info = MessageInfo(
                    id = userId,
                    sessionID = "ses_demo_workspace",
                    role = "user",
                    time = time(created = userCreated, completed = userCreated + 1_000),
                    agent = "explore",
                    modelID = "deepseek-v4.1-flash-free",
                    providerID = "opencode",
                ),
                parts = listOf(
                    textPart(
                        id = "prt_demo_workspace_user_1",
                        sessionId = "ses_demo_workspace",
                        messageId = userId,
                        text = "Review the workspace path guard for symlink escapes and tell me what is still missing.",
                        start = userCreated,
                        end = userCreated + 1_000,
                    ),
                ),
            ),
            Message(
                info = MessageInfo(
                    id = assistantId,
                    sessionID = "ses_demo_workspace",
                    role = "assistant",
                    time = time(created = start, completed = completed),
                    agent = "explore",
                    modelID = "deepseek-v4.1-flash-free",
                    providerID = "opencode",
                    cost = 0.03,
                    tokens = tokens(input = 9_800, output = 1_250),
                    finish = "stop",
                ),
                parts = listOf(
                    textPart(
                        id = "prt_demo_workspace_text_1",
                        sessionId = "ses_demo_workspace",
                        messageId = assistantId,
                        text = "The guard normalises the path before resolving it and rejects parent traversal. The remaining gap is the final canonical-path check after a symlink is followed.",
                        start = start,
                        end = start + 15_000,
                    ),
                    toolPart(
                        id = "prt_demo_workspace_tool_1",
                        sessionId = "ses_demo_workspace",
                        messageId = assistantId,
                        tool = "read",
                        callId = "call_demo_workspace_1",
                        title = "core/workspace/PathGuard.kt",
                        status = "completed",
                        detail = "41 lines read",
                        start = start + 15_000,
                        end = start + 18_000,
                    ),
                    textPart(
                        id = "prt_demo_workspace_text_2",
                        sessionId = "ses_demo_workspace",
                        messageId = assistantId,
                        text = "Add a canonical-path comparison after `realpath` and cover symlink loops in the fuzz test. That closes WS-1 without changing the service interface.",
                        start = start + 18_000,
                        end = completed,
                    ),
                ),
            ),
        )
    }

    private fun releaseMessages(now: Long): List<Message> = emptyList()

    private fun time(created: Long, completed: Long? = null): JsonObject = buildJsonObject {
        put("created", created)
        if (completed != null) put("completed", completed)
    }

    private fun partTime(start: Long, end: Long? = null): JsonObject = buildJsonObject {
        put("start", start)
        if (end != null) put("end", end)
    }

    private fun tokens(input: Long, output: Long): JsonObject = buildJsonObject {
        put("input", input)
        put("output", output)
        put("reasoning", 0)
        put(
            "cache",
            buildJsonObject {
                put("read", 0)
                put("write", 0)
            },
        )
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

    private fun reasoningPart(
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
        type = "reasoning",
        text = text,
        time = partTime(start, end),
    )

    private fun toolPart(
        id: String,
        sessionId: String,
        messageId: String,
        tool: String,
        callId: String,
        title: String,
        status: String,
        detail: String,
        start: Long,
        end: Long? = null,
    ): Part = Part(
        id = id,
        sessionID = sessionId,
        messageID = messageId,
        type = "tool",
        callID = callId,
        tool = tool,
        state = buildJsonObject {
            put("status", status)
            put("title", title)
            put("metadata", buildJsonObject { put("detail", detail) })
            put("input", buildJsonObject { put("path", title) })
            if (status == "completed") {
                put("output", detail)
            }
        },
        time = partTime(start, end),
    )

    fun files(): Map<String, ByteArray> = linkedMapOf(
        "README.md" to """
            # OpenCode Mobile workspace

            This is a sample workspace rendered by the native Compose file browser.
            It is demo data and is not read from a real runtime.

            - `docs/` contains architecture notes.
            - `app/` contains the Android shell.
            - `core/` contains the shared contracts.
        """.trimIndent().toByteArray(),
        "docs/architecture.md" to """
            # Architecture

            The UI talks to application service ports. Runtime, filesystem, and
            transport implementations stay outside the presentation layer.
        """.trimIndent().toByteArray(),
        "docs/runtime-notes.md" to """
            # Runtime notes

            The supervisor is responsible for the loopback listener, the per-launch
            secret, and process-tree shutdown. The UI must not construct either.
        """.trimIndent().toByteArray(),
        "app/build.gradle.kts" to """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.compose)
            }

            android {
                compileSdk = 36
                buildFeatures { compose = true }
            }
        """.trimIndent().toByteArray(),
        "core/model/Session.kt" to """
            @Serializable
            data class Session(
                val id: String,
                val title: String,
                val time: SessionTime,
            )
        """.trimIndent().toByteArray(),
        "core/model/Message.kt" to """
            @Serializable
            data class Message(
                val info: MessageInfo,
                val parts: List<Part>,
            )
        """.trimIndent().toByteArray(),
        "runtime/manifest.json" to """
            {
              "openCodeVersion": "1.19.3",
              "profile": "A_DIRECT"
            }
        """.trimIndent().toByteArray(),
        "assets/binary-sample.bin" to byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x01,
            0x02,
            0x03,
        ),
    )

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
}
