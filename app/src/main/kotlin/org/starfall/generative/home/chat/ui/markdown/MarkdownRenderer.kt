package org.starfall.generative.home.chat.ui.markdown

import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.intellij.markdown.ast.getTextInNode
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.home.chat.logic.markdown.MathPiece
import org.starfall.generative.home.chat.logic.markdown.MathSplitter
import org.starfall.generative.home.chat.logic.markdown.markdownFence
import org.starfall.generative.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.home.chat.ui.AssetKey
import org.starfall.generative.shared.ui.text.GatedSelectionContainer
import org.starfall.generative.shared.ui.text.messageMarkdownTypography
import org.starfall.generative.shared.ui.text.toComposeFontFamily
import org.starfall.generative.shared.ui.text.LocalStreamingTextFade
import org.starfall.generative.home.chat.ui.MarkdownParseCache
import org.starfall.generative.shared.ui.text.STREAM_FADE_DURATION_MS
import org.starfall.generative.home.chat.ui.TrackPendingWork
import org.starfall.generative.home.chat.ui.TrackReadyAssets
import org.starfall.generative.shared.ui.text.rememberStreamingTextPresentation

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
    initialParse: State? = null
) {
    if (content.isBlank()) return
    if (content.length > MAX_MARKDOWN_RENDER_CHARS) {
        SelectionContainer {
            Text(
                text = content.take(MAX_MARKDOWN_RENDER_CHARS) + "\n\n[Response preview truncated for performance]",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                modifier = modifier.then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            )
        }
        return
    }
    val latestContent by rememberUpdatedState(content)
    val streamingNow by rememberUpdatedState(isStreaming)
    // A tree parsed earlier in this process is shown on the very first frame, so a reopened chat has its
    // final height immediately instead of growing once the background parse finishes.
    var parsed by remember { mutableStateOf<State>(initialParse ?: MarkdownParseCache.get(content) ?: State.Loading()) }
    val settledParse = parsed
    TrackPendingWork(settledParse is State.Loading)
    TrackReadyAssets(if (settledParse is State.Success) listOf(AssetKey.Markdown(settledParse.content)) else emptyList())
    LaunchedEffect(Unit) {
        // Finish each off-main parse and retain its result while the next chunk is
        // parsed. Fast token updates must not cancel parsing or flash a blank UI.
        snapshotFlow { latestContent }.conflate().collect { input ->
            parsed = MarkdownParseCache.get(input)
                ?: withContext(Dispatchers.Default) { parseLibraryMarkdown(input) }.also {
                    // Partial text of a streaming answer is never worth keeping.
                    if (!streamingNow) MarkdownParseCache.put(input, it)
                }
        }
    }
    LaunchedEffect(isStreaming, settledParse) {
        // The last chunk is parsed while still "streaming"; keep it once the answer is final.
        if (!isStreaming && settledParse is State.Success) MarkdownParseCache.put(settledParse.content, settledParse)
    }
    val type = MaterialTheme.typography
    val preferences = LocalCodeRenderingPreferences.current
    // The Settings choice (ON / OFF / AUTO) is the default; nothing used to pass it down.
    val effectiveLatexMode = latexMode ?: preferences.latexMode
    val bodySize = preferences.effectiveMessageFontSize.sp
    val messageFont = preferences.messageFontFamily.toComposeFontFamily()
    Markdown(
        state = parsed,
        modifier = modifier
            .then(if (trimLeadingBlockSpace) Modifier.trimLeadingSpace(MARKDOWN_BLOCK_PADDING) else Modifier)
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier),
        colors = markdownColor(),
        typography = messageMarkdownTypography(),
        padding = markdownPadding(block = MARKDOWN_BLOCK_PADDING),
        components = markdownComponents(
            codeFence = { model ->
                val fence = markdownFence(model.content, model.node)
                RenderCodeBlock(fence.language, fence.code,
                    isStreaming && !fence.closed && model.node.endOffset == model.content.length)
            },
            codeBlock = { model -> RenderCodeBlock("", model.node.getTextInNode(model.content).toString().trimEnd(), false) },
            paragraph = { model -> RenderParagraph(model, effectiveLatexMode, fillMaxWidth) },
            table = { model -> RenderMarkdownTable(model) }
        ),
        error = { Text(content, style = type.bodyLarge.copy(fontFamily = messageFont, fontSize = bodySize)) }
    )
}

private const val MAX_MARKDOWN_RENDER_CHARS = 128_000
private val MARKDOWN_BLOCK_PADDING = 8.dp

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
fun StreamingMarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = true
) {
    val latestContent by rememberUpdatedState(content)
    var displayContent by remember { mutableStateOf(content) }
    var finishingFade by remember { mutableStateOf(isStreaming) }
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
                displayContent = latestContent
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
                if (finalText.length <= MAX_MARKDOWN_RENDER_CHARS && MarkdownParseCache.get(finalText) == null) {
                    MarkdownParseCache.put(finalText, withContext(Dispatchers.Default) { parseLibraryMarkdown(finalText) })
                }
                displayContent = finalText
                settled = true
            }
        }
    }
    CompositionLocalProvider(LocalStreamingTextFade provides (isStreaming || finishingFade)) {
        // Above the render limit the single renderer shows its truncated preview, as before.
        // `!isStreaming` keeps a regenerated answer on the chunked path on its first frame, before the effect resets `settled`.
        if ((settled && !isStreaming) || displayContent.length > MAX_MARKDOWN_RENDER_CHARS) {
            MarkdownRenderer(
                content = displayContent,
                modifier = modifier,
                isStreaming = isStreaming
            )
        } else {
            ChunkedStreamingMarkdown(content = displayContent, modifier = modifier)
        }
    }
}

@Composable
private fun RenderParagraph(model: MarkdownComponentModel, latexMode: String, fillMaxWidth: Boolean) {
    val text = model.node.getTextInNode(model.content).toString()
    val context = LocalContext.current
    val presentation = rememberStreamingTextPresentation(text)
    val isError = remember(text) { isErrorText(text) }
    // Null when the paragraph holds no math (or rendering is disabled): the plain Markdown path is kept.
    val mathPieces = remember(text, latexMode) {
        MathSplitter.split(text, latexMode).takeIf { pieces -> pieces.any { it is MathPiece.Math } }
    }
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = preferences.effectiveMessageFontSize.sp
    val messageFont = preferences.messageFontFamily.toComposeFontFamily()

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
                            clipboard.setPrimaryClip(ClipData.newPlainText("Error Message", text))
                            Toast.makeText(context, "Copied error text", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        FluentIcon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Error",
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
