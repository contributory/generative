package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import org.starfall.generative.shared.ui.text.GatedSelectionContainer

/** Full-source fallback for a pathological paragraph too expensive to annotate as Markdown. */
@Composable
internal fun LargeMarkdownParagraph(
    source: String,
    startOffset: Int,
    endOffset: Int,
    style: TextStyle
) {
    val start = startOffset.coerceIn(0, source.length)
    val end = endOffset.coerceIn(start, source.length)
    val text = remember(source, start, end) { source.substring(start, end) }
    GatedSelectionContainer {
        Text(text = text, style = style, modifier = Modifier.fillMaxWidth())
    }
}

internal const val LARGE_PARAGRAPH_EAGER_LIMIT = 20_000
