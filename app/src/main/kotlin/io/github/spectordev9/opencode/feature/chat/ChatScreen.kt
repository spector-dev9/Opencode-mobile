package io.github.spectordev9.opencode.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.spectordev9.opencode.feature.chat.ChatPartUi.FileAttachment
import io.github.spectordev9.opencode.feature.chat.ChatPartUi.Other
import io.github.spectordev9.opencode.feature.chat.ChatPartUi.Reasoning
import io.github.spectordev9.opencode.feature.chat.ChatPartUi.Text as TextPart
import io.github.spectordev9.opencode.feature.chat.ChatPartUi.Tool
import io.github.spectordev9.opencode.ui.components.EmptyState
import io.github.spectordev9.opencode.ui.components.ErrorCard
import io.github.spectordev9.opencode.ui.components.LabelChip
import io.github.spectordev9.opencode.ui.components.MarkdownText
import io.github.spectordev9.opencode.ui.port.ChatRepository
import io.github.spectordev9.opencode.ui.port.PermissionDecision
import io.github.spectordev9.opencode.ui.theme.CodeTextStyle
import io.github.spectordev9.opencode.ui.util.formatCost
import io.github.spectordev9.opencode.ui.util.formatRelativeTime
import io.github.spectordev9.opencode.ui.util.formatTokenCount

@Composable
fun ChatRoute(
    repository: ChatRepository,
    sessionId: String,
    initialTitle: String?,
    onBack: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat-$sessionId",
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    sessionId = sessionId,
                    initialTitle = initialTitle,
                    repository = repository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        state = state,
        onBack = onBack,
        onPromptChange = viewModel::onPromptChanged,
        onSend = viewModel::sendPrompt,
        onStop = viewModel::stop,
        onPermission = viewModel::answerPermission,
        onRefresh = viewModel::refresh,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPermission: (PermissionDecision) -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
) {
    val listState = rememberLazyListState()
    var initialScrollDone by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    val messages = state.messages
    val lastMessage = messages.lastOrNull()
    val lastContentKey = lastMessage?.parts?.joinToString("|") { part ->
        "${part.id}:${part.hashCode()}"
    }.orEmpty()

    LaunchedEffect(state.sessionId, messages.size, lastContentKey) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone) {
            listState.scrollToItem(messages.lastIndex)
            initialScrollDone = true
        } else {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= messages.lastIndex - 1) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to sessions",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.title?.takeIf { it.isNotBlank() } ?: "Chat",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.subtitle(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Reload messages",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                state.permission?.let { permission ->
                    PermissionCard(
                        permission = permission,
                        onDecision = onPermission,
                    )
                }
                Composer(
                    prompt = state.prompt,
                    enabled = !state.isLoading && !state.isStopping,
                    canSend = state.prompt.isNotBlank() &&
                        !state.isLoading &&
                        state.permission == null,
                    isStreaming = state.isStreaming,
                    isStopping = state.isStopping,
                    onPromptChange = onPromptChange,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
            if (messages.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Start a conversation",
                        body = "Ask OpenCode to explain, change, or test code in this workspace.",
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(messages, key = ChatMessageUi::id) { message ->
                        MessageItem(message)
                    }
                    if (state.isStreaming) {
                        item(key = "streaming") {
                            StreamingIndicator()
                        }
                    }
                }
            }
        }
    }
}

private fun ChatUiState.subtitle(): String {
    val usage = reportedUsage
    return when {
        permission != null -> "Permission required"
        isStopping -> "Stopping..."
        isStreaming -> "OpenCode is working..."
        usage != null -> buildString {
            usage.modelLabel?.let { append(it).append(" - ") }
            usage.reportedTokens?.let { append(formatTokenCount(it)).append(" tokens") }
            usage.cost?.let {
                if (isNotEmpty()) append(" - ")
                append(formatCost(it))
            }
        }.ifBlank { "OpenCode" }
        else -> if (isConnected) "Connected" else "Waiting for OpenCode"
    }
}

@Composable
private fun MessageItem(message: ChatMessageUi) {
    when (message.role) {
        ChatRole.USER -> UserMessage(message)
        ChatRole.ASSISTANT -> AssistantMessage(message)
        ChatRole.SYSTEM -> {
            SelectionContainer {
                Text(
                    text = message.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ChatRole.UNKNOWN -> {
            val text = message.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessageUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 4.dp,
            ),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message.parts.forEach { part ->
                    when (part) {
                        is TextPart -> SelectionContainer {
                            Text(
                                text = part.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        is FileAttachment -> AttachmentRow(part)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessageUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "OpenCode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            message.agent?.takeIf { it.isNotBlank() }?.let { LabelChip(text = it) }
            Spacer(modifier = Modifier.weight(1f))
            message.createdAtMillis?.let { createdAt ->
                Text(
                    text = formatRelativeTime(createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        message.parts.forEach { part ->
            when (part) {
                is TextPart -> MarkdownText(text = part.text)
                is Reasoning -> ReasoningBlock(part)
                is Tool -> ToolCard(part)
                is FileAttachment -> AttachmentRow(part)
                is Other -> Unit
            }
        }
        val footer = buildList {
            message.modelLabel?.let(::add)
            message.tokens?.total?.let { add("${formatTokenCount(it)} tokens") }
            message.cost?.let { add(formatCost(it)) }
        }
        if (footer.isNotEmpty()) {
            Text(
                text = footer.joinToString(" - "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReasoningBlock(part: Reasoning) {
    var expanded by remember(part.id) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Reasoning",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = if (expanded) "Collapse reasoning" else "Expand reasoning",
                    modifier = Modifier.size(20.dp),
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectionContainer {
                    Text(
                        text = part.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(part: Tool) {
    var expanded by remember(part.id) { mutableStateOf(false) }
    val statusColor: Color = when (part.status) {
        ToolRunStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ToolRunStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
        ToolRunStatus.ERROR -> MaterialTheme.colorScheme.error
        ToolRunStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Build,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = statusColor,
                )
                Text(
                    text = part.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = part.status.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
            part.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            part.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                val truncated = detail.length > 400
                SelectionContainer {
                    Text(
                        text = if (expanded || !truncated) detail else detail.take(400) + "...",
                        style = CodeTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (truncated) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text(if (expanded) "Show less" else "Show more")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(part: FileAttachment) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = part.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                part.mime?.let { mime ->
                    Text(
                        text = mime,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: PermissionUi,
    onDecision: (PermissionDecision) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Permission required",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (permission.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Text(
                text = permission.permission,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (permission.patterns.isNotEmpty()) {
                Text(
                    text = permission.patterns.joinToString(", "),
                    style = CodeTextStyle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onDecision(PermissionDecision.ALLOW_ONCE) },
                    enabled = !permission.busy,
                ) {
                    Text("Allow once")
                }
                FilledTonalButton(
                    onClick = { onDecision(PermissionDecision.ALLOW_ALWAYS) },
                    enabled = !permission.busy,
                ) {
                    Text("Always")
                }
                TextButton(
                    onClick = { onDecision(PermissionDecision.REJECT) },
                    enabled = !permission.busy,
                ) {
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun Composer(
    prompt: String,
    enabled: Boolean,
    canSend: Boolean,
    isStreaming: Boolean,
    isStopping: Boolean,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message OpenCode") },
                maxLines = 6,
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            )
            if (isStreaming) {
                FilledIconButton(
                    onClick = onStop,
                    enabled = !isStopping,
                ) {
                    if (isStopping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = "Stop response",
                        )
                    }
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send message",
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "OpenCode is working...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
