package org.starfall.multigateway.shared.ui.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.starfall.multigateway.shared.code.highlightedCode

/** Small, feature-independent code preview for settings, storage and other non-chat screens. */
@Composable
fun SharedCodeBlock(language: String, code: String, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val highlighted = produceState(AnnotatedString(code), code, language, dark) {
        value = withContext(Dispatchers.Default) { highlightedCode(language, code, dark) }
    }.value
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp
    ) {
        Column {
            Text(
                language.ifBlank { "code" },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                highlighted,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Virtualized code surface for large tool responses; only visible chunks are highlighted and composed. */
@Composable
fun SharedLazyCodeBlock(language: String, code: String, modifier: Modifier = Modifier) {
    val starts by produceState<IntArray?>(null, code) {
        value = withContext(Dispatchers.Default) { sharedChunkStarts(code) }
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                language.ifBlank { "code" },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val chunks = starts
            if (chunks == null) {
                Text("Loading…", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    items(count = chunks.size, key = { it }) { index ->
                        val from = chunks[index]
                        val to = if (index + 1 < chunks.size) chunks[index + 1] else code.length
                        val chunk = remember(code, from, to) { code.substring(from, to) }
                        val highlighted = produceState(AnnotatedString(chunk), chunk, language, dark) {
                            value = withContext(Dispatchers.Default) { highlightedCode(language, chunk, dark) }
                        }.value
                        Text(
                            highlighted,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun sharedChunkStarts(text: String, maxLines: Int = 80, maxChars: Int = 4_000): IntArray {
    val starts = ArrayList<Int>()
    var start = 0
    while (start < text.length) {
        starts.add(start)
        val limit = minOf(text.length, start + maxChars)
        var index = start
        var lines = 0
        var lastBreak = -1
        while (index < limit) {
            if (text[index] == '\n') {
                lines++
                lastBreak = index + 1
                if (lines >= maxLines) break
            }
            index++
        }
        start = when {
            lines >= maxLines -> lastBreak
            limit == text.length -> text.length
            lastBreak > start -> lastBreak
            else -> limit
        }
    }
    return starts.toIntArray()
}

