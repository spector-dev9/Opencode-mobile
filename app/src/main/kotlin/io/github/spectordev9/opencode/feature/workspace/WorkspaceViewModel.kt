package io.github.spectordev9.opencode.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.spectordev9.opencode.core.common.ApiResult
import io.github.spectordev9.opencode.core.workspace.Entry
import io.github.spectordev9.opencode.core.workspace.EntryKind
import io.github.spectordev9.opencode.core.workspace.WorkspaceService
import io.github.spectordev9.opencode.ui.util.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_PREVIEW_BYTES: Int = 64 * 1024

data class PreviewUiState(
    val path: String,
    val name: String,
    val text: String? = null,
    val isBinary: Boolean = false,
    val isTruncated: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class WorkspaceUiState(
    val path: String = "",
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val preview: PreviewUiState? = null,
) {
    val visibleEntries: List<Entry>
        get() {
            val filtered = if (query.isBlank()) {
                entries
            } else {
                entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            return filtered.sortedWith(
                compareBy(
                    { it.kind != EntryKind.DIRECTORY },
                    { it.name.lowercase() },
                ),
            )
        }

    val parentPath: String?
        get() {
            if (path.isBlank()) return null
            val trimmed = path.trimEnd('/')
            val slash = trimmed.lastIndexOf('/')
            return when {
                slash < 0 -> ""
                slash == 0 -> ""
                else -> trimmed.substring(0, slash)
            }
        }
}

class WorkspaceViewModel(
    private val workspace: WorkspaceService,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var previewJob: Job? = null

    init {
        openDirectory("")
    }

    fun openDirectory(path: String) {
        loadJob?.cancel()
        previewJob?.cancel()
        mutableState.update {
            it.copy(
                path = path.trim('/'),
                isLoading = true,
                error = null,
                preview = null,
            )
        }
        loadJob = scope.launch {
            when (val result = workspace.list(path)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        entries = result.value,
                        error = null,
                    )
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        entries = emptyList(),
                        error = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun refresh() {
        openDirectory(mutableState.value.path)
    }

    fun onQueryChange(value: String) {
        mutableState.update { it.copy(query = value) }
    }

    fun openPreview(entry: Entry) {
        if (entry.kind != EntryKind.FILE) return
        previewJob?.cancel()
        mutableState.update {
            it.copy(
                preview = PreviewUiState(
                    path = entry.path,
                    name = entry.name,
                    isLoading = true,
                ),
            )
        }
        previewJob = scope.launch {
            when (
                val result = workspace.readRange(
                    path = entry.path,
                    offset = 0,
                    length = MAX_PREVIEW_BYTES,
                )
            ) {
                is ApiResult.Success -> {
                    val chunk = result.value
                    val binary = chunk.bytes.any { it == 0.toByte() }
                    mutableState.update { state ->
                        state.copy(
                            preview = state.preview?.copy(
                                text = if (binary) null else chunk.bytes.toString(Charsets.UTF_8),
                                isBinary = binary,
                                isTruncated = !chunk.endOfFile,
                                isLoading = false,
                                error = null,
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> mutableState.update { state ->
                    state.copy(
                        preview = state.preview?.copy(
                            isLoading = false,
                            error = result.error.toUserMessage(),
                        ),
                    )
                }
            }
        }
    }

    fun closePreview() {
        previewJob?.cancel()
        mutableState.update { it.copy(preview = null) }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }
}
