package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.shared.ui.text.GatedSelectionContainer
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import org.starfall.generative.features.home.chat.model.StreamFreezeSignal
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
    streamStartOffset: Int = 0,
    cacheIdentity: MarkdownRenderCacheIdentity? = null,
    streamFreezeSignal: StreamFreezeSignal? = null
) {
    val truncationLabel = stringResource(R.string.chat_response_truncated)
    if (isStreaming && streamSnapshot != null && streamGenerationId != null && streamMessageId != null) {
        IncrementalStreamingMarkdown(
            streamSnapshot = streamSnapshot,
            generationId = streamGenerationId,
            messageId = streamMessageId,
            sourceStartOffset = streamStartOffset,
            modifier = modifier,
            cacheIdentity = cacheIdentity,
            streamFreezeSignal = streamFreezeSignal
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
            if (input.length > MAX_MARKDOWN_RENDER_CHARS) {
                previewCreated = true
                delay(80)
                val latest = latestContent.value
                if (latest.length > MAX_MARKDOWN_RENDER_CHARS) onPreview(latest)
            } else if (!previewCreated) {
                onPreview("")
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
            isStreaming && !oversizedPreview.isNullOrEmpty() -> StreamingMarkdownPreview(oversizedPreview, modifier)
            isStreaming && displayContent.length > MAX_MARKDOWN_RENDER_CHARS -> StreamingMarkdownPreview(displayContent, modifier)
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
    GatedSelectionContainer {
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
    sourceStartOffset: Int,
    modifier: Modifier,
    cacheIdentity: MarkdownRenderCacheIdentity?,
    streamFreezeSignal: StreamFreezeSignal?
) {
    val initialBaseText = streamSnapshot.baseText
    val truncationLabel = stringResource(R.string.chat_response_truncated)
    key(generationId, messageId, sourceStartOffset, initialBaseText) {
        val cancellationToken = remember { MarkdownParserCancellationToken() }
        val incrementalParser = remember { IncrementalMarkdownParser(cancellationToken) }
        val latestSnapshot = rememberUpdatedState(streamSnapshot)
        val renderSnapshot = remember { mutableStateOf(incrementalParser.currentSnapshot()) }
        val parsing = remember { mutableStateOf(false) }
        TrackPendingWork(parsing.value)
        CollectIncrementalMarkdownStream(
            initialBaseText = initialBaseText,
            latestSnapshot = latestSnapshot,
            parser = incrementalParser,
            cancellationToken = cancellationToken,
            renderSnapshot = renderSnapshot,
            parsing = parsing,
            streamFreezeSignal = streamFreezeSignal
        )
        RenderIncrementalMarkdown(
            parser = incrementalParser,
            renderSnapshot = renderSnapshot.value,
            latestSnapshot = latestSnapshot,
            truncationLabel = truncationLabel,
            modifier = modifier,
            cacheIdentity = cacheIdentity
        )
    }
}

@Composable
private fun CollectIncrementalMarkdownStream(
    initialBaseText: String,
    latestSnapshot: androidx.compose.runtime.State<StreamTextSnapshot>,
    parser: IncrementalMarkdownParser,
    cancellationToken: MarkdownParserCancellationToken,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>,
    streamFreezeSignal: StreamFreezeSignal?
) {
    LaunchedEffect(Unit) {
        val parserJob = launch {
            cancellationToken.ownerJob = currentCoroutineContext()[Job]
            try {
                if (streamFreezeSignal?.isFrozen != true) {
                    appendInitialMarkdownBase(
                        initialBaseText, parser, renderSnapshot, parsing, streamFreezeSignal
                    )
                    if (streamFreezeSignal?.isFrozen != true) {
                        collectIncrementalMarkdownUpdates(
                            latestSnapshot = latestSnapshot,
                            parser = parser,
                            renderSnapshot = renderSnapshot,
                            parsing = parsing,
                            streamFreezeSignal = streamFreezeSignal
                        )
                    }
                }
            } finally {
                cancellationToken.ownerJob = null
            }
        }
        val freezeJob = streamFreezeSignal?.let { signal ->
            launch {
                signal.frozen.first { frozen -> frozen }
                parserJob.cancelAndJoin()
            }
        }
        try {
            parserJob.join()
        } finally {
            freezeJob?.cancelAndJoin()
        }
    }
}

private suspend fun appendInitialMarkdownBase(
    initialBaseText: String,
    parser: IncrementalMarkdownParser,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>,
    streamFreezeSignal: StreamFreezeSignal?
) {
    if (streamFreezeSignal?.isFrozen == true) return
    val initialContent = initialBaseText.take(MAX_MARKDOWN_RENDER_CHARS)
    if (initialContent.isEmpty()) return
    appendIncrementalMarkdown(
        parser, initialContent, StreamTextSnapshot.of(initialBaseText), renderSnapshot, parsing,
        streamFreezeSignal
    )
}

private suspend fun collectIncrementalMarkdownUpdates(
    latestSnapshot: androidx.compose.runtime.State<StreamTextSnapshot>,
    parser: IncrementalMarkdownParser,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>,
    streamFreezeSignal: StreamFreezeSignal?
) {
    var lastSequence = 0L
    snapshotFlow { latestSnapshot.value }.conflate().collect { current ->
        if (streamFreezeSignal?.isFrozen == true) return@collect
        if (renderSnapshot.value.overflowStart != null) {
            lastSequence = current.latestSegment?.sequence ?: lastSequence
            return@collect
        }
        if (current.length > MAX_MARKDOWN_RENDER_CHARS.toLong()) {
            // Do not parse or lay out the whole answer again. Freeze the parser's stable prefix and
            // render only the remaining source as one Text node in the same selection/layout tree.
            delay(80)
            if (streamFreezeSignal?.isFrozen == true) return@collect
            if (latestSnapshot.value.length > MAX_MARKDOWN_RENDER_CHARS.toLong()) {
                val overflowSnapshot = withContext(Dispatchers.Default) {
                    parser.preserveUnstableTailAsPlainText()
                }
                if (streamFreezeSignal?.isFrozen != true) renderSnapshot.value = overflowSnapshot
            }
            lastSequence = latestSnapshot.value.latestSegment?.sequence ?: lastSequence
            return@collect
        }
        appendNewMarkdownSegments(current, lastSequence, parser, renderSnapshot, parsing, streamFreezeSignal).also {
            lastSequence = it
        }
    }
}

private suspend fun appendNewMarkdownSegments(
    current: StreamTextSnapshot,
    previousSequence: Long,
    parser: IncrementalMarkdownParser,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>,
    streamFreezeSignal: StreamFreezeSignal?
): Long {
    var lastSequence = previousSequence
    for (segment in current.segmentsAfter(previousSequence)) {
        if (streamFreezeSignal?.isFrozen == true) break
        lastSequence = segment.sequence
        val remaining = (MAX_MARKDOWN_RENDER_CHARS - renderSnapshot.value.parsedLength).coerceAtLeast(0)
        val accepted = segment.text.take(remaining)
        if (accepted.isEmpty()) continue
        appendIncrementalMarkdown(parser, accepted, current, renderSnapshot, parsing, streamFreezeSignal)
        if (streamFreezeSignal?.isFrozen == true || renderSnapshot.value.overflowStart != null || accepted.length < segment.text.length) break
    }
    return lastSequence
}

private suspend fun appendIncrementalMarkdown(
    parser: IncrementalMarkdownParser,
    text: String,
    source: StreamTextSnapshot,
    renderSnapshot: MutableState<IncrementalMarkdownSnapshot>,
    parsing: MutableState<Boolean>,
    streamFreezeSignal: StreamFreezeSignal?
) {
    if (streamFreezeSignal?.isFrozen == true) return
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
        if (streamFreezeSignal?.isFrozen == true) return
        result.linkUpdates.forEach { (label, destination) ->
            parser.referenceLinkHandler.store(label, destination)
        }
        if (streamFreezeSignal?.isFrozen != true) renderSnapshot.value = result.snapshot
    } finally {
        parsing.value = false
    }
}

@Composable
private fun RenderIncrementalMarkdown(
    parser: IncrementalMarkdownParser,
    renderSnapshot: IncrementalMarkdownSnapshot,
    latestSnapshot: androidx.compose.runtime.State<StreamTextSnapshot>,
    truncationLabel: String,
    modifier: Modifier,
    cacheIdentity: MarkdownRenderCacheIdentity?
) {
    val preferences = LocalCodeRenderingPreferences.current
    val typography = messageMarkdownTypography()
    CompositionLocalProvider(LocalStreamingTextFade provides true) {
        RenderIncrementalMarkdownDocument(
            parser = parser,
            renderSnapshot = renderSnapshot,
            latestSnapshot = latestSnapshot,
            truncationLabel = truncationLabel,
            modifier = modifier,
            cacheIdentity = cacheIdentity,
            preferences = preferences,
            typography = typography
        )
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
    typography: MarkdownTypography
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
            fillMaxWidth = true
        ),
        success = { _, components, successModifier ->
            RenderIncrementalMarkdownDocument(
                stableNodes = renderSnapshot.stableNodes,
                unstableNodes = renderSnapshot.unstableNodes,
                source = latestSnapshot.value,
                overflowStart = renderSnapshot.overflowStart,
                components = components,
                latexEnabled = !preferences.latexMode.equals("OFF", ignoreCase = true),
                modifier = successModifier
            )
        },
        error = { androidx.compose.material3.Text(truncationLabel, modifier = Modifier.fillMaxWidth()) }
    )
}
