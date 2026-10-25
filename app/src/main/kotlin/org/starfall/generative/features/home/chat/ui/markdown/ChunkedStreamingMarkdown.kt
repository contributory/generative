package org.starfall.generative.home.chat.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.starfall.generative.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.home.chat.logic.markdown.stableMarkdownPrefixLength
import org.starfall.generative.home.chat.ui.MarkdownParseCache
import org.starfall.generative.shared.ui.text.LocalStreamingTextFade

/** One finished top-level block. [parsed] is kept here so the chunk never waits for [MarkdownParseCache]. */
private class FrozenChunk(val text: String, val parsed: State)

/**
 * Finished top-level blocks of a streaming answer. [prefix] is their concatenated source; [tailSeed] is the
 * parse of the text that follows it. Both are written in one state update, so the tail never shows text that
 * has already moved into a chunk.
 */
private class FrozenMarkdown(val prefix: String, val chunks: List<FrozenChunk>, val tailSeed: State?) {
    companion object {
        val EMPTY = FrozenMarkdown("", emptyList(), null)
    }
}

/**
 * Streaming answer split into frozen chunks and one growing tail. Frozen chunks are parsed once, are not
 * faded, and are not re-parsed; only the tail is re-parsed on each update. See plans/streaming-markdown-chunking.md.
 */
@Composable
internal fun ChunkedStreamingMarkdown(content: String, modifier: Modifier = Modifier) {
    val latestContent by rememberUpdatedState(content)
    var frozen by remember { mutableStateOf(FrozenMarkdown.EMPTY) }

    // Text that no longer starts with the frozen prefix (regeneration, tool-block restart, think-tag
    // stripping) invalidates it. Derive it at composition time so no stale frame is ever drawn.
    val valid = if (content.startsWith(frozen.prefix)) frozen else FrozenMarkdown.EMPTY
    val tail = content.substring(valid.prefix.length)

    LaunchedEffect(Unit) {
        snapshotFlow { latestContent }.conflate().collect { text ->
            var base = frozen.takeIf { text.startsWith(it.prefix) } ?: FrozenMarkdown.EMPTY
            if (base !== frozen) frozen = base
            while (true) {                                   // a burst may contain several finished blocks
                val current = latestContent
                if (!current.startsWith(base.prefix)) break
                val rest = current.substring(base.prefix.length)
                val cut = stableMarkdownPrefixLength(rest)
                if (cut <= 0) break
                val chunk = rest.substring(0, cut)
                val chunkState = withContext(Dispatchers.Default) { parseLibraryMarkdown(chunk) }
                if (chunkState !is State.Success) break      // keep it in the tail; the final render handles it
                MarkdownParseCache.put(chunk, chunkState)    // must be cached BEFORE the chunk is shown
                val prefix = base.prefix + chunk
                val tailSeed = withContext(Dispatchers.Default) {
                    parseLibraryMarkdown(current.substring(prefix.length))
                }
                if (!latestContent.startsWith(prefix)) break // text changed during the parse
                base = FrozenMarkdown(
                    prefix = prefix,
                    chunks = base.chunks + FrozenChunk(chunk, chunkState),
                    tailSeed = tailSeed.takeIf { it is State.Success }
                )
                frozen = base                                // ONE atomic write: chunk appears, tail shrinks
            }
        }
    }

    Column(modifier.fillMaxWidth()) {                        // no spacing: the library already spaces blocks
        CompositionLocalProvider(LocalStreamingTextFade provides false) {
            valid.chunks.forEachIndexed { index, chunk ->
                key(index, chunk.text) {
                    MarkdownRenderer(content = chunk.text, isStreaming = false, initialParse = chunk.parsed)
                }
            }
        }
        if (tail.isNotBlank()) {
            // Keyed by the frozen state: when a block is frozen the tail gets a new renderer that starts from
            // the parse written with it. Otherwise the old tail parse would still show the frozen block.
            key(valid) {
                MarkdownRenderer(content = tail, isStreaming = true, initialParse = valid.tailSeed)
            }
        }
    }
}
