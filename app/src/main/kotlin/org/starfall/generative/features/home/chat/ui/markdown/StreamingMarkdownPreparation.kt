package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.starfall.generative.features.home.chat.logic.markdown.MathPiece
import org.starfall.generative.features.home.chat.logic.markdown.MathSplitter
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.features.home.chat.logic.markdown.stableMarkdownPrefixLength
import org.starfall.generative.features.home.chat.model.ActiveChatStreamState
import org.starfall.generative.features.home.chat.model.ChatStreamStatus
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import org.starfall.generative.features.home.chat.ui.MarkdownParseCache
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences

/** Prepares cache entries, never hidden Compose trees or guessed layout sizes. */
@Composable
internal fun PrepareStreamingMarkdown(
    conversationId: String?,
    storedMessage: StoredMessage?,
    activeStream: StateFlow<ActiveChatStreamState?>?,
    composedMessageIds: () -> Set<String> = { emptySet() }
) {
    if (conversationId == null || activeStream == null) return
    val density = LocalDensity.current
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = with(density) { preferences.effectiveMessageFontSize.sp.toPx() }
    val displayFont = MaterialTheme.typography.bodyLarge.fontSize.takeIf { it.isSpecified } ?: 16.sp
    val displaySize = with(density) { displayFont.toPx() }
    val answerColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val dark = isSystemInDarkTheme()
    val latexMode = preferences.latexMode
    val saved by rememberUpdatedState(storedMessage)
    val composedIds by rememberUpdatedState(composedMessageIds)
    LaunchedEffect(conversationId, activeStream, bodySize, displaySize, answerColor, dark, latexMode) {
        var lastMessageId: String? = null
        var lastGenerationId = 0L
        var preparedKey: Pair<Long, String>? = null
        var answer = StreamingMarkdownPreparer()
        combine(activeStream, snapshotFlow { saved to composedIds() }) { live, (finalMessage, mounted) ->
            if (live?.conversationId == conversationId) {
                lastMessageId = live.messageId
                lastGenerationId = live.generationId
                if (live.messageId in mounted) null else PreparationRequest(live.generationId, live.messageId, live.content,
                    live.status != ChatStreamStatus.STREAMING || live.stopSignal.isFrozen)
            } else if (finalMessage?.id == lastMessageId && finalMessage?.activeVersion?.processingFinishedAt != null &&
                finalMessage.id !in mounted) {
                PreparationRequest(lastGenerationId, finalMessage.id,
                    StreamTextSnapshot.of(finalMessage.content),
                    true)
            } else null
        }.conflate().distinctUntilChanged().collect { request ->
            if (request == null) return@collect
            val key = request.generationId to request.messageId
            if (key != preparedKey) {
                answer = StreamingMarkdownPreparer()
                preparedKey = key
            }
            answer.prepare(request.content, request.terminal, latexMode, bodySize, displaySize, answerColor, dark)
        }
    }
}

private data class PreparationRequest(
    val generationId: Long,
    val messageId: String,
    val content: StreamTextSnapshot,
    val terminal: Boolean
)

/** Append-only cursor; stable block boundaries come from the existing Markdown implementation. */
internal class StreamingMarkdownPreparer {
    private var preparedLength = 0

    suspend fun prepare(
        source: StreamTextSnapshot,
        terminal: Boolean,
        latexMode: String,
        bodySizePx: Float,
        displaySizePx: Float,
        colorArgb: Int,
        dark: Boolean
    ) = withContext(Dispatchers.Default) {
        val end = source.length.coerceAtMost(MAX_MARKDOWN_RENDER_CHARS.toLong()).toInt()
        if (end < preparedLength) preparedLength = 0
        if (end > preparedLength) {
            val tail = source.slice(preparedLength, end)
            val cut = if (terminal && source.length <= MAX_MARKDOWN_RENDER_CHARS) tail.length
                else stableMarkdownPrefixLength(tail)
            val block = tail.substring(0, cut)
            val stable = cut > 0 && !MathSplitter.hasUnclosedMathDelimiter(block)
            if (stable) prepareAst(block.trimEnd())
            // Closed formulas can be prepared even before the next paragraph makes its AST stable.
            MathSplitter.split(tail, latexMode).filterIsInstance<MathPiece.Math>().forEach { math ->
                currentCoroutineContext().ensureActive()
                val key = latexCacheKey(math.formula,
                    if (math.display) displaySizePx else bodySizePx, colorArgb, dark)
                if (!LatexDrawables.isPrepared(key)) {
                    try { prepareLatexDrawable(key) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        android.util.Log.w("Markdown preparation", "Formula remains available as source", error)
                    }
                }
            }
            if (stable) preparedLength += cut
        }
        // A final full-document entry is reusable when a previously unseen row mounts after Stop.
        if (terminal && source.length <= MAX_MARKDOWN_RENDER_CHARS) prepareAst(source.slice(0, end).trimEnd())
    }

    private suspend fun prepareAst(text: String) {
        if (text.isBlank() || MarkdownParseCache.contains(text)) return
        val startedAt = System.nanoTime()
        val parsed = try { parseLibraryMarkdown(text) } finally {
            MarkdownParseCache.recordFullParse(text.length, System.nanoTime() - startedAt)
        }
        currentCoroutineContext().ensureActive()
        MarkdownParseCache.put(text, parsed, contentFingerprint = markdownContentFingerprint(text))
    }
}
