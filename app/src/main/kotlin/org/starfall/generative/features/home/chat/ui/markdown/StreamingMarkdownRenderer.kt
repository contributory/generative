package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import org.starfall.generative.features.home.chat.ui.MarkdownParseCache
import org.starfall.generative.features.home.chat.ui.TrackPendingWork
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.text.LocalStreamingTextFade
import org.starfall.generative.shared.ui.text.STREAM_FADE_DURATION_MS
import org.starfall.generative.shared.ui.text.messageMarkdownTypography

@Composable
internal fun StreamingMarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = true,
    streamSnapshot: StreamTextSnapshot? = null,
    streamGenerationId: Long? = null,
    streamMessageId: String? = null,
    cacheIdentity: MarkdownRenderCacheIdentity? = null
) {
    val truncationLabel = stringResource(R.string.chat_response_truncated)
    if (isStreaming && streamSnapshot != null && streamGenerationId != null && streamMessageId != null) {
        IncrementalStreamingMarkdown(
            streamSnapshot = streamSnapshot,
            generationId = streamGenerationId,
            messageId = streamMessageId,
            modifier = modifier,
            cacheIdentity = cacheIdentity
        )
        return
    }

    val latestContent = rememberUpdatedState(content)
    val displayContent = remember(streamGenerationId, streamMessageId, cacheIdentity) { mutableStateOf(content) }
    val oversizedPreview = remember(streamGenerationId, streamMessageId, cacheIdentity) { mutableStateOf<String?>(null) }
    val finishingFade = remember { mutableStateOf(isStreaming) }
    val settled = remember { mutableStateOf(!isStreaming) }
    ObserveOversizedStreamingPreview(
        latestContent = latestContent,
        truncationLabel = truncationLabel,
        streamGenerationId = streamGenerationId,
        streamMessageId = streamMessageId,
        cacheIdentity = cacheIdentity,
        onPreview = { oversizedPreview.value = it }
    )
    ObserveStreamingMarkdownLifecycle(
        isStreaming = isStreaming,
        content = content,
        latestContent = latestContent,
        displayContent = displayContent,
        finishingFade = finishingFade,
        settled = settled,
        cacheIdentity = cacheIdentity
    )
    RenderStreamingMarkdown(
        displayContent = displayContent.value,
        oversizedPreview = oversizedPreview.value,
        truncationLabel = truncationLabel,
        modifier = modifier,
        isStreaming = isStreaming,
        finishingFade = finishingFade.value,
        settled = settled.value,
        cacheIdentity = cacheIdentity,
        streamGenerationId = streamGenerationId
    )
}

@Composable
private fun ObserveOversizedStreamingPreview(
    latestContent: androidx.compose.runtime.State<String>,
    truncationLabel: String,
    streamGenerationId: Long?,
    streamMessageId: String?,
    cacheIdentity: MarkdownRenderCacheIdentity?,
    onPreview: (String) -> Unit
) {
    LaunchedEffect(streamGenerationId, streamMessageId, cacheIdentity) {
        var previewCreated = false
        snapshotFlow { latestContent.value }.conflate().collect { input ->
            if (!previewCreated && input.length > MAX_MARKDOWN_RENDER_CHARS) {
                previewCreated = true
                val preview = withContext(Dispatchers.Default) {
                    input.take(MAX_MARKDOWN_RENDER_CHARS) + "\n\n" + truncationLabel
                }
                onPreview(preview)
            }
        }
    }
}

@Composable
private fun ObserveStreamingMarkdownLifecycle(
    isStreaming: Boolean,
    content: String,
    latestContent: androidx.compose.runtime.State<String>,
    displayContent: MutableState<String>,
    finishingFade: MutableState<Boolean>,
    settled: MutableState<Boolean>,
    cacheIdentity: MarkdownRenderCacheIdentity?
) {
    LaunchedEffect(isStreaming, if (isStreaming) null else content) {
        if (isStreaming) {
            settled.value = false
            finishingFade.value = true
            collectStreamingMarkdownChunks(latestContent, displayContent)
        } else {
            displayContent.value = latestContent.value
            if (finishingFade.value) {
                delay(STREAM_FADE_DURATION_MS + 32)
                finishingFade.value = false
            }
            if (!settled.value) {
                val finalText = latestContent.value
                cacheFinalMarkdownIfNeeded(finalText, cacheIdentity)
                displayContent.value = finalText
                settled.value = true
            }
        }
    }
}

private suspend fun collectStreamingMarkdownChunks(
    latestContent: androidx.compose.runtime.State<String>,
    displayContent: MutableState<String>
) {
    snapshotFlow { latestContent.value }.conflate().collect {
        delay(64)
        val latest = latestContent.value
        if (latest.length <= MAX_MARKDOWN_RENDER_CHARS) displayContent.value = latest
    }
}

private suspend fun cacheFinalMarkdownIfNeeded(
    finalText: String,
    cacheIdentity: MarkdownRenderCacheIdentity?
) {
    if (finalText.length > MAX_MARKDOWN_RENDER_CHARS || MarkdownParseCache.get(finalText, cacheIdentity) != null) return
    val (state, fingerprint) = withContext(Dispatchers.Default) {
        val startedAt = System.nanoTime()
        val parsedState = try {
            parseLibraryMarkdown(finalText)
        } finally {
            MarkdownParseCache.recordFullParse(finalText.length, System.nanoTime() - startedAt)
        }
        parsedState to if (parsedState is State.Success) markdownContentFingerprint(finalText) else null
    }
    MarkdownParseCache.put(finalText, state, cacheIdentity, fingerprint)
}

@Composable
private fun RenderStreamingMarkdown(
    displayContent: String,
    oversizedPreview: String?,
    truncationLabel: String,
    modifier: Modifier,
    isStreaming: Boolean,
    finishingFade: Boolean,
    settled: Boolean,
    cacheIdentity: MarkdownRenderCacheIdentity?,
    streamGenerationId: Long?
) {
    CompositionLocalProvider(LocalStreamingTextFade provides (isStreaming || finishingFade)) {
        when {
            // This is only a live-stream safety preview. Once generation stops, let the complete
            // source reach MarkdownRenderer, which falls back to bounded plain-text nodes if needed.
            isStreaming && oversizedPreview != null -> StreamingMarkdownPreview(oversizedPreview, modifier)
            isStreaming && displayContent.length > MAX_MARKDOWN_RENDER_CHARS -> StreamingMarkdownPreview(
                displayContent.take(MAX_MARKDOWN_RENDER_CHARS) + "\n\n" + truncationLabel,
                modifier
            )
            else -> RenderStreamingMarkdownDocument(
                displayContent = displayContent,
                modifier = modifier,
                isStreaming = isStreaming,
                settled = settled,
                cacheIdentity = cacheIdentity,
                streamGenerationId = streamGenerationId
            )
        }
    }
}

@Composable
private fun StreamingMarkdownPreview(text: String, modifier: Modifier) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RenderStreamingMarkdownDocument(
    displayContent: String,
    modifier: Modifier,
    isStreaming: Boolean,
    settled: Boolean,
    cacheIdentity: MarkdownRenderCacheIdentity?,
    streamGenerationId: Long?
) {
    if (settled && !isStreaming) {
        MarkdownRenderer(
            content = displayContent,
            modifier = modifier,
            isStreaming = isStreaming,
            cacheIdentity = cacheIdentity
        )
    } else {
        ChunkedStreamingMarkdown(
            content = displayContent,
            modifier = modifier,
            cacheIdentity = cacheIdentity,
            streamGenerationId = streamGenerationId
        )
    }
}

@Composable
private fun IncrementalStreamingMarkdown(
    streamSnapshot: StreamTextSnapshot,
    generationId: Long,
    messageId: String,
    modifier: Modifier,
    cacheIdentity: MarkdownRenderCacheIdentity?
) {
    val initialBaseText = streamSnapshot.baseText
    val truncationLabel = stringResource(R.string.chat_response_truncated)
    key(generationId, messageId, initialBaseText) {
        val cancellationToken = remember { MarkdownParserCancellationToken() }
        val incrementalParser = remember { IncrementalMarkdownParser(cancellationToken) }
        val latestSnapshot = rememberUpdatedState(streamSnapshot)
        val renderSnapshot = remember { mutableStateOf(incrementalParser.currentSnapshot()) }
        val truncatedContent = remember {
            mutableStateOf(initialTruncatedContent(streamSnapshot, truncationLabel))
        }
        val parsing = remember { mutableStateOf(false) }
        TrackPendingWork(parsing.value)
        CollectIncrementalMarkdownStream(
            initialBaseText = initialBaseText,
            latestSnapshot = latestSnapshot,
            parser = incrementalParser,
            cancellationToken = cancellationToken,
            renderSnapshot = renderSnapshot,
            truncatedContent = truncatedContent,
            parsing = parsing,
            truncationLabel = truncationLabel
        )
        RenderIncrementalMarkdown(
            parser = incrementalParser,
            renderSnapshot = renderSnapshot.value,
            latestSnapshot = latestSnapshot,
            truncatedContent = truncatedContent.value,
            truncationLabel = truncationLabel,
            modifier = modifier,
            cacheIdentity = cacheIdentity
        )
    }
}

private fun initialTruncatedContent(snapshot: StreamTextSnapshot, truncationLabel: String): String? =
    snapshot.takeIf { it.length > MAX_MARKDOWN_RENDER_CHARS.toLong() }
        ?.prefix(MAX_MARKDOWN_RENDER_CHARS)
        ?.let { "$it\n\n$truncationLabel" }

@Composable
private fun CollectIncrementalMarkdownStream(
    initialBaseText: String,
    latestSnapshot: androidx.compose.runtime.State<StreamTextSnapshot>,
    parser: IncrementalMarkdownParser,
    cancellationToken: MarkdownParserCancellationToken,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    truncatedContent: MutableState<String?>,
    parsing: MutableState<Boolean>,
    truncationLabel: String
) {
    LaunchedEffect(Unit) {
        cancellationToken.ownerJob = currentCoroutineContext()[Job]
        try {
            if (truncatedContent.value != null) return@LaunchedEffect
            appendInitialMarkdownBase(initialBaseText, parser, renderSnapshot, parsing)
            collectIncrementalMarkdownUpdates(
                latestSnapshot = latestSnapshot,
                parser = parser,
                renderSnapshot = renderSnapshot,
                truncatedContent = truncatedContent,
                parsing = parsing,
                truncationLabel = truncationLabel
            )
        } finally {
            cancellationToken.ownerJob = null
        }
    }
}

private suspend fun appendInitialMarkdownBase(
    initialBaseText: String,
    parser: IncrementalMarkdownParser,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>
) {
    val initialContent = initialBaseText.take(MAX_MARKDOWN_RENDER_CHARS)
    if (initialContent.isEmpty()) return
    appendIncrementalMarkdown(parser, initialContent, StreamTextSnapshot.of(initialBaseText), renderSnapshot, parsing)
}

private suspend fun collectIncrementalMarkdownUpdates(
    latestSnapshot: androidx.compose.runtime.State<StreamTextSnapshot>,
    parser: IncrementalMarkdownParser,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    truncatedContent: MutableState<String?>,
    parsing: MutableState<Boolean>,
    truncationLabel: String
) {
    var lastSequence = 0L
    if (latestSnapshot.value.length > MAX_MARKDOWN_RENDER_CHARS.toLong()) {
        truncatedContent.value = truncatedMarkdownText(latestSnapshot.value, truncationLabel)
        return
    }
    snapshotFlow { latestSnapshot.value }.conflate().collect { current ->
        if (truncatedContent.value != null) return@collect
        if (current.length > MAX_MARKDOWN_RENDER_CHARS.toLong()) {
            truncatedContent.value = truncatedMarkdownText(current, truncationLabel)
            return@collect
        }
        if (renderSnapshot.value.overflowStart != null) {
            lastSequence = current.latestSegment?.sequence ?: lastSequence
            return@collect
        }
        appendNewMarkdownSegments(current, lastSequence, parser, renderSnapshot, parsing).also {
            lastSequence = it
        }
    }
}

private suspend fun appendNewMarkdownSegments(
    current: StreamTextSnapshot,
    previousSequence: Long,
    parser: IncrementalMarkdownParser,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>
): Long {
    var lastSequence = previousSequence
    for (segment in current.segmentsAfter(previousSequence)) {
        lastSequence = segment.sequence
        val remaining = (MAX_MARKDOWN_RENDER_CHARS - renderSnapshot.value.parsedLength).coerceAtLeast(0)
        val accepted = segment.text.take(remaining)
        if (accepted.isEmpty()) continue
        appendIncrementalMarkdown(parser, accepted, current, renderSnapshot, parsing)
        if (renderSnapshot.value.overflowStart != null || accepted.length < segment.text.length) break
    }
    return lastSequence
}

private suspend fun appendIncrementalMarkdown(
    parser: IncrementalMarkdownParser,
    text: String,
    source: StreamTextSnapshot,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>
) {
    parsing.value = true
    try {
        val result = withContext(Dispatchers.Default) {
            parser.append(
                text = text,
                source = source,
                maxUnstableTailChars = MAX_INCREMENTAL_UNSTABLE_TAIL_CHARS,
                maxParserAppendChars = MAX_INCREMENTAL_PARSER_APPEND_CHARS
            )
        }
        result.linkUpdates.forEach { (label, destination) ->
            parser.referenceLinkHandler.store(label, destination)
        }
        renderSnapshot.value = result.snapshot
    } finally {
        parsing.value = false
    }
}

private fun truncatedMarkdownText(snapshot: StreamTextSnapshot, truncationLabel: String): String =
    snapshot.prefix(MAX_MARKDOWN_RENDER_CHARS) + "\n\n" + truncationLabel

@Composable
private fun RenderIncrementalMarkdown(
    parser: IncrementalMarkdownParser,
    renderSnapshot: IncrementalMarkdownSnapshot,
    latestSnapshot: androidx.compose.runtime.State<StreamTextSnapshot>,
    truncatedContent: String?,
    truncationLabel: String,
    modifier: Modifier,
    cacheIdentity: MarkdownRenderCacheIdentity?
) {
    val preferences = LocalCodeRenderingPreferences.current
    val typography = messageMarkdownTypography()
    val renderOptionsKey = 31 * preferences.hashCode() +
        17 * preferences.latexMode.hashCode() + 7 * true.hashCode() + true.hashCode()
    CompositionLocalProvider(LocalStreamingTextFade provides true) {
        if (truncatedContent != null) {
            StreamingMarkdownPreview(truncatedContent, modifier)
        } else {
            RenderIncrementalMarkdownDocument(
                parser = parser,
                renderSnapshot = renderSnapshot,
                latestSnapshot = latestSnapshot,
                truncationLabel = truncationLabel,
                modifier = modifier,
                cacheIdentity = cacheIdentity,
                preferences = preferences,
                typography = typography,
                renderOptionsKey = renderOptionsKey
            )
        }
    }
}

@Composable
private fun RenderIncrementalMarkdownDocument(
    parser: IncrementalMarkdownParser,
    renderSnapshot: IncrementalMarkdownSnapshot,
    latestSnapshot: androidx.compose.runtime.State<StreamTextSnapshot>,
    truncationLabel: String,
    modifier: Modifier,
    cacheIdentity: MarkdownRenderCacheIdentity?,
    preferences: org.starfall.generative.shared.storage.preferences.AppPreferences,
    typography: MarkdownTypography,
    renderOptionsKey: Int
) {
    Markdown(
        state = State.Success(
            node = parser.file,
            // This opaque revision key drives Compose updates; rendering slices source from the latest snapshot.
            content = renderSnapshot.revision.toString(),
            linksLookedUp = true,
            referenceLinkHandler = parser.referenceLinkHandler
        ),
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(),
        typography = typography,
        padding = markdownPadding(block = MARKDOWN_BLOCK_PADDING),
        components = chatMarkdownComponents(
            isStreaming = true,
            latexMode = preferences.latexMode,
            fillMaxWidth = true,
            cacheIdentity = cacheIdentity,
            renderOptionsKey = renderOptionsKey
        ),
        success = { _, components, successModifier ->
            VirtualizedIncrementalMarkdown(
                stableNodes = renderSnapshot.stableNodes,
                unstableNodes = renderSnapshot.unstableNodes,
                source = latestSnapshot.value,
                overflowStart = renderSnapshot.overflowStart,
                parserKey = parser,
                components = components,
                modifier = successModifier,
                typographyKey = typography.hashCode(),
                optionsKey = renderOptionsKey
            )
        },
        error = { androidx.compose.material3.Text(truncationLabel, modifier = Modifier.fillMaxWidth()) }
    )
}
