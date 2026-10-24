package io.github.spectordev9.opencode.feature.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.spectordev9.opencode.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal")
                        Text(
                            text = "Blocked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EmptyState(
                icon = Icons.Outlined.Code,
                title = "Terminal is not available yet",
                body = "A real terminal needs an authenticated PTY session from the OpenCode runtime. This screen does not simulate commands or echo input.",
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "What is required",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Requirement("An authenticated, runtime-owned PTY session.")
                    Requirement("Explicit lifecycle and teardown owned by the runtime.")
                    Requirement("A keyboard and resize protocol that does not leak credentials.")
                    Requirement("No local shell fallback: it is not the same trust boundary.")
                }
            }
        }
    }
}

@Composable
private fun Requirement(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
