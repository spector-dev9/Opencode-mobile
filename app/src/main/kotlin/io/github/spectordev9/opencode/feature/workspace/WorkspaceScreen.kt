package io.github.spectordev9.opencode.feature.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.spectordev9.opencode.core.workspace.Entry
import io.github.spectordev9.opencode.core.workspace.EntryKind
import io.github.spectordev9.opencode.core.workspace.WorkspaceService
import io.github.spectordev9.opencode.ui.components.EmptyState
import io.github.spectordev9.opencode.ui.components.ErrorCard
import io.github.spectordev9.opencode.ui.theme.CodeTextStyle
import io.github.spectordev9.opencode.ui.util.formatBytes
import io.github.spectordev9.opencode.ui.util.formatRelativeTime

@Composable
fun WorkspaceRoute(workspace: WorkspaceService) {
    val viewModel: WorkspaceViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkspaceViewModel(workspace) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    WorkspaceScreen(
        state = state,
        onOpenDirectory = viewModel::openDirectory,
        onOpenPreview = viewModel::openPreview,
        onClosePreview = viewModel::closePreview,
        onQueryChange = viewModel::onQueryChange,
        onRefresh = viewModel::refresh,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: WorkspaceUiState,
    onOpenDirectory: (String) -> Unit,
    onOpenPreview: (Entry) -> Unit,
    onClosePreview: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Workspace")
                        Text(
                            text = "Read-only preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh workspace",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Breadcrumbs(
                path = state.path,
                onOpenDirectory = onOpenDirectory,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Filter this folder") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear filter")
                        }
                    }
                },
                singleLine = true,
            )
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.error != null) {
                ErrorCard(
                    message = state.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onRetry = onRefresh,
                    onDismiss = onClearError,
                )
            }
            when {
                state.isLoading && state.entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.visibleEntries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.FolderOff,
                            title = if (state.query.isNotBlank()) "No matching entries" else "This folder is empty",
                            body = if (state.query.isNotBlank()) {
                                "Try a different filter."
                            } else {
                                "Files created by OpenCode will appear here."
                            },
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(state.visibleEntries, key = Entry::path) { entry ->
                            EntryRow(
                                entry = entry,
                                onClick = {
                                    when (entry.kind) {
                                        EntryKind.DIRECTORY -> onOpenDirectory(entry.path)
                                        EntryKind.FILE -> onOpenPreview(entry)
                                        else -> Unit
                                    }
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    state.preview?.let { preview ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onClosePreview,
            sheetState = sheetState,
        ) {
            PreviewContent(preview)
        }
    }
}

@Composable
private fun Breadcrumbs(
    path: String,
    onOpenDirectory: (String) -> Unit,
) {
    val segments = if (path.isBlank()) {
        listOf("Home" to "")
    } else {
        buildList {
            add("Home" to "")
            var current = ""
            path.split('/').filter { it.isNotBlank() }.forEach { segment ->
                current = if (current.isBlank()) segment else "$current/$segment"
                add(segment to current)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, (label, target) ->
            if (index > 0) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { onOpenDirectory(target) },
                enabled = target != path,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: Entry,
    onClick: () -> Unit,
) {
    val clickable = entry.kind == EntryKind.DIRECTORY || entry.kind == EntryKind.FILE
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = clickable,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = when (entry.kind) {
                    EntryKind.DIRECTORY -> Icons.Outlined.Folder
                    EntryKind.SYMLINK -> Icons.Outlined.Link
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                tint = if (entry.kind == EntryKind.DIRECTORY) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = buildList {
                    if (entry.kind == EntryKind.FILE) add(formatBytes(entry.sizeBytes))
                    if (entry.modifiedAtEpochMillis > 0) {
                        add(formatRelativeTime(entry.modifiedAtEpochMillis))
                    }
                    entry.symlinkTarget?.let { add("-> $it") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" - "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.kind == EntryKind.DIRECTORY) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewContent(preview: PreviewUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = preview.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = preview.path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Preview only. Editing is not available in the UI.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        when {
            preview.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            preview.error != null -> {
                ErrorCard(message = preview.error)
            }
            preview.isBinary -> {
                Row(
                    modifier = Modifier.padding(vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = "Binary file. A text preview is not available.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            preview.text != null -> {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    SelectionContainer {
                        Text(
                            text = preview.text,
                            style = CodeTextStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
        if (preview.isTruncated && !preview.isBinary) {
            Text(
                text = "Showing the first 64 KiB only. The file is truncated.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(modifier = Modifier.size(24.dp))
    }
}
