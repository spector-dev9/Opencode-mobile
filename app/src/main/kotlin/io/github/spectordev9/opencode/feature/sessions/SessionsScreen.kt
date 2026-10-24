package io.github.spectordev9.opencode.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.spectordev9.opencode.core.model.Session
import io.github.spectordev9.opencode.ui.components.EmptyState
import io.github.spectordev9.opencode.ui.components.ErrorCard
import io.github.spectordev9.opencode.ui.components.LabelChip
import io.github.spectordev9.opencode.ui.port.ChatRepository
import io.github.spectordev9.opencode.ui.util.formatCost
import io.github.spectordev9.opencode.ui.util.formatRelativeTime

@Composable
fun SessionsRoute(
    repository: ChatRepository,
    onOpenSession: (id: String, title: String?) -> Unit,
) {
    val viewModel: SessionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SessionsViewModel(repository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.openSessionId, state.sessions) {
        val id = state.openSessionId ?: return@LaunchedEffect
        val title = state.sessions.firstOrNull { it.id == id }?.title
        viewModel.consumeOpenSession()
        onOpenSession(id, title)
    }

    SessionsScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onQueryChange = viewModel::onQueryChange,
        onOpenSession = { session -> onOpenSession(session.id, session.title) },
        onCreateClick = viewModel::showCreateDialog,
        onCreateDismiss = viewModel::dismissCreateDialog,
        onCreateConfirm = viewModel::createSession,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    state: SessionsUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenSession: (Session) -> Unit,
    onCreateClick: () -> Unit,
    onCreateDismiss: () -> Unit,
    onCreateConfirm: (String) -> Unit,
    onClearError: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh sessions",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateClick,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New session") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading && state.sessions.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.sessions.isNotEmpty() || state.isSearching) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search sessions") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                )
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
                state.isLoading && state.sessions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.visibleSessions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.Forum,
                            title = if (state.isSearching) "No matching sessions" else "No sessions yet",
                            body = if (state.isSearching) {
                                "Try a different search, or start a new session."
                            } else {
                                "Start a new session to begin working with OpenCode."
                            },
                            actionLabel = if (state.isSearching) null else "New session",
                            onAction = if (state.isSearching) null else onCreateClick,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.visibleSessions, key = Session::id) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onOpenSession(session) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showCreateDialog) {
        CreateSessionDialog(
            isCreating = state.isCreating,
            onDismiss = onCreateDismiss,
            onConfirm = onCreateConfirm,
        )
    }
}

@Composable
private fun SessionCard(
    session: Session,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = session.title.ifBlank { "Untitled session" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.agent?.takeIf { it.isNotBlank() }?.let { agent ->
                    LabelChip(text = agent)
                }
                LabelChip(text = "#${session.slug}")
                Spacer(modifier = Modifier.weight(1f))
                val cost = session.cost
                if (cost != null && cost > 0.0) {
                    Text(
                        text = formatCost(cost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatRelativeTime(session.time.updated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreateSessionDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val submit = {
        if (!isCreating) {
            onConfirm(title.value.trim().ifBlank { "New session" })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Give the session a short title. You can rename it later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title.value,
                    onValueChange = { title.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    placeholder = { Text("New session") },
                    singleLine = true,
                    enabled = !isCreating,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = !isCreating) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Cancel")
            }
        },
    )
}
