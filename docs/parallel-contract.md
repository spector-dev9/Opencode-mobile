# Parallel UI/runtime contract

Status: UI presentation slice implemented against isolated demo data.
Production service wiring remains blocked by the G0 decision status recorded
in `docs/plan/phase-0.md`; this document records the boundary the runtime
work must satisfy when wiring starts.

## Repository and worktree coordination

- Runtime work continues in the existing `opencode-mobile` worktree
  (branch `spike-run` at the time of writing).
- UI work is isolated in the `opencode-mobile-ui` worktree on branch
  `ui/native-shell`, based on `origin/main`.
- Shared contract files (`core/*`, `settings.gradle.kts`,
  `gradle/libs.versions.toml`, `docs/traceability.md`) are edited only for
  additive UI needs and must be reviewed on integration. Feature screens
  live in `app/` for this slice; extracting `feature/*` modules is a
  follow-up that must keep the same port signatures.

## Existing contracts used by the UI

| Contract | Module path | UI use |
| --- | --- | --- |
| `Session`, `Message`, `Part`, `MessageInfo`, `PermissionRequest`, `FileEntry` | `core/model` | Transcript, session list, permissions, file entries |
| `ServerEvent` | `core/gateway-api` | SSE events consumed by the transcript reducer |
| `OpenCodeClient` / `OpenCodeGateway` | `core/opencode-client`, `core/gateway-api` | Runtime-owned implementation source for the chat port; the UI does not call it directly |
| `WorkspaceService`, `Entry`, `Chunk` | `core/workspace-api` | Files screen list/read preview |
| `PtyService`, `PtySession`, `PtyEvent` | `core/terminal-api` | Future terminal presentation; not wired in this slice |
| `ApiResult`, `ApiError`, `ErrorCode` | `core/common-api` | All UI-visible failures |

The UI does not import `core/runtime`, `core/supervisor`, `core/security`,
or `core/diagnostics`. Runtime state is presented only through the
application port described below.

## UI-owned application port

The first UI slice adds one use-case port because `OpenCodeClient` exposes
the reads, prompt, and event stream but not session creation, abort, or
permission replies. The port is not a second transport and must be
implemented by wrapping the existing authenticated client and the bridge
commands, not by adding another HTTP/SSE stack.

```kotlin
// app/src/main/kotlin/io/github/spectordev9/opencode/ui/port/ChatRepository.kt
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
```

Required production mapping:

| Port method | Runtime source |
| --- | --- |
| `listSessions` | `OpenCodeClient.listSessions(directory)` |
| `listMessages` | `OpenCodeClient.listMessages(sessionId, directory)` |
| `prompt` | `OpenCodeClient.promptAsync(sessionId, directory, PromptInput)` |
| `events` | `OpenCodeClient.events(directory)` with reconnect/resync semantics |
| `createSession` | `POST /session` (bridge command `session.create`) through the typed gateway |
| `abort` | `POST /session/{sessionID}/abort` (bridge command `session.abort`) |
| `replyPermission` | `POST /session/{sessionID}/permissions/{permissionID}` (bridge command `session.permissionReply`) |

`PermissionDecision` is `ALLOW_ONCE`, `ALLOW_ALWAYS`, or `REJECT`. The
implementation owns translation to OpenCode's response body; the UI does
not construct API payloads.

### Composition root

`AppDependencies` in `app/.../AppDependencies.kt` is the only injection
point. It contains `chat`, `workspace`, `preferences`, and `isDemo`.
Feature screens and ViewModels receive these dependencies by constructor;
no screen reaches a singleton runtime or a transport directly.

The current build selects:

- debug: `DemoChatRepository` + `DemoWorkspaceService` and a persistent
  “Demo data” banner;
- release: `UnavailableChatRepository` + `UnavailableWorkspaceService`,
  which surface `E_RUNTIME_DOWN`. A release build must not silently use
  demo data.

Replacing demo data requires no UI change: implement `ChatRepository`
over the real client, return the real `WorkspaceService`, set
`isDemo = false`, and remove the demo warning. Do not keep a mock path
reachable in a release build.

## Streaming contract

Events arrive as `ServerEvent(type, directory, id, payload)`.
`payload` is a JSON-compatible map. The UI reducer (`TranscriptStore`)
accepts only the session named in `sessionID` and applies this mapping:

| Event | UI effect |
| --- | --- |
| `server.connected` | Enter resync: buffer subsequent events, fetch `listMessages`, replace the transcript, replay the buffer, resume live application |
| `message.updated` | Upsert message metadata (role, timestamps, cost, tokens, provider/model) |
| `message.part.updated` | Upsert the part; a part revision older than the current one is ignored |
| `message.part.delta` | Append `delta` when `field == "text"`; create the part if it arrived first |
| `message.part.removed` | Remove the part |
| `message.removed` | Remove the message |
| `session.idle` | End the in-progress turn; `sessionID` must match |
| `session.error` | Show an error and end the in-progress turn |
| `session.updated` | Refresh the session title |
| `permission.asked` | Show the permission card with Allow once / Always / Deny |
| `permission.replied` | Clear the matching permission card |
| `server.heartbeat` | Ignore (the transport's idle watchdog owns this) |

Event ids are deduplicated in a bounded window. Part revisions use the
first available numeric value from `part.time.updated`, `part.time.end`,
then `part.time.start`, matching the recorded S4 resync algorithm. The
gateway remains responsible for reconnecting and for fetching the snapshot
on `server.connected`; the UI only requests the snapshot through
`listMessages` and owns presentation state.

The UI never polls while connected. Snapshot reads occur on initial load,
manual refresh, and `server.connected` resync only.

### Cancellation and errors

- Sending a prompt disables further sends until `session.idle`,
  `session.error`, or an abort result.
- The stop control calls `abort`; the UI shows the stopping state until the
  call returns and does not append deltas after the turn is closed.
- `ApiResult.Failure` is rendered with its `ErrorCode`. `E_AUTH` and
  `E_RUNTIME_DOWN` are shown as connection/authentication states, not as
  transcript messages. `E_TIMEOUT` and `E_UPSTREAM` preserve the current
  transcript and offer a retry.
- Cancellation from the UI cancels the ViewModel's coroutine; the
  repository must not turn cancellation into an error event.

### Threading and lifecycle

- Repository suspend functions are main-safe; the implementation may move
  I/O to its own dispatcher.
- `events()` is collected for the lifetime of the chat ViewModel.
- Demo events are emitted on the caller's coroutine; the production
  implementation must preserve arrival order and not lose the final part
  update before `session.idle`.
- A ViewModel owns no server resources. Closing the screen stops event
  collection; the server turn continues and is reconciled on return.

## Workspace contract

The files screen depends only on `WorkspaceService`:

- `path` is a workspace-relative path using `/` separators; the empty
  string is the root.
- `list` and `stat` return `Entry` values. Directories sort before files
  and both sort by name in presentation.
- Preview reads `readRange(path, 0, 64 KiB)`. NUL bytes mark the file
  binary; `endOfFile == false` marks the preview truncated.
- `E_PATH_DENIED`, `E_TOO_LARGE`, and `E_CONFLICT` are shown as inline
  errors and never trigger a path retry outside the workspace.
- Create, rename, move, trash, and restore presentation is deliberately
  deferred; the first slice is read-only. When those controls are added,
  they must call the existing `WorkspaceService` methods and confirm
  destructive actions.

## Terminal contract (future slice)

`PtyService` is the only allowed backend. A terminal screen must attach to
`PtySession.events`, send bytes through `write`, resize through `resize`,
and close the session when leaving. A text field that echoes commands is
not an accepted implementation. The current build shows an explicit
“terminal not connected” state and does not claim PTY support.

## Acceptance and evidence for this slice

Host-checkable:

- Unit tests for transcript mapping/resync, chat/session/workspace
  ViewModels, and markdown-lite parsing.
- `python3 scripts/check-architecture.py`,
  `python3 scripts/check-bridge-contract.py`.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

Not yet accepted:

- Real service wiring, authenticated SSE recovery in the app, event-to-paint
  latency, rotation, physical-device layout, keyboard/IME behavior, and
  terminal input. These remain runtime/device acceptance items and are not
  implied by the host build.

## Integration checklist for the runtime agent

1. Implement `ChatRepository` over the existing authenticated client and
   gateway; keep `isDemo = false` in release.
2. Provide a real `WorkspaceService` from the workspace module; do not add
   filesystem access to the UI layer.
3. Keep the event payload shapes above; if OpenCode changes them, update
   `TranscriptStore` and its tests together with the gateway fixtures.
4. Run the chat resync/abort tests and then the app/UI bridge checks listed
   in `docs/plan/phase-0.md` once G0-dependent wiring is unblocked.
5. Record device evidence in `docs/evidence/RESULTS.md`; this UI slice
   contributes host build and unit-test evidence only.
