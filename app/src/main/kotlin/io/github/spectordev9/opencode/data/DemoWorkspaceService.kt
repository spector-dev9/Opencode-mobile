package io.github.spectordev9.opencode.data

import io.github.spectordev9.opencode.core.common.ApiError
import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.common.ErrorCode
import io.github.spectordev9.opencode.core.common.TrashId
import io.github.spectordev9.opencode.core.workspace.Chunk
import io.github.spectordev9.opencode.core.workspace.Entry
import io.github.spectordev9.opencode.core.workspace.EntryKind
import io.github.spectordev9.opencode.core.workspace.Precondition
import io.github.spectordev9.opencode.core.workspace.WriteReceipt
import io.github.spectordev9.opencode.core.workspace.WorkspaceService
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.min

/**
 * Debug-only in-memory workspace used by the Compose file browser. It uses
 * workspace-relative paths and never touches the host filesystem.
 */
class DemoWorkspaceService(
    initialFiles: Map<String, ByteArray> = DemoData.files(),
    private val clock: () -> Long = System::currentTimeMillis,
) : WorkspaceService {
    private val files = LinkedHashMap<String, ByteArray>()
    private val directories = linkedSetOf<String>("")
    private val trash = LinkedHashMap<String, TrashedItem>()

    init {
        initialFiles.forEach { (path, bytes) ->
            normalize(path)?.let { normalized ->
                files[normalized] = bytes.copyOf()
                parentDirectories(normalized).forEach(directories::add)
            }
        }
        directories += listOf("docs/design", "empty-folder")
    }

    override suspend fun list(path: String): ApiResult<List<Entry>> {
        val normalized = normalize(path) ?: return denied(path)
        if (!isDirectory(normalized)) return denied(path)

        val allPaths = files.keys + directories.filter { it.isNotEmpty() }
        val children = allPaths
            .filter { it != normalized && parentOf(it) == normalized }
            .distinct()
            .map { childPath -> entry(childPath) }
            .sortedWith(compareBy<Entry> { it.kind != EntryKind.DIRECTORY }.thenBy { it.name.lowercase() })
        return ApiResult.Success(children)
    }

    override suspend fun stat(path: String): ApiResult<Entry> {
        val normalized = normalize(path) ?: return denied(path)
        val entry = entryOrNull(normalized) ?: return denied(path)
        return ApiResult.Success(entry)
    }

    override suspend fun readRange(path: String, offset: Long, length: Int): ApiResult<Chunk> {
        val normalized = normalize(path) ?: return denied(path)
        val bytes = files[normalized] ?: return denied(path)
        val start = offset.coerceAtLeast(0).coerceAtMost(bytes.size.toLong()).toInt()
        val end = min(bytes.size, start + length.coerceAtLeast(0))
        return ApiResult.Success(
            Chunk(
                offset = start.toLong(),
                bytes = bytes.copyOfRange(start, end),
                endOfFile = end >= bytes.size,
            ),
        )
    }

    override suspend fun write(
        path: String,
        data: ByteArray,
        expect: Precondition,
    ): ApiResult<WriteReceipt> {
        val normalized = normalize(path) ?: return denied(path)
        if (isDirectory(normalized)) {
            return ApiResult.Failure(ApiError(ErrorCode.E_CONFLICT, "Path is a directory: $path"))
        }
        val previous = files[normalized]
        if (expect is Precondition.Match) {
            val matches = previous != null &&
                previous.size.toLong() == expect.sizeBytes &&
                (expect.sha256 == null || previous.sha256() == expect.sha256)
            if (!matches) {
                return ApiResult.Failure(ApiError(ErrorCode.E_CONFLICT, "Write precondition failed: $path"))
            }
        }
        parentDirectories(normalized).forEach(directories::add)
        files[normalized] = data.copyOf()
        return ApiResult.Success(
            WriteReceipt(
                path = normalized,
                sizeBytes = data.size.toLong(),
                modifiedAtEpochMillis = clock(),
                sha256 = data.sha256(),
            ),
        )
    }

    override suspend fun mkdir(path: String): ApiResult<Entry> {
        val normalized = normalize(path) ?: return denied(path)
        if (normalized.isEmpty()) return ApiResult.Success(entry(""))
        if (files.containsKey(normalized)) {
            return ApiResult.Failure(ApiError(ErrorCode.E_CONFLICT, "A file already exists: $path"))
        }
        parentDirectories(normalized).forEach(directories::add)
        directories += normalized
        return ApiResult.Success(entry(normalized))
    }

    override suspend fun rename(from: String, to: String): ApiResult<Entry> {
        val source = normalize(from) ?: return denied(from)
        val destination = normalize(to) ?: return denied(to)
        if (source.isEmpty() || destination.isEmpty()) {
            return ApiResult.Failure(ApiError(ErrorCode.E_PATH_DENIED, "Cannot rename the workspace root"))
        }
        if (!exists(source)) return denied(from)
        if (exists(destination)) {
            return ApiResult.Failure(ApiError(ErrorCode.E_CONFLICT, "Destination exists: $to"))
        }

        if (files.containsKey(source)) {
            files[destination] = files.remove(source) ?: return denied(from)
        } else {
            val descendants = files.keys.filter { it.startsWith("$source/") }
            descendants.forEach { path ->
                val suffix = path.removePrefix(source)
                files[destination + suffix] = files.remove(path) ?: ByteArray(0)
            }
            val childDirs = directories.filter { it == source || it.startsWith("$source/") }
            childDirs.forEach { dir ->
                directories.remove(dir)
                directories += destination + dir.removePrefix(source)
            }
        }
        parentDirectories(destination).forEach(directories::add)
        return ApiResult.Success(entry(destination))
    }

    override suspend fun move(from: String, to: String): ApiResult<Entry> = rename(from, to)

    override suspend fun trash(path: String): ApiResult<TrashId> {
        val normalized = normalize(path) ?: return denied(path)
        if (normalized.isEmpty() || !exists(normalized)) return denied(path)

        val removedFiles = files
            .filterKeys { it == normalized || it.startsWith("$normalized/") }
            .mapValues { it.value.copyOf() }
        val removedDirs = directories
            .filter { it == normalized || it.startsWith("$normalized/") }
            .toSet()
        removedFiles.keys.forEach(files::remove)
        removedDirs.forEach(directories::remove)

        val id = TrashId("trash_${UUID.randomUUID().toString().take(8)}")
        trash[id.value] = TrashedItem(
            path = normalized,
            files = removedFiles,
            directories = removedDirs,
        )
        return ApiResult.Success(id)
    }

    override suspend fun restore(id: TrashId): ApiResult<Entry> {
        val trashed = trash[id.value] ?: return denied(id.value)
        val destination = if (exists(trashed.path)) {
            "${trashed.path}-restored-${id.value.takeLast(4)}"
        } else {
            trashed.path
        }
        trashed.files.forEach { (path, bytes) ->
            val suffix = path.removePrefix(trashed.path)
            files[destination + suffix] = bytes
        }
        trashed.directories.forEach { path ->
            val suffix = path.removePrefix(trashed.path)
            directories += destination + suffix
        }
        parentDirectories(destination).forEach(directories::add)
        trash.remove(id.value)
        return ApiResult.Success(entry(destination))
    }

    override suspend fun search(path: String, query: String, limit: Int): ApiResult<List<Entry>> {
        val normalized = normalize(path) ?: return denied(path)
        if (query.isBlank()) return ApiResult.Success(emptyList())
        val results = (files.keys + directories.filter { it.isNotEmpty() })
            .filter { candidate ->
                candidate == normalized || candidate.startsWith("${normalized.ifEmpty { "" }}/") ||
                    normalized.isEmpty()
            }
            .filter { candidate -> candidate.contains(query, ignoreCase = true) }
            .sorted()
            .take(limit.coerceAtLeast(0))
            .map(::entry)
        return ApiResult.Success(results)
    }

    private fun exists(path: String): Boolean =
        path.isEmpty() || files.containsKey(path) || directories.contains(path)

    private fun isDirectory(path: String): Boolean =
        path.isEmpty() || directories.contains(path)

    private fun entryOrNull(path: String): Entry? = when {
        path.isEmpty() -> entry(path)
        files.containsKey(path) -> entry(path)
        directories.contains(path) -> entry(path)
        else -> null
    }

    private fun entry(path: String): Entry {
        val isDir = isDirectory(path)
        val name = if (path.isEmpty()) "/" else path.substringAfterLast('/')
        return Entry(
            path = path,
            name = name,
            kind = if (isDir) EntryKind.DIRECTORY else EntryKind.FILE,
            sizeBytes = if (isDir) 0 else (files[path]?.size?.toLong() ?: 0),
            modifiedAtEpochMillis = clock() - (path.hashCode().toLong() and 0xFFFF),
        )
    }

    private fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    private fun parentDirectories(path: String): List<String> {
        if (path.isEmpty()) return emptyList()
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.size <= 1) return listOf("")
        val result = mutableListOf("")
        var current = ""
        for (index in 0 until parts.lastIndex) {
            current = if (current.isEmpty()) parts[index] else "$current/${parts[index]}"
            result += current
        }
        return result
    }

    private fun normalize(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.contains('\u0000')) return null
        val withoutEdges = trimmed.trim('/')
        if (withoutEdges.isEmpty()) return ""
        val parts = withoutEdges.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun <T> denied(path: String): ApiResult<T> =
        ApiResult.Failure(ApiError(ErrorCode.E_PATH_DENIED, "Path is outside the demo workspace: $path"))

    private data class TrashedItem(
        val path: String,
        val files: Map<String, ByteArray>,
        val directories: Set<String>,
    )
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
