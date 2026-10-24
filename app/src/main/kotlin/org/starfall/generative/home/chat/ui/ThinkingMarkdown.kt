package org.starfall.generative.home.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.starfall.generative.home.chat.ui.markdown.MarkdownRenderer

/** Thinking uses the full message Markdown pipeline while retaining its quieter text color. */
@Composable
internal fun ThinkingMarkdown(
    reasoning: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val thinkingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            onSurface = thinkingColor,
            onSurfaceVariant = thinkingColor
        )
    ) {
        MarkdownRenderer(
            content = reasoning,
            modifier = modifier,
            isStreaming = isStreaming,
            trimLeadingBlockSpace = true
        )
    }
}
