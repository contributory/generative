package org.starfall.generative.features.home.chat.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.shared.storage.preferences.CodeBlockDisplayMode
import org.starfall.generative.shared.ui.code.CodeBlockSurface
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.code.codeTextStyle
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.nestedContainerBorder
import org.starfall.generative.shared.ui.layout.nestedContainerColor

private const val MAX_INLINE_CODE_CHARS = 16_000
private const val MAX_INLINE_CODE_LINES = 600
private const val PREVIEW_CODE_CHARS = 1_600
private const val MAX_VISIBLE_CODE_LINE_CHARS = 2_000

/** Chat-only code block logic: long-block display modes plus a bounded fallback for very large fences. */
@Composable
internal fun RenderCodeBlock(
    language: String,
    source: String,
    startOffset: Int,
    endOffset: Int,
    isStreaming: Boolean,
    trimTrailingWhitespace: Boolean = false
) {
    val start = startOffset.coerceIn(0, source.length)
    val end = adjustedCodeEnd(source, start, endOffset, trimTrailingWhitespace)
    val codeLength = end - start
    val initialLineCount = remember(source, start, end, isStreaming) {
        if (isStreaming) null else if (codeLength <= MAX_INLINE_CODE_CHARS) countLines(source, start, end) else null
    }
    val lineCount by produceState<Int?>(initialLineCount, source, start, end, isStreaming) {
        if (!isStreaming && value == null) value = withContext(Dispatchers.Default) { countLines(source, start, end) }
    }
    var expanded by remember(language, source, start, end) { mutableStateOf(false) }
    var drawerVisible by remember(language, source, start, end) { mutableStateOf(false) }

    if (isHugeCode(codeLength, lineCount)) {
        RenderHugeCodeBlock(
            language = language,
            source = source,
            start = start,
            end = end,
            lineCount = lineCount,
            isStreaming = isStreaming,
            drawerVisible = drawerVisible,
            onOpen = { drawerVisible = true },
            onClose = { drawerVisible = false }
        )
        return
    }
    RenderConfiguredCodeBlock(
        language = language,
        source = source,
        start = start,
        end = end,
        lineCount = lineCount,
        isStreaming = isStreaming,
        expanded = expanded,
        drawerVisible = drawerVisible,
        onExpandedChange = { expanded = it },
        onOpen = { drawerVisible = true },
        onClose = { drawerVisible = false }
    )
}

private fun adjustedCodeEnd(source: String, start: Int, endOffset: Int, trimTrailingWhitespace: Boolean): Int {
    var end = endOffset.coerceIn(start, source.length)
    if (trimTrailingWhitespace) {
        while (end > start && source[end - 1].isWhitespace()) end--
    }
    return end
}

private fun isHugeCode(codeLength: Int, lineCount: Int?): Boolean =
    codeLength > MAX_INLINE_CODE_CHARS || (lineCount ?: 0) > MAX_INLINE_CODE_LINES

@Composable
private fun RenderHugeCodeBlock(
    language: String,
    source: String,
    start: Int,
    end: Int,
    lineCount: Int?,
    isStreaming: Boolean,
    drawerVisible: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    HugeCodeBlockPreview(
        language = language,
        source = source,
        startOffset = start,
        endOffset = end,
        lineCount = lineCount,
        isStreaming = isStreaming,
        onCopy = {
            scope.launch {
                val code = withContext(Dispatchers.Default) { source.substring(start, end) }
                copyCode(context, code)
            }
        },
        onOpen = onOpen
    )
    if (drawerVisible) HugeCodeBlockDialog(language, source, start, end, onDismiss = onClose)
}

@Composable
private fun RenderConfiguredCodeBlock(
    language: String,
    source: String,
    start: Int,
    end: Int,
    lineCount: Int?,
    isStreaming: Boolean,
    expanded: Boolean,
    drawerVisible: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val preferences = LocalCodeRenderingPreferences.current
    val code = remember(source, start, end) { source.substring(start, end) }
    val codeLanguageFallback = stringResource(R.string.code_language_fallback)
    val isLong = isLongCodeBlock(language, isStreaming, lineCount, preferences)
    val displayMode = preferences.codeBlockDisplayMode
    if (displayMode == CodeBlockDisplayMode.SHOW_IN_DRAWER && isLong) {
        LongCodeBlockDrawerCard(language, codeLanguageFallback, lineCount, onOpen)
        if (drawerVisible) {
            CodeBlockDialog(
                language = language,
                code = code,
                isStreaming = isStreaming,
                onDismiss = onClose
            )
        }
        return
    }
    RenderInlineCodeBlock(
        language = language,
        code = code,
        isStreaming = isStreaming,
        displayMode = displayMode,
        maxLines = preferences.codeBlockMaxLines,
        isLong = isLong,
        expanded = expanded,
        onExpandedChange = onExpandedChange
    )
}

private fun isLongCodeBlock(
    language: String,
    isStreaming: Boolean,
    lineCount: Int?,
    preferences: org.starfall.generative.shared.storage.preferences.AppPreferences
): Boolean = !isStreaming &&
    (preferences.codeBlockIncludeUndefined || language.isNotBlank()) &&
    lineCount.orZero() > preferences.codeBlockMaxLines

@Composable
private fun LongCodeBlockDrawerCard(
    language: String,
    languageFallback: String,
    lineCount: Int?,
    onOpen: () -> Unit
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(10.dp),
        color = nestedContainerColor(),
        border = nestedContainerBorder(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.code_block_summary, language.ifBlank { languageFallback }, lineCount.orZero()),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun RenderInlineCodeBlock(
    language: String,
    code: String,
    isStreaming: Boolean,
    displayMode: CodeBlockDisplayMode,
    maxLines: Int,
    isLong: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val visibleCode = if (displayMode == CodeBlockDisplayMode.EXTENDABLE && isLong && !expanded) {
        code.lineSequence().take(maxLines).joinToString("\n")
    } else code
    CodeBlockSurface(
        language = language,
        code = visibleCode,
        modifier = Modifier.padding(vertical = 4.dp),
        originalCode = code,
        isStreaming = isStreaming,
        onExpansionChange = if (displayMode == CodeBlockDisplayMode.EXTENDABLE && isLong) {
            { onExpandedChange(!expanded) }
        } else null,
        expanded = expanded
    )
}

@Composable
private fun HugeCodeBlockPreview(
    language: String,
    source: String,
    startOffset: Int,
    endOffset: Int,
    lineCount: Int?,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    val fallback = stringResource(R.string.code_language_fallback)
    val copyLabel = stringResource(R.string.code_copy)
    val summary = lineCount?.let {
        stringResource(R.string.code_block_summary, language.ifBlank { fallback }, it)
    } ?: stringResource(R.string.code_block_extendable)
    val previewStart = if (isStreaming) (endOffset - PREVIEW_CODE_CHARS).coerceAtLeast(startOffset) else startOffset
    val previewEnd = safeCodeSourceEnd(source, previewStart, endOffset, PREVIEW_CODE_CHARS)
    val preview = source.substring(previewStart, previewEnd)
    Surface(
        onClick = { if (!isStreaming) onOpen() },
        shape = RoundedCornerShape(10.dp),
        color = nestedContainerColor(),
        border = nestedContainerBorder(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    FluentIcon(Icons.Outlined.ContentCopy, copyLabel, modifier = Modifier.size(16.dp))
                }
            }
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    maxLines = 8,
                    style = codeTextStyle(),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpen, enabled = !isStreaming, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.code_block_extend))
            }
        }
    }
}

@Composable
private fun VirtualizedCodeBlockDialog(
    language: String,
    source: String,
    startOffset: Int,
    endOffset: Int,
    isStreaming: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(language.ifBlank { "code" }, Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                    IconButton(onClick = {
                        scope.launch {
                            val code = withContext(Dispatchers.Default) { source.substring(startOffset, endOffset) }
                            copyCode(context, code)
                        }
                    }) {
                        FluentIcon(Icons.Outlined.ContentCopy, stringResource(R.string.code_copy))
                    }
                    IconButton(onClick = onDismiss) {
                        FluentIcon(Icons.Outlined.Close, stringResource(R.string.code_block_close))
                    }
                }
                if (isStreaming) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                VirtualizedCodeLines(source, startOffset, endOffset, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HugeCodeBlockDialog(
    language: String,
    source: String,
    startOffset: Int,
    endOffset: Int,
    onDismiss: () -> Unit
) {
    VirtualizedCodeBlockDialog(
        language = language,
        source = source,
        startOffset = startOffset,
        endOffset = endOffset,
        isStreaming = false,
        onDismiss = onDismiss
    )
}

@Composable
private fun CodeBlockDialog(
    language: String,
    code: String,
    isStreaming: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(language.ifBlank { "code" }, Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                    IconButton(onClick = onDismiss) {
                        FluentIcon(Icons.Outlined.Close, stringResource(R.string.code_block_close))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    CodeBlockSurface(
                        language = language,
                        code = code,
                        modifier = Modifier.padding(vertical = 4.dp),
                        originalCode = code,
                        isStreaming = isStreaming,
                        onExpansionChange = null,
                        expanded = true
                    )
                }
            }
        }
    }
}

/** Only visible/prefetched code lines are composed; pathological individual lines are safely abbreviated. */
@Composable
private fun VirtualizedCodeLines(
    source: String,
    startOffset: Int,
    endOffset: Int,
    modifier: Modifier = Modifier
) {
    val lineStarts by produceState(initialValue = emptyList<Int>(), source, startOffset, endOffset) {
        value = withContext(Dispatchers.Default) { findLineStarts(source, startOffset, endOffset) }
    }
    LazyColumn(modifier) {
        itemsIndexed(lineStarts, key = { index, _ -> index }) { index, start ->
            val nextStart = lineStarts.getOrNull(index + 1)
            val endExclusive = (nextStart?.minus(1) ?: endOffset).coerceAtLeast(start)
            val fullLineLength = endExclusive - start
            val visibleEnd = safeCodeSourceEnd(source, start, endExclusive, MAX_VISIBLE_CODE_LINE_CHARS)
            val line = source.substring(start, visibleEnd).trimEnd('\r')
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)) {
                Text(
                    text = line + if (fullLineLength > MAX_VISIBLE_CODE_LINE_CHARS) " …" else "",
                    style = codeTextStyle(),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun countLines(source: String, startOffset: Int, endOffset: Int): Int {
    val start = startOffset.coerceIn(0, source.length)
    val end = endOffset.coerceIn(start, source.length)
    if (end <= start) return 0
    var lineBreaks = 0
    for (index in start until end) if (source[index] == '\n') lineBreaks++
    return lineBreaks + if (source[end - 1] == '\n') 0 else 1
}

private fun Int?.orZero(): Int = this ?: 0

private fun findLineStarts(source: String, startOffset: Int, endOffset: Int): List<Int> {
    val start = startOffset.coerceIn(0, source.length)
    val end = endOffset.coerceIn(start, source.length)
    if (end <= start) return emptyList()
    val starts = ArrayList<Int>()
    starts += start
    for (index in start until end - 1) {
        if (source[index] == '\n') starts += index + 1
    }
    return starts
}

private fun safeCodeSourceEnd(source: String, start: Int, limitEnd: Int, maxChars: Int): Int {
    var end = minOf(start + maxChars, limitEnd)
    if (end < limitEnd && end > start && source[end - 1].isHighSurrogate() && source[end].isLowSurrogate()) end--
    if (end == start && end < limitEnd) end = (end + 2).coerceAtMost(limitEnd)
    return end
}

private fun copyCode(context: Context, code: String) {
    val result = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.code_copy), code))
    }
    Toast.makeText(
        context,
        context.getString(if (result.isSuccess) R.string.code_copied else R.string.code_too_large_to_copy),
        Toast.LENGTH_SHORT
    ).show()
}
