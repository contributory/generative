package org.starfall.generative.features.home.chat.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.shared.ui.text.GatedSelectionContainer

/** Bounded fallback for a single paragraph too large to annotate/layout as one Compose text node. */
@Composable
internal fun LargeMarkdownParagraph(
    source: String,
    startOffset: Int,
    endOffset: Int,
    style: TextStyle
) {
    val start = startOffset.coerceIn(0, source.length)
    val end = endOffset.coerceIn(start, source.length)
    val sourceLength = end - start
    var showDialog by remember(source, start, end) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember(source, start, end) {
        val previewEnd = safeSourceEnd(source, start, end, LARGE_PARAGRAPH_PREVIEW_CHARS)
        source.substring(start, previewEnd)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GatedSelectionContainer {
            Text(
                text = preview + if (sourceLength > preview.length) "…" else "",
                style = style,
                modifier = Modifier.fillMaxWidth()
            )
        }
        TextButton(onClick = { showDialog = true }) {
            Text(stringResource(R.string.expand))
        }
    }

    if (showDialog) {
        val chunks by produceState<List<String>>(initialValue = emptyList(), source, start, end) {
            value = withContext(Dispatchers.Default) {
                chunkLargeMarkdownParagraph(source, start, end)
            }
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.expand)) },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    GatedSelectionContainer {
                        LazyColumn(Modifier.padding(12.dp)) {
                            itemsIndexed(chunks, key = { index, _ -> index }) { _, chunk ->
                                Text(
                                    text = chunk,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        scope.launch {
                            val text = withContext(Dispatchers.Default) { source.substring(start, end) }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.common_copy), text))
                            Toast.makeText(context, context.getString(R.string.common_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.common_copy)) }
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_close)) }
                }
            }
        )
    }
}

internal fun chunkLargeMarkdownParagraph(text: String, chunkSize: Int = LARGE_PARAGRAPH_DIALOG_CHUNK_CHARS): List<String> =
    chunkLargeMarkdownParagraph(text, 0, text.length, chunkSize)

internal fun chunkLargeMarkdownParagraph(
    source: String,
    startOffset: Int,
    endOffset: Int,
    chunkSize: Int = LARGE_PARAGRAPH_DIALOG_CHUNK_CHARS
): List<String> {
    require(chunkSize > 0)
    val start = startOffset.coerceIn(0, source.length)
    val end = endOffset.coerceIn(start, source.length)
    val chunks = ArrayList<String>((end - start + chunkSize - 1) / chunkSize)
    var cursor = start
    while (cursor < end) {
        val next = safeSourceEnd(source, cursor, end, chunkSize)
        chunks += source.substring(cursor, next)
        cursor = next
    }
    return chunks
}

private fun safeSourceEnd(source: String, start: Int, limitEnd: Int, maxChars: Int): Int {
    var end = minOf(start + maxChars, limitEnd)
    if (end < limitEnd && end > start && source[end - 1].isHighSurrogate() && source[end].isLowSurrogate()) end--
    if (end == start && end < limitEnd) end = (end + 2).coerceAtMost(limitEnd)
    return end
}

internal const val LARGE_PARAGRAPH_EAGER_LIMIT = 20_000
private const val LARGE_PARAGRAPH_PREVIEW_CHARS = 4_000
private const val LARGE_PARAGRAPH_DIALOG_CHUNK_CHARS = 2_048
