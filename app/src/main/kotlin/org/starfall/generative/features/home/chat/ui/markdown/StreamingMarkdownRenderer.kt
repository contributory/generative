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
    val latestContent by rememberUpdatedState(content)
    var displayContent by remember(streamGenerationId, streamMessageId, cacheIdentity) { mutableStateOf(content) }
    var oversizedPreview by remember(streamGenerationId, streamMessageId, cacheIdentity) { mutableStateOf<String?>(null) }
    var finishingFade by remember { mutableStateOf(isStreaming) }
    LaunchedEffect(streamGenerationId, streamMessageId, cacheIdentity) {
        var previewCreated = false
        snapshotFlow { latestContent }.conflate().collect { input ->
            if (!previewCreated && input.length > MAX_MARKDOWN_RENDER_CHARS) {
                previewCreated = true
                oversizedPreview = withContext(Dispatchers.Default) {
                    input.take(MAX_MARKDOWN_RENDER_CHARS) + "\n\n" + truncationLabel
                }
            }
        }
    }
    // While streaming, finished top-level blocks are frozen by ChunkedStreamingMarkdown. Once the answer is
    // final and parsed, the single renderer takes over. A message that is already finished starts settled.
    var settled by remember { mutableStateOf(!isStreaming) }
    LaunchedEffect(isStreaming, if (isStreaming) null else content) {
        if (isStreaming) {
            settled = false
            finishingFade = true
            snapshotFlow { latestContent }.conflate().collect {
                // Render a complete received chunk, rather than reparsing every animated character.
                delay(64)
                val latest = latestContent
                if (latest.length <= MAX_MARKDOWN_RENDER_CHARS) displayContent = latest
            }
        } else {
            // Flush the last chunk and let its fade finish before switching to static rendering.
            displayContent = latestContent
            if (finishingFade) {
                delay(STREAM_FADE_DURATION_MS + 32)
                finishingFade = false
            }
            if (!settled) {
                // Parse the final text first: without it the single renderer starts as Loading and the
                // whole answer would blink blank. Texts above the render limit are never parsed.
                val finalText = latestContent
                if (finalText.length <= MAX_MARKDOWN_RENDER_CHARS && MarkdownParseCache.get(finalText, cacheIdentity) == null) {
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
                displayContent = finalText
                settled = true
            }
        }
    }
    CompositionLocalProvider(LocalStreamingTextFade provides (isStreaming || finishingFade)) {
        when {
            oversizedPreview != null -> SelectionContainer {
                Text(
                    text = oversizedPreview.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    modifier = modifier.fillMaxWidth()
                )
            }
            displayContent.length > MAX_MARKDOWN_RENDER_CHARS -> SelectionContainer {
                Text(
                    text = truncationLabel,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    modifier = modifier.fillMaxWidth()
                )
            }
            // `!isStreaming` keeps a regenerated answer on the chunked path on its first frame, before the effect resets `settled`.
            (settled && !isStreaming) -> MarkdownRenderer(
                content = displayContent,
                modifier = modifier,
                isStreaming = isStreaming,
                cacheIdentity = cacheIdentity
            )
            else -> ChunkedStreamingMarkdown(
                content = displayContent,
                modifier = modifier,
                cacheIdentity = cacheIdentity,
                streamGenerationId = streamGenerationId
            )
        }
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
        val latestSnapshot by rememberUpdatedState(streamSnapshot)
        var renderSnapshot by remember {
            mutableStateOf(incrementalParser.currentSnapshot())
        }
        var truncatedContent by remember {
            mutableStateOf(
                streamSnapshot.takeIf { it.length > MAX_MARKDOWN_RENDER_CHARS.toLong() }
                    ?.prefix(MAX_MARKDOWN_RENDER_CHARS)
                    ?.let { "$it\n\n$truncationLabel" }
            )
        }
        var parsing by remember { mutableStateOf(false) }
        TrackPendingWork(parsing)

        LaunchedEffect(Unit) {
            cancellationToken.ownerJob = currentCoroutineContext()[Job]
            try {
                if (truncatedContent != null) return@LaunchedEffect
                val initialContent = initialBaseText.take(MAX_MARKDOWN_RENDER_CHARS)
                if (initialContent.isNotEmpty()) {
                    parsing = true
                    try {
                        val initialSource = StreamTextSnapshot.of(initialBaseText)
                        val result = withContext(Dispatchers.Default) {
                            incrementalParser.append(
                                text = initialContent,
                                source = initialSource,
                                maxUnstableTailChars = MAX_INCREMENTAL_UNSTABLE_TAIL_CHARS,
                                maxParserAppendChars = MAX_INCREMENTAL_PARSER_APPEND_CHARS
                            )
                        }
                        result.linkUpdates.forEach { (label, destination) ->
                            incrementalParser.referenceLinkHandler.store(label, destination)
                        }
                        renderSnapshot = result.snapshot
                    } finally {
                        parsing = false
                    }
                }

                var lastSequence = 0L
                if (latestSnapshot.length > MAX_MARKDOWN_RENDER_CHARS.toLong()) {
                    truncatedContent = latestSnapshot.prefix(MAX_MARKDOWN_RENDER_CHARS) +
                        "\n\n" + truncationLabel
                    return@LaunchedEffect
                }
                snapshotFlow { latestSnapshot }.conflate().collect { current ->
                    if (truncatedContent != null) return@collect
                    if (current.length > MAX_MARKDOWN_RENDER_CHARS.toLong()) {
                        truncatedContent = current.prefix(MAX_MARKDOWN_RENDER_CHARS) + "\n\n" + truncationLabel
                        return@collect
                    }
                    if (renderSnapshot.overflowStart != null) {
                        lastSequence = current.latestSegment?.sequence ?: lastSequence
                        return@collect
                    }
                    for (segment in current.segmentsAfter(lastSequence)) {
                        lastSequence = segment.sequence
                        val remaining = (MAX_MARKDOWN_RENDER_CHARS - renderSnapshot.parsedLength).coerceAtLeast(0)
                        val accepted = segment.text.take(remaining)
                        if (accepted.isEmpty()) continue
                        parsing = true
                        try {
                            val result = withContext(Dispatchers.Default) {
                                incrementalParser.append(
                                    text = accepted,
                                    source = current,
                                    maxUnstableTailChars = MAX_INCREMENTAL_UNSTABLE_TAIL_CHARS,
                                    maxParserAppendChars = MAX_INCREMENTAL_PARSER_APPEND_CHARS
                                )
                            }
                            result.linkUpdates.forEach { (label, destination) ->
                                incrementalParser.referenceLinkHandler.store(label, destination)
                            }
                            renderSnapshot = result.snapshot
                        } finally {
                            parsing = false
                        }
                        if (renderSnapshot.overflowStart != null || accepted.length < segment.text.length) break
                    }
                }
            } finally {
                cancellationToken.ownerJob = null
            }
        }

        val preferences = LocalCodeRenderingPreferences.current
        val typography = messageMarkdownTypography()
        val renderOptionsKey = 31 * preferences.hashCode() +
            17 * preferences.latexMode.hashCode() + 7 * true.hashCode() + true.hashCode()
        CompositionLocalProvider(LocalStreamingTextFade provides true) {
            val truncated = truncatedContent
            if (truncated != null) {
                SelectionContainer {
                    Text(
                        text = truncated,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        modifier = modifier.fillMaxWidth()
                    )
                }
            } else {
                Markdown(
                    state = State.Success(
                        node = incrementalParser.file,
                        // This opaque revision key drives Compose updates; the custom success renderer slices
                        // actual source from the latest stream snapshot and never consumes State.Success.content.
                        content = renderSnapshot.revision.toString(),
                        linksLookedUp = true,
                        referenceLinkHandler = incrementalParser.referenceLinkHandler
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
                            source = latestSnapshot,
                            overflowStart = renderSnapshot.overflowStart,
                            parserKey = incrementalParser,
                            components = components,
                            modifier = successModifier,
                            typographyKey = typography.hashCode(),
                            optionsKey = renderOptionsKey
                        )
                    },
                    error = { Text(truncationLabel, modifier = Modifier.fillMaxWidth()) }
                )
            }
        }
    }
}
