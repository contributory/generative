package org.starfall.multigateway.ui.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.starfall.multigateway.data.local.preferences.WordWrapMode

private const val LAZY_CHUNK_LINES = 80
private const val LAZY_CHUNK_CHARS = 4_000

/**
 * Start offsets of consecutive chunks of [text]. Only offsets are kept (no substring copies), a chunk
 * ends at a line break once it holds [maxLines] lines or [maxChars] characters, and a single line longer
 * than [maxChars] is split. One linear pass, so it stays cheap for very large inputs.
 */
internal fun chunkStarts(text: String, maxLines: Int = LAZY_CHUNK_LINES, maxChars: Int = LAZY_CHUNK_CHARS): IntArray {
    val starts = ArrayList<Int>()
    var start = 0
    while (start < text.length) {
        starts.add(start)
        val limit = minOf(text.length, start + maxChars)
        var i = start
        var lines = 0
        var lastBreak = -1
        while (i < limit) {
            if (text[i] == '\n') {
                lines++
                lastBreak = i + 1
                if (lines >= maxLines) break
            }
            i++
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

/**
 * Code surface for texts that may be huge: only the chunks on screen are composed (and highlighted),
 * and the header, font size, highlighting and word-wrap rules are the same as [CodeBlockSurface].
 */
@Composable
internal fun LazyCodeBlock(
    language: String,
    text: String,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null
) {
    val preferences = LocalCodeRenderingPreferences.current
    var sessionWrap by remember(text) { mutableStateOf(false) }
    val mode = if (preferences.wordWrapMode == WordWrapMode.OFF && sessionWrap) WordWrapMode.VIEWPORT else preferences.wordWrapMode
    val textStyle = codeTextStyle()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val characterWidth = remember(textStyle, density) {
        (measurer.measure("0".repeat(100), textStyle, softWrap = false).size.width / 100f).coerceAtLeast(1f)
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val starts by produceState<IntArray?>(null, text) {
        value = withContext(Dispatchers.Default) { chunkStarts(text) }
    }
    val horizontalState = rememberScrollState()
    val listState = rememberLazyListState()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            CodeBlockHeader(
                language = language,
                isStreaming = false,
                originalCode = text,
                sessionWrap = sessionWrap,
                onToggleWrap = { sessionWrap = !sessionWrap },
                onDownload = onDownload
            )
            val chunks = starts
            if (chunks == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val viewport = with(density) { (maxWidth - 24.dp).coerceAtLeast(1.dp).toPx() }
                    val target = codeWrapWidth(mode, viewport, (characterWidth * preferences.wordWrapColumn).coerceAtMost(200_000f))
                    val bodyModifier = if (target == null) Modifier else Modifier.width(with(density) { target.toDp() } + 24.dp)
                    val scrollsHorizontally = mode == WordWrapMode.OFF || mode == WordWrapMode.COLUMN
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxHeight()
                            .then(if (scrollsHorizontally) Modifier.horizontalScroll(horizontalState) else Modifier.fillMaxWidth())
                    ) {
                        items(count = chunks.size, key = { it }) { index ->
                            val from = chunks[index]
                            val to = if (index + 1 < chunks.size) chunks[index + 1] else text.length
                            val chunk = remember(text, from, to) { text.substring(from, to) }
                            val highlighted by produceState(AnnotatedString(chunk), chunk, language, dark) {
                                value = withContext(Dispatchers.Default) { highlightedCode(language, chunk, dark) }
                            }
                            Text(
                                highlighted,
                                style = textStyle,
                                softWrap = mode != WordWrapMode.OFF,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = bodyModifier.padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = if (index == 0) 12.dp else 0.dp,
                                    bottom = if (index == chunks.lastIndex) 12.dp else 0.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
