package org.starfall.generative.features.home.chat.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.getTextInNode
import org.starfall.generative.R
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.logic.markdown.MathPiece
import org.starfall.generative.features.home.chat.logic.markdown.MathSplitter
import org.starfall.generative.features.home.chat.logic.markdown.PersistentMarkdownAstCache
import org.starfall.generative.features.home.chat.logic.markdown.markdownFenceSource
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.features.home.chat.ui.AssetKey
import org.starfall.generative.features.home.chat.ui.MarkdownBlockHeightCache
import org.starfall.generative.features.home.chat.ui.MarkdownParseCache
import org.starfall.generative.features.home.chat.ui.TrackPendingWork
import org.starfall.generative.features.home.chat.ui.TrackReadyAssets
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.GatedSelectionContainer
import org.starfall.generative.shared.ui.text.messageMarkdownTypography
import org.starfall.generative.shared.ui.text.rememberStreamingTextPresentation
import org.starfall.generative.shared.ui.text.toComposeFontFamily

/** Library primitives with app-owned code toolbar, error/LaTeX UI and streaming fades. */
@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    latexMode: String? = null,
    fillMaxWidth: Boolean = true,
    /**
     * The library puts a block-sized spacer before every top-level block, including the first, and none
     * after the last. Inside a padded container (the user bubble) that makes the top gap larger than the
     * bottom one; this removes the leading spacer so both gaps match.
     */
    trimLeadingBlockSpace: Boolean = false,
    /**
     * Parse to show on the first frame instead of [State.Loading]. Used by [ChunkedStreamingMarkdown] for
     * a streaming tail: it may be slightly older than [content], which is the same staleness a
     * streaming renderer already shows while it re-parses.
     */
    initialParse: State? = null,
    cacheIdentity: MarkdownRenderCacheIdentity? = null
) {
    val context = LocalContext.current.applicationContext
    val persistentCache = remember(context) { PersistentMarkdownAstCache.get(context) }
    SideEffect {
        persistentCache.registerMemoryInvalidator(MarkdownParseCache) { conversationId, messageId ->
            when {
                conversationId == null -> MarkdownParseCache.clearAll()
                messageId == null -> MarkdownParseCache.invalidateConversation(conversationId)
                else -> MarkdownParseCache.invalidateMessage(conversationId, messageId)
            }
        }
        persistentCache.registerMemoryInvalidator(MarkdownBlockHeightCache) { conversationId, messageId ->
            when {
                conversationId == null -> MarkdownBlockHeightCache.clearAll()
                messageId == null -> MarkdownBlockHeightCache.invalidateConversation(conversationId)
                else -> MarkdownBlockHeightCache.invalidateMessage(conversationId, messageId)
            }
        }
    }
    if (content.isBlank()) return
    if (content.length > MAX_MARKDOWN_RENDER_CHARS) {
        SelectionContainer {
            Text(
                text = content.take(MAX_MARKDOWN_RENDER_CHARS) + "\n\n" + stringResource(R.string.chat_response_truncated),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                modifier = modifier.then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            )
        }
        return
    }
    val latestContent by rememberUpdatedState(content)
    val latestCacheIdentity by rememberUpdatedState(cacheIdentity)
    val latestInitialParse by rememberUpdatedState(initialParse)
    val streamingNow by rememberUpdatedState(isStreaming)
    // A tree parsed earlier in this process is shown on the very first frame, so a reopened chat has its
    // final height immediately instead of growing once the background parse finishes.
    val inheritedFingerprint = LocalMarkdownContentFingerprint.current
    val latestInheritedFingerprint by rememberUpdatedState(inheritedFingerprint)
    val initialParsed = remember {
        initialParse ?: MarkdownParseCache.get(content, cacheIdentity)
    }
    var parsed by remember {
        mutableStateOf<State>(initialParsed ?: State.Loading())
    }
    var parsedFingerprint by remember {
        mutableStateOf(inheritedFingerprint ?: MarkdownParseCache.getFingerprint(content, cacheIdentity))
    }
    val settledParse = parsed
    TrackPendingWork(settledParse is State.Loading)
    TrackReadyAssets(
        if (settledParse is State.Success) listOf(AssetKey.Markdown(settledParse.content, cacheIdentity)) else emptyList()
    )
    LaunchedEffect(Unit) {
        // Disk hydration and parser fallback are both off Main. Conflation keeps rapid source changes
        // from queuing an unbounded series of obsolete parse jobs.
        snapshotFlow { Triple(latestContent, latestCacheIdentity, streamingNow) }.conflate().collect { update ->
            val (input, identity, activeStream) = update
            val epoch = identity?.let(persistentCache::captureEpoch)
            val inMemory = MarkdownParseCache.get(input, identity)
            val memoryFingerprint = if (inMemory != null) MarkdownParseCache.getFingerprint(input, identity) else null
            val onDisk = if (inMemory == null && !activeStream && identity != null && epoch != null) {
                persistentCache.load(identity, input, epoch)
            } else null
            val seeded = latestInitialParse?.takeIf { state ->
                state is State.Success && state.content == input
            }
            val result: Pair<State, String?> = when {
                inMemory != null -> inMemory to (
                    memoryFingerprint ?: withContext(Dispatchers.Default) { markdownContentFingerprint(input) }
                    )
                onDisk != null -> onDisk to withContext(Dispatchers.Default) { markdownContentFingerprint(input) }
                seeded is State.Success -> seeded to (
                    latestInheritedFingerprint ?: withContext(Dispatchers.Default) { markdownContentFingerprint(input) }
                    )
                else -> withContext(Dispatchers.Default) {
                    val startedAt = System.nanoTime()
                    val state = try {
                        parseLibraryMarkdown(input)
                    } finally {
                        MarkdownParseCache.recordFullParse(input.length, System.nanoTime() - startedAt)
                    }
                    state to if (state is State.Success) markdownContentFingerprint(input) else null
                }
            }
            val (state, resultFingerprint) = result
            parsed = state
            parsedFingerprint = resultFingerprint
            if (!activeStream && state is State.Success) {
                MarkdownParseCache.put(input, state, identity, resultFingerprint)
                if (identity != null && onDisk == null && epoch != null) {
                    persistentCache.store(identity, input, state, epoch)
                }
            }
        }
    }
    LaunchedEffect(isStreaming, settledParse, content, cacheIdentity, parsedFingerprint) {
        // The last streamed chunk may have been parsed before the answer became final. Persist it under
        // its immutable message/block/content key without delaying stream completion or UI collection.
        if (!isStreaming && settledParse is State.Success && settledParse.content == content) {
            val fingerprint = parsedFingerprint ?: withContext(Dispatchers.Default) {
                markdownContentFingerprint(content)
            }
            MarkdownParseCache.put(settledParse.content, settledParse, cacheIdentity, fingerprint)
            if (cacheIdentity != null) {
                val epoch = persistentCache.captureEpoch(cacheIdentity)
                persistentCache.store(cacheIdentity, content, settledParse, epoch)
            }
        }
    }
    val type = MaterialTheme.typography
    val preferences = LocalCodeRenderingPreferences.current
    // The Settings choice (ON / OFF / AUTO) is the default; nothing used to pass it down.
    val effectiveLatexMode = latexMode ?: preferences.latexMode
    val bodySize = preferences.effectiveMessageFontSize.sp
    val messageFont = preferences.messageFontFamily.toComposeFontFamily()
    val markdownTypography = messageMarkdownTypography()
    val renderOptionsKey = 31 * preferences.hashCode() +
        17 * effectiveLatexMode.hashCode() + 7 * fillMaxWidth.hashCode() + isStreaming.hashCode()
    Markdown(
        state = parsed,
        modifier = modifier
            .then(if (trimLeadingBlockSpace) Modifier.trimLeadingSpace(MARKDOWN_BLOCK_PADDING) else Modifier)
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier),
        colors = markdownColor(),
        typography = markdownTypography,
        padding = markdownPadding(block = MARKDOWN_BLOCK_PADDING),
        components = chatMarkdownComponents(isStreaming, effectiveLatexMode, fillMaxWidth, cacheIdentity, renderOptionsKey),
        success = { state, components, successModifier ->
            val viewport = LocalMarkdownViewportState.current
            if (!isStreaming && viewport != null) {
                val fingerprint = LocalMarkdownContentFingerprint.current ?: parsedFingerprint
                if (fingerprint == null) {
                    // A legacy cache entry without its precomputed fingerprint is a one-frame eager fallback;
                    // the off-main cache load below supplies the fingerprint before virtualization starts.
                    CompositionLocalProvider(LocalMarkdownViewportState provides null) {
                        MarkdownSuccessWithBoundedHtml(state, components, successModifier)
                    }
                } else {
                    CompositionLocalProvider(LocalMarkdownContentFingerprint provides fingerprint) {
                        if (state.node.children.size > VIRTUALIZED_TOP_LEVEL_BLOCKS) {
                            VirtualizedMarkdownBlocks(
                                state = state,
                                components = components,
                                modifier = successModifier,
                                identity = cacheIdentity,
                                contentFingerprint = fingerprint,
                                typographyKey = markdownTypography.hashCode(),
                                optionsKey = renderOptionsKey
                            )
                        } else {
                            MarkdownSuccessWithBoundedHtml(state, components, successModifier)
                        }
                    }
                }
            } else {
                MarkdownSuccessWithBoundedHtml(state, components, successModifier)
            }
        },
        error = { Text(content, style = type.bodyLarge.copy(fontFamily = messageFont, fontSize = bodySize)) }
    )
}

internal const val MAX_MARKDOWN_RENDER_CHARS = 128_000
internal const val MAX_INCREMENTAL_UNSTABLE_TAIL_CHARS = 8_000
internal const val MAX_INCREMENTAL_PARSER_APPEND_CHARS = 4_096
private const val VIRTUALIZED_TOP_LEVEL_BLOCKS = 12
private const val VIRTUALIZED_LIST_ITEMS = 12
internal val MARKDOWN_BLOCK_PADDING = 8.dp

@Composable
internal fun chatMarkdownComponents(
    isStreaming: Boolean,
    latexMode: String,
    fillMaxWidth: Boolean,
    cacheIdentity: MarkdownRenderCacheIdentity? = null,
    renderOptionsKey: Int = 0
): MarkdownComponents = markdownComponents(
    codeFence = { model ->
        val fence = markdownFenceSource(model.content, model.node)
        RenderCodeBlock(
            language = fence.language,
            source = model.content,
            startOffset = fence.codeStartOffset,
            endOffset = fence.codeEndOffset,
            isStreaming = isStreaming && !fence.closed && model.node.endOffset == model.content.length
        )
    },
    codeBlock = { model ->
        RenderCodeBlock(
            language = "",
            source = model.content,
            startOffset = model.node.startOffset,
            endOffset = model.node.endOffset,
            isStreaming = false,
            trimTrailingWhitespace = true
        )
    },

    heading1 = { model -> RenderMarkdownHeading(model, model.typography.h1, latexMode, fillMaxWidth) },
    heading2 = { model -> RenderMarkdownHeading(model, model.typography.h2, latexMode, fillMaxWidth) },
    heading3 = { model -> RenderMarkdownHeading(model, model.typography.h3, latexMode, fillMaxWidth) },
    heading4 = { model -> RenderMarkdownHeading(model, model.typography.h4, latexMode, fillMaxWidth) },
    heading5 = { model -> RenderMarkdownHeading(model, model.typography.h5, latexMode, fillMaxWidth) },
    heading6 = { model -> RenderMarkdownHeading(model, model.typography.h6, latexMode, fillMaxWidth) },
    setextHeading1 = { model ->
        RenderMarkdownHeading(model, model.typography.h1, latexMode, fillMaxWidth, MarkdownTokenTypes.SETEXT_CONTENT)
    },
    setextHeading2 = { model ->
        RenderMarkdownHeading(model, model.typography.h2, latexMode, fillMaxWidth, MarkdownTokenTypes.SETEXT_CONTENT)
    },
    paragraph = { model -> RenderParagraph(model, latexMode, fillMaxWidth) },
    orderedList = { model ->
        val itemCount = remember(model.node) {
            model.node.children.count { it.type == MarkdownElementTypes.LIST_ITEM }
        }
        if (LocalMarkdownViewportState.current != null && itemCount > VIRTUALIZED_LIST_ITEMS) {
            VirtualizedMarkdownList(
                model = model,
                identity = cacheIdentity,
                contentFingerprint = LocalMarkdownContentFingerprint.current
                    ?: markdownContentFingerprint(model.content),
                ordered = true,
                typographyKey = model.typography.hashCode(),
                optionsKey = renderOptionsKey
            )
        } else {
            MarkdownOrderedList(model.content, model.node, style = model.typography.ordered, depth = model.listDepth)
        }
    },
    unorderedList = { model ->
        val itemCount = remember(model.node) {
            model.node.children.count { it.type == MarkdownElementTypes.LIST_ITEM }
        }
        if (LocalMarkdownViewportState.current != null && itemCount > VIRTUALIZED_LIST_ITEMS) {
            VirtualizedMarkdownList(
                model = model,
                identity = cacheIdentity,
                contentFingerprint = LocalMarkdownContentFingerprint.current
                    ?: markdownContentFingerprint(model.content),
                ordered = false,
                typographyKey = model.typography.hashCode(),
                optionsKey = renderOptionsKey
            )
        } else {
            MarkdownBulletList(model.content, model.node, style = model.typography.bullet, depth = model.listDepth)
        }
    },
    table = { model ->
        RenderMarkdownTable(
            model = model,
            identity = cacheIdentity,
            allowVirtualization = LocalMarkdownViewportState.current != null,
            typographyKey = model.typography.hashCode(),
            optionsKey = renderOptionsKey
        )
    }
)

/** Cuts [amount] from the top of the content: it is measured in full, then placed higher and reported shorter. */
private fun Modifier.trimLeadingSpace(amount: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val shift = amount.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/**
 * Animated streaming version of markdown content.
 */
@Composable
private fun RenderParagraph(model: MarkdownComponentModel, latexMode: String, fillMaxWidth: Boolean) {
    val sourceLength = (model.node.endOffset - model.node.startOffset).coerceAtLeast(0)
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = preferences.effectiveMessageFontSize.sp
    val messageFont = preferences.messageFontFamily.toComposeFontFamily()
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = messageFont,
        fontSize = bodySize,
        lineHeight = bodySize * 1.375f,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (sourceLength > LARGE_PARAGRAPH_EAGER_LIMIT) {
        LargeMarkdownParagraph(
            source = model.content,
            startOffset = model.node.startOffset,
            endOffset = model.node.endOffset,
            style = bodyStyle
        )
        return
    }
    val text = model.node.getTextInNode(model.content).toString()
    val context = LocalContext.current
    val presentation = rememberStreamingTextPresentation(text)
    val isError = remember(text) { isErrorText(text) }
    // Null when the paragraph holds no math (or rendering is disabled): the plain Markdown path is kept.
    val mathPieces = remember(text, latexMode) {
        MathSplitter.split(text, latexMode).takeIf { pieces -> pieces.any { it is MathPiece.Math } }
    }

    when {
        isError -> {
            val errorColor = MaterialTheme.colorScheme.error
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(1.dp, errorColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        GatedSelectionContainer {
                            Text(
                                text = text,
                                modifier = presentation.modifier,
                                onTextLayout = presentation.onTextLayout,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                     fontFamily = messageFont,
                                     fontSize = bodySize,
                                     lineHeight = bodySize * 1.375f,
                                    color = errorColor,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.markdown_error_clip_label), text))
                            Toast.makeText(context, context.getString(R.string.markdown_copied_error_text), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        FluentIcon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.message_copy_error),
                            tint = errorColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        mathPieces != null -> {
            RenderMathParagraph(
                pieces = mathPieces,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = messageFont,
                    fontSize = bodySize,
                    lineHeight = bodySize * 1.375f,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                fillMaxWidth = fillMaxWidth
            )
        }
        else -> {
            val settings = annotatorSettings()
            val annotated = buildAnnotatedString {
                    buildMarkdownAnnotatedString(model.content, model.node, settings)
            }
            GatedSelectionContainer {
                MarkdownClickableText(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = messageFont,
                        fontSize = bodySize,
                        lineHeight = bodySize * 1.375f,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                     modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
                 )
            }
        }
    }
}

private fun isErrorText(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("[Error:", ignoreCase = true) ||
           trimmed.startsWith("Error:", ignoreCase = true) ||
           trimmed.startsWith("API Error", ignoreCase = true) ||
           trimmed.startsWith("Exception:", ignoreCase = true) ||
           trimmed.startsWith("Failed:", ignoreCase = true) ||
           trimmed.contains("[Error:", ignoreCase = true)
}
