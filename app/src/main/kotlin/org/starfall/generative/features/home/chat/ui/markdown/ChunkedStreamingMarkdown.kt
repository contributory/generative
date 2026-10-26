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
 * when it outgrows the parser limit, a short plain-text preview is shown until the final static parse.
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

        fun prefixStillMatches(text: String, state: FrozenMarkdown): Boolean = when {
            appendOnlyGeneration -> text.length >= state.prefixLength
            else -> state.prefixText?.let { prefix -> text.startsWith(prefix) } == true
        }

        // A generation id makes the provider's append-only contract sufficient to validate the prefix in O(1).
        // Without one, retain the exact prefix and verify it before reusing chunks.
        val valid = if (prefixStillMatches(content, frozen)) frozen else FrozenMarkdown.EMPTY
        val tailLength = (content.length - valid.prefixLength).coerceAtLeast(0)
        val tailIsPreview = tailLength > MAX_CHUNKED_MARKDOWN_TAIL_CHARS
        val tail = if (tailIsPreview) {
            "…\n" + content.takeLast(CHUNKED_TAIL_PREVIEW_CHARS)
        } else {
            content.substring(valid.prefixLength)
        }

        LaunchedEffect(Unit) {
            snapshotFlow { latestContent }.conflate().collect { text ->
                var base = frozen.takeIf { prefixStillMatches(text, it) } ?: FrozenMarkdown.EMPTY
                if (base !== frozen) frozen = base
                while (true) {                                   // a burst may contain several finished blocks
                    val current = latestContent
                    if (!prefixStillMatches(current, base)) break
                    val restLength = current.length - base.prefixLength
                    if (restLength > MAX_CHUNKED_MARKDOWN_TAIL_CHARS) break // do not rescan an unclosed huge block
                    val rest = current.substring(base.prefixLength)
                    val cut = stableMarkdownPrefixLength(rest)
                    if (cut <= 0) break
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
                    if (chunkState !is State.Success) break      // keep it in the tail; the final render handles it
                    val chunkIdentity = cacheIdentity?.forBlock("chunk-${base.chunks.size}")
                    MarkdownParseCache.put(chunk, chunkState, chunkIdentity, fingerprint) // cache before publishing the chunk
                    val prefixLength = base.prefixLength + chunk.length
                    val prefixText = if (appendOnlyGeneration) null else base.prefixText.orEmpty() + chunk
                    val tailText = current.substring(prefixLength)
                    val tailSeed = withContext(Dispatchers.Default) {
                        val startedAt = System.nanoTime()
                        try {
                            parseLibraryMarkdown(tailText)
                        } finally {
                            MarkdownParseCache.recordFullParse(tailText.length, System.nanoTime() - startedAt)
                        }
                    }
                    val latestStillMatches = if (appendOnlyGeneration) {
                        latestContent.length >= prefixLength
                    } else {
                        latestContent.startsWith(prefixText.orEmpty())
                    }
                    if (!latestStillMatches) break // text changed during the parse
                    base = FrozenMarkdown(
                        prefixLength = prefixLength,
                        prefixText = prefixText,
                        chunks = base.chunks.add(FrozenMarkdownChunk(chunk, chunkState, chunkIdentity, fingerprint)),
                        tailSeed = tailSeed.takeIf { it is State.Success }
                    )
                    frozen = base                                // ONE atomic state write: chunk appears, tail shrinks
                }
            }
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

private const val MAX_CHUNKED_MARKDOWN_TAIL_CHARS = 8_000
private const val CHUNKED_TAIL_PREVIEW_CHARS = 4_000
