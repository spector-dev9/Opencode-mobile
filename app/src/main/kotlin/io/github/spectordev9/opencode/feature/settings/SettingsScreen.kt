package io.github.spectordev9.opencode.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.spectordev9.opencode.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    isDemo: Boolean,
    versionName: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSection(title = "Appearance") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            ThemeModeRow(
                                mode = mode,
                                selected = mode == themeMode,
                                onClick = { onThemeModeChange(mode) },
                            )
                            if (index != ThemeMode.entries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection(title = "Runtime") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDemo) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = if (isDemo) {
                                Icons.Outlined.Science
                            } else {
                                Icons.Outlined.Info
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isDemo) "Demo data" else "Runtime not connected",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (isDemo) {
                                    "No OpenCode runtime is attached. Sessions, messages, and files are simulated in this debug build."
                                } else {
                                    "Production wiring is pending the runtime contract freeze (G0). This build does not fabricate chat data."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            SettingsSection(title = "Privacy and safety") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column {
                        InfoRow(
                            icon = Icons.Outlined.Lock,
                            title = "Credentials stay out of the UI",
                            body = "Secrets and loopback tokens are owned by the runtime, never stored in preferences.",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        InfoRow(
                            icon = Icons.Outlined.Visibility,
                            title = "Read-only file preview",
                            body = "Workspace files are fetched for preview at a 64 KiB cap. The UI does not edit files.",
                        )
                    }
                }
            }

            SettingsSection(title = "About") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Version",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = versionName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenLicenses)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Open source licenses",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        content()
    }
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = when (mode) {
                ThemeMode.SYSTEM -> "Follow system"
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DARK -> "Dark"
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
