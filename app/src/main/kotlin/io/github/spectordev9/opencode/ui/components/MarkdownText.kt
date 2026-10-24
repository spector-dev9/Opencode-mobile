package io.github.spectordev9.opencode.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.spectordev9.opencode.ui.theme.CodeTextStyle
import io.github.spectordev9.opencode.ui.util.MarkdownBlock
import io.github.spectordev9.opencode.ui.util.MarkdownLite

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is MarkdownBlock.Code -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (block.language != null) {
                                Text(
                                    text = block.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(
                                        start = 12.dp,
                                        top = 8.dp,
                                        end = 12.dp,
                                    ),
                                )
                            }
                            SelectionContainer {
                                Text(
                                    text = block.code,
                                    style = CodeTextStyle,
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
