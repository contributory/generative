package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.model.State
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.features.home.chat.logic.markdown.stableMarkdownPrefixLength
import org.starfall.generative.features.home.chat.ui.MarkdownParseCache
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.text.messageMarkdownTypography

/** Frozen top-level nodes plus the length of their append-only source prefix. */
private class FrozenMarkdown(
    val prefixLength: Int,
    /** Present only on the compatibility path that has no generation-scoped append-only source. */
    val prefixText: String?,
    val chunks: PersistentList<FrozenMarkdownChunk>,
    val tailSeed: State?
) {
    companion object {
        val EMPTY = FrozenMarkdown(prefixLength = 0, prefixText = "", chunks = persistentListOf(), tailSeed = null)
    }
}

/**
 * Compatibility renderer for streaming callers that do not yet provide [StreamTextSnapshot]. Finished
 * chunks are parsed once and virtualized with the chat viewport. The active incomplete tail is bounded;
 * when it outgrows the parser limit, it is shown in full as plain text until the final static parse.
 */
@Composable
internal fun ChunkedStreamingMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    cacheIdentity: MarkdownRenderCacheIdentity? = null,
    streamGenerationId: Long? = null
) {
    key(streamGenerationId, cacheIdentity) {
        val latestContent by rememberUpdatedState(content)
        var frozen by remember { mutableStateOf(FrozenMarkdown.EMPTY) }
        val appendOnlyGeneration = streamGenerationId != null


        // A generation id makes the provider's append-only contract sufficient to validate the prefix in O(1).
        // Without one, retain the exact prefix and verify it before reusing chunks.
        val valid = frozen.takeIf { frozenPrefixMatches(content, it, appendOnlyGeneration) } ?: FrozenMarkdown.EMPTY
        val tailLength = (content.length - valid.prefixLength).coerceAtLeast(0)
        val tailIsPreview = tailLength > MAX_CHUNKED_MARKDOWN_TAIL_CHARS
        // An oversized tail is rendered as plain text, but never shortened: all of it stays visible.
        val tail = content.substring(valid.prefixLength)

        LaunchedEffect(Unit) {
            observeFrozenMarkdown(
                latestContent = { latestContent },
                currentFrozen = { frozen },
                appendOnlyGeneration = appendOnlyGeneration,
                cacheIdentity = cacheIdentity,
                onFrozen = { frozen = it }
            )
        }

        val preferences = LocalCodeRenderingPreferences.current
        val typographyKey = messageMarkdownTypography().hashCode()
        val renderOptionsKey = 31 * preferences.hashCode() +
            17 * preferences.latexMode.hashCode() + 7 * true.hashCode() + false.hashCode()
        VirtualizedFrozenChunks(
            chunks = valid.chunks,
            tail = tail,
            tailIsPreview = tailIsPreview,
            tailSeed = valid.tailSeed,
            identity = cacheIdentity,
            modifier = modifier,
            typographyKey = typographyKey,
            optionsKey = renderOptionsKey
        )
    }
}

private fun frozenPrefixMatches(text: String, state: FrozenMarkdown, appendOnlyGeneration: Boolean): Boolean = when {
    appendOnlyGeneration -> text.length >= state.prefixLength
    else -> state.prefixText?.let { prefix -> text.startsWith(prefix) } == true
}

private suspend fun observeFrozenMarkdown(
    latestContent: () -> String,
    currentFrozen: () -> FrozenMarkdown,
    appendOnlyGeneration: Boolean,
    cacheIdentity: MarkdownRenderCacheIdentity?,
    onFrozen: (FrozenMarkdown) -> Unit
) {
    snapshotFlow { latestContent() }.conflate().collect { text ->
        val existing = currentFrozen()
        var base = existing.takeIf { frozenPrefixMatches(text, it, appendOnlyGeneration) } ?: FrozenMarkdown.EMPTY
        if (base !== existing) onFrozen(base)
        while (true) {
            val current = latestContent()
            if (!frozenPrefixMatches(current, base, appendOnlyGeneration)) break
            val next = freezeStableMarkdownChunk(base, current, appendOnlyGeneration, cacheIdentity, latestContent)
                ?: break
            base = next
            onFrozen(base)
        }
    }
}

private suspend fun freezeStableMarkdownChunk(
    base: FrozenMarkdown,
    current: String,
    appendOnlyGeneration: Boolean,
    cacheIdentity: MarkdownRenderCacheIdentity?,
    latestContent: () -> String
): FrozenMarkdown? {
    val rest = current.substring(base.prefixLength)
    val cut = stableMarkdownPrefixLength(rest)
    if (cut <= 0) return null
    val chunk = rest.substring(0, cut)
    val (chunkState, fingerprint) = withContext(Dispatchers.Default) {
        val startedAt = System.nanoTime()
        val state = try {
            parseLibraryMarkdown(chunk)
        } finally {
            MarkdownParseCache.recordFullParse(chunk.length, System.nanoTime() - startedAt)
        }
        state to markdownContentFingerprint(chunk)
    }
    if (chunkState !is State.Success) return null

    val chunkIdentity = cacheIdentity?.forBlock("chunk-${base.chunks.size}")
    MarkdownParseCache.put(chunk, chunkState, chunkIdentity, fingerprint)
    val prefixLength = base.prefixLength + chunk.length
    val prefixText = if (appendOnlyGeneration) null else base.prefixText.orEmpty() + chunk
    // An oversized remainder is shown as plain text, so its seed parse would be wasted work.
    val remainingTail = current.substring(prefixLength)
    val tailSeed = if (remainingTail.length <= MAX_CHUNKED_MARKDOWN_TAIL_CHARS) parseFrozenTail(remainingTail) else null
    val latestStillMatches = if (appendOnlyGeneration) {
        latestContent().length >= prefixLength
    } else {
        latestContent().startsWith(prefixText.orEmpty())
    }
    if (!latestStillMatches) return null
    return FrozenMarkdown(
        prefixLength = prefixLength,
        prefixText = prefixText,
        chunks = base.chunks.add(FrozenMarkdownChunk(chunk, chunkState, chunkIdentity, fingerprint)),
        tailSeed = tailSeed?.takeIf { it is State.Success }
    )
}

private suspend fun parseFrozenTail(text: String): State {
    return withContext(Dispatchers.Default) {
        val startedAt = System.nanoTime()
        try {
            parseLibraryMarkdown(text)
        } finally {
            MarkdownParseCache.recordFullParse(text.length, System.nanoTime() - startedAt)
        }
    }
}

private const val MAX_CHUNKED_MARKDOWN_TAIL_CHARS = 8_000
