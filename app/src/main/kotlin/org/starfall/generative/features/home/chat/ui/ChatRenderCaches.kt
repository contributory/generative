package org.starfall.generative.features.home.chat.ui

import android.content.ComponentCallbacks2
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import com.mikepenz.markdown.model.State
import java.lang.ref.SoftReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import org.starfall.generative.features.home.chat.ui.markdown.LatexCacheKey
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.ui.markdown.LatexDrawables

/**
 * RAM that only exists to make reopening a chat faster. Everything registered here is low priority:
 * the garbage collector may drop the values at any time (they are held by [SoftReference]) and the app
 * empties the caches itself when Android reports memory pressure ([onTrimMemory]).
 */
internal interface EvictableCache {
    /** Keep at most [fraction] (0..1) of the capacity; 0 empties the cache. */
    fun trimToFraction(fraction: Float)
}

internal object ChatRenderCaches {
    private val caches = CopyOnWriteArrayList<EvictableCache>()

    fun register(cache: EvictableCache) {
        caches.addIfAbsent(cache)
    }

    fun clearAll() = caches.forEach { it.trimToFraction(0f) }

    /**
     * Called from [android.content.ComponentCallbacks2.onTrimMemory]. Going to the background
     * ([ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN]) is normal and keeps the cache; any real pressure
     * (running low/critical, or the process being a candidate to be killed) empties it.
     */
    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        val fraction = when {
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> return
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> 0f
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> 0.5f
            else -> return
        }
        caches.forEach { it.trimToFraction(fraction) }
    }

    /** True when [key] can be drawn on the first frame without waiting for any background work. */
    fun isReady(key: AssetKey): Boolean = when (key) {
        is AssetKey.Markdown -> MarkdownParseCache.contains(key.content, key.identity)
        is AssetKey.Latex -> LatexDrawables.peek(key.key) != null
        is AssetKey.Attachment -> AttachmentPreviewCache.contains(key.reference)
    }
}

/**
 * LRU cache bounded by a caller-defined weight whose values are only softly reachable, so it never
 * competes with the rest of the app for memory.
 */
internal class SoftLruCache<K : Any, V : Any>(maxWeight: Int) : EvictableCache {
    private class Entry<V : Any>(value: V, val weight: Int) {
        val ref = SoftReference(value)
    }

    private val lru = object : LruCache<K, Entry<V>>(maxWeight) {
        // Fixed when the entry is stored: LruCache requires sizeOf to stay constant, and a soft
        // reference may be cleared at any time.
        override fun sizeOf(key: K, value: Entry<V>): Int = value.weight
    }

    init {
        ChatRenderCaches.register(this)
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = lru.get(key) ?: return null
        return entry.ref.get() ?: run {
            lru.remove(key)
            null
        }
    }

    @Synchronized
    fun contains(key: K): Boolean = get(key) != null

    @Synchronized
    fun put(key: K, value: V, weight: Int) {
        lru.put(key, Entry(value, weight.coerceAtLeast(1)))
    }

    @Synchronized
    fun removeIf(predicate: (K) -> Boolean) {
        lru.snapshot().keys.filter(predicate).forEach(lru::remove)
    }

    @Synchronized
    override fun trimToFraction(fraction: Float) {
        if (fraction <= 0f) lru.evictAll() else lru.trimToSize((lru.maxSize() * fraction).toInt())
    }
}

/** Parsed Markdown keyed to one message/block version, with a small content-only slot for standalone renderers. */
internal data class MarkdownParseCacheMetrics(
    val hits: Long,
    val misses: Long,
    val writes: Long,
    val fullParseCount: Long,
    val fullParseChars: Long,
    val fullParseNanos: Long
)

internal object MarkdownParseCache {
    private data class Key(val content: String, val identity: MarkdownRenderCacheIdentity?)
    private data class Entry(val state: State.Success, val contentFingerprint: String?)

    // Weighed in source characters; the parsed tree is several times larger.
    private const val MAX_CHARS = 600_000
    private val cache = SoftLruCache<Key, Entry>(MAX_CHARS)
    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val writes = AtomicLong()
    private val fullParseCount = AtomicLong()
    private val fullParseChars = AtomicLong()
    private val fullParseNanos = AtomicLong()

    fun get(content: String, identity: MarkdownRenderCacheIdentity? = null): State.Success? =
        cache.get(Key(content, identity))?.state.also { if (it == null) misses.incrementAndGet() else hits.incrementAndGet() }

    fun getFingerprint(content: String, identity: MarkdownRenderCacheIdentity? = null): String? =
        cache.get(Key(content, identity))?.contentFingerprint

    fun contains(content: String, identity: MarkdownRenderCacheIdentity? = null): Boolean =
        cache.contains(Key(content, identity))

    fun put(
        content: String,
        state: State,
        identity: MarkdownRenderCacheIdentity? = null,
        contentFingerprint: String? = null
    ) {
        if (state is State.Success && state.content == content) {
            cache.put(Key(content, identity), Entry(state, contentFingerprint), content.length)
            writes.incrementAndGet()
        }
    }

    fun recordFullParse(chars: Int, durationNanos: Long) {
        fullParseCount.incrementAndGet()
        fullParseChars.addAndGet(chars.toLong())
        fullParseNanos.addAndGet(durationNanos.coerceAtLeast(0L))
    }

    fun metrics(): MarkdownParseCacheMetrics = MarkdownParseCacheMetrics(
        hits = hits.get(), misses = misses.get(), writes = writes.get(),
        fullParseCount = fullParseCount.get(), fullParseChars = fullParseChars.get(),
        fullParseNanos = fullParseNanos.get()
    )

    internal fun resetMetricsForTests() {
        hits.set(0); misses.set(0); writes.set(0)
        fullParseCount.set(0); fullParseChars.set(0); fullParseNanos.set(0)
    }

    fun invalidateMessage(conversationId: String, messageId: String) {
        cache.removeIf { key ->
            key.identity?.let { it.conversationId == conversationId && it.messageId == messageId } == true
        }
    }

    fun invalidateConversation(conversationId: String) {
        cache.removeIf { key -> key.identity?.conversationId == conversationId }
    }

    fun clearAll() = cache.trimToFraction(0f)
}

/** Content-free recomposition counters used by profiling. */
internal object ChatRenderMetrics {
    private val screenRecompositions = AtomicLong()
    private val messageRecompositions = AtomicLong()

    fun recordChatScreenRecomposition() { screenRecompositions.incrementAndGet() }
    fun recordMessageCardRecomposition() { messageRecompositions.incrementAndGet() }

    internal fun messageRecompositionCountForTests(): Long = messageRecompositions.get()

    internal fun resetForTests() {
        screenRecompositions.set(0)
        messageRecompositions.set(0)
    }
}

internal sealed interface AssetKey {
    data class Markdown(val content: String, val identity: MarkdownRenderCacheIdentity? = null) : AssetKey
    data class Latex(val key: LatexCacheKey) : AssetKey
    data class Attachment(val reference: String) : AssetKey
}

/**
 * Remembers which conversations were last seen settled at their newest message, together with the
 * assets that were on screen. Reopening one of them skips the loading veil when every one of those
 * assets is still cached and nothing that affects layout has changed.
 */
internal object ChatOpenCache {
    private const val MAX_CONVERSATIONS = 24

    private class Entry(val signature: Int, val environment: Int, val assets: List<AssetKey>)

    private val cache = SoftLruCache<String, Entry>(MAX_CONVERSATIONS)

    fun isWarm(conversationId: String, signature: Int, environment: Int): Boolean {
        val entry = cache.get(conversationId) ?: return false
        return entry.signature == signature &&
            entry.environment == environment &&
            entry.assets.all(ChatRenderCaches::isReady)
    }

    fun remember(conversationId: String, signature: Int, environment: Int, assets: List<AssetKey>) {
        cache.put(conversationId, Entry(signature, environment, assets), 1)
    }
}

/**
 * Counts the background work (Markdown parsing, formulas, previews) of the items on screen and keeps
 * the assets that are ready, so the chat can tell when its layout has stopped changing.
 */
@Stable
internal class ChatSettleTracker {
    var pending by mutableIntStateOf(0)
        private set

    private val retained = LinkedHashMap<AssetKey, Int>()

    fun beginPending() {
        pending += 1
    }

    fun endPending() {
        pending = (pending - 1).coerceAtLeast(0)
    }

    fun retain(key: AssetKey) {
        retained[key] = (retained[key] ?: 0) + 1
    }

    fun release(key: AssetKey) {
        val count = (retained[key] ?: return) - 1
        if (count <= 0) retained.remove(key) else retained[key] = count
    }

    fun readyAssets(): List<AssetKey> = retained.keys.toList()
}

internal val LocalChatSettleTracker = staticCompositionLocalOf<ChatSettleTracker?> { null }

/** While [pending] is true the surrounding chat is told that this item's size is not final yet. */
@Composable
internal fun TrackPendingWork(pending: Boolean) {
    val tracker = LocalChatSettleTracker.current ?: return
    if (pending) {
        DisposableEffect(tracker) {
            tracker.beginPending()
            onDispose { tracker.endPending() }
        }
    }
}

/** Reports assets that are on screen in their final form, for [ChatOpenCache]. */
@Composable
internal fun TrackReadyAssets(assets: List<AssetKey>) {
    val tracker = LocalChatSettleTracker.current ?: return
    if (assets.isEmpty()) return
    DisposableEffect(tracker, assets) {
        assets.forEach(tracker::retain)
        onDispose { assets.forEach(tracker::release) }
    }
}
