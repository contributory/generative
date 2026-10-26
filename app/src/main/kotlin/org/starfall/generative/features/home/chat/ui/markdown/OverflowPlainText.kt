package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/** Renders oversized plain-text overflow in bounded text nodes instead of one huge text layout. */
@Composable
internal fun OverflowPlainText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    expandToWidth: Boolean = true
) {
    val chunks = remember(text) { splitOverflowText(text) }
    Column(if (expandToWidth) modifier.fillMaxWidth() else modifier) {
        chunks.forEachIndexed { index, chunk ->
            key(index) {
                // The next Text node naturally starts on the following line. Remove one trailing
                // newline from non-final chunks to avoid introducing an extra blank line at seams.
                val displayedChunk = if (index < chunks.lastIndex && chunk.endsWith('\n')) {
                    chunk.dropLast(1)
                } else {
                    chunk
                }
                Text(
                    text = displayedChunk,
                    style = style,
                    modifier = if (expandToWidth) Modifier.fillMaxWidth() else Modifier
                )
            }
        }
    }
}

/** Splits at a nearby newline where possible and never separates a UTF-16 surrogate pair. */
internal fun splitOverflowText(
    text: String,
    maxChunkChars: Int = OVERFLOW_PLAIN_TEXT_CHUNK_CHARS
): List<String> {
    require(maxChunkChars > 0) { "maxChunkChars must be positive" }
    if (text.isEmpty()) return emptyList()

    val chunks = ArrayList<String>((text.length / maxChunkChars) + 1)
    var start = 0
    while (start < text.length) {
        val hardEnd = (start + maxChunkChars).coerceAtMost(text.length)
        var end = hardEnd

        if (hardEnd < text.length) {
            val newline = text.lastIndexOf('\n', hardEnd - 1)
            if (newline >= start) end = newline + 1
        }

        if (end < text.length && end > start &&
            text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()
        ) {
            end--
        }
        // A chunk size of one cannot fit a surrogate pair. Keep the pair together in that case.
        if (end == start) end = (start + 2).coerceAtMost(text.length)

        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}

private const val OVERFLOW_PLAIN_TEXT_CHUNK_CHARS = 500
