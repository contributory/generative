package org.starfall.generative

import android.content.ComponentCallbacks2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import org.starfall.generative.features.home.chat.ui.AssetKey
import org.starfall.generative.features.home.chat.ui.ChatOpenCache
import org.starfall.generative.features.home.chat.ui.ChatRenderCaches
import org.starfall.generative.features.home.chat.ui.ChatSettleTracker
import org.starfall.generative.features.home.chat.ui.MarkdownParseCache
import org.starfall.generative.features.home.chat.ui.MarkdownBlockHeightCache
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.logic.markdown.PersistentMarkdownAstCache
import java.nio.file.Files
import org.starfall.generative.features.home.chat.ui.SoftLruCache

@RunWith(RobolectricTestRunner::class)
class ChatRenderCachesTest {
    @Before fun cold() {
        ChatRenderCaches.clearAll()
        MarkdownParseCache.resetMetricsForTests()
        MarkdownBlockHeightCache.resetForTests()
    }

    private fun parse(text: String): State = runBlocking { parseMarkdown(text) }

    @Test fun lruDropsTheLeastRecentlyUsedEntryWhenOverWeight() {
        val cache = SoftLruCache<String, String>(maxWeight = 10)
        cache.put("a", "A", 4)
        cache.put("b", "B", 4)
        assertNotNull(cache.get("a"))          // "a" is now the most recent
        cache.put("c", "C", 4)                 // 12 > 10: "b" goes
        assertEquals("A", cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals("C", cache.get("c"))
    }

    @Test fun trimmingKeepsAFractionAndZeroEmptiesEverything() {
        val cache = SoftLruCache<Int, Int>(maxWeight = 10)
        (1..10).forEach { cache.put(it, it, 1) }
        cache.trimToFraction(0.5f)
        assertFalse(cache.contains(1))
        assertTrue(cache.contains(10))
        cache.trimToFraction(0f)
        assertFalse(cache.contains(10))
    }

    @Test fun parsedMarkdownIsReusedUntilMemoryRunsLow() {
        val text = "# Title\n\nSome **bold** text."
        MarkdownParseCache.put(text, parse(text))
        assertTrue(MarkdownParseCache.contains(text))
        // Going to the background is normal and keeps the cache.
        ChatRenderCaches.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertTrue(MarkdownParseCache.contains(text))
        ChatRenderCaches.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertFalse(MarkdownParseCache.contains(text))
    }

    @Test fun parseMetricsSeparateMemoryReuseFromFullParseFallbacks() {
        val text = "# Metrics"
        MarkdownParseCache.put(text, parse(text))
        assertNotNull(MarkdownParseCache.get(text))
        assertNull(MarkdownParseCache.get("missing"))
        MarkdownParseCache.recordFullParse(chars = 123, durationNanos = 456L)

        val metrics = MarkdownParseCache.metrics()
        assertEquals(1L, metrics.hits)
        assertEquals(1L, metrics.misses)
        assertEquals(1L, metrics.writes)
        assertEquals(1L, metrics.fullParseCount)
        assertEquals(123L, metrics.fullParseChars)
        assertEquals(456L, metrics.fullParseNanos)
    }

    @Test fun aParseOfDifferentTextIsNeverStoredUnderTheWrongKey() {
        MarkdownParseCache.put("other", parse("text"))
        assertNull(MarkdownParseCache.get("other"))
    }

    @Test fun contentFingerprintLivesAndEvictsWithItsParsedTree() {
        val identity = MarkdownRenderCacheIdentity("conversation", "fingerprinted")
        val text = "# Fingerprinted"
        MarkdownParseCache.put(text, parse(text), identity, contentFingerprint = "sha256:fixture")

        assertEquals("sha256:fixture", MarkdownParseCache.getFingerprint(text, identity))
        assertNull(MarkdownParseCache.getFingerprint(text))

        MarkdownParseCache.invalidateMessage(identity.conversationId, identity.messageId)
        assertNull(MarkdownParseCache.getFingerprint(text, identity))
    }

    @Test fun aChatIsWarmOnlyWhileItsAssetsSignatureAndEnvironmentMatch() {
        val text = "Answer"
        MarkdownParseCache.put(text, parse(text))
        ChatOpenCache.remember("chat", signature = 7, environment = 1, assets = listOf(AssetKey.Markdown(text)))
        assertTrue(ChatOpenCache.isWarm("chat", 7, 1))
        assertFalse("changed messages", ChatOpenCache.isWarm("chat", 8, 1))
        assertFalse("changed font/theme/width", ChatOpenCache.isWarm("chat", 7, 2))
        assertFalse("never opened", ChatOpenCache.isWarm("other", 7, 1))
        MarkdownParseCache.get(text)
        ChatRenderCaches.clearAll()
        assertFalse("everything was evicted", ChatOpenCache.isWarm("chat", 7, 1))
    }

    @Test fun aChatWhoseParsedContentWasEvictedIsNotWarm() {
        val text = "Answer"
        ChatOpenCache.remember("chat", 1, 1, listOf(AssetKey.Markdown(text)))
        assertFalse(ChatOpenCache.isWarm("chat", 1, 1))
    }

    @Test fun messageDeletionInvalidatesOnlyThatMessageInTheMemoryAstCache() {
        val root = Files.createTempDirectory("markdown-memory-invalidation").toFile()
        try {
            val cache = PersistentMarkdownAstCache(root)
            val firstIdentity = MarkdownRenderCacheIdentity("conversation", "first")
            val secondIdentity = MarkdownRenderCacheIdentity("conversation", "second")
            val firstContent = "First message"
            val secondContent = "Second message"
            MarkdownParseCache.put(firstContent, parse(firstContent), firstIdentity)
            MarkdownParseCache.put(secondContent, parse(secondContent), secondIdentity)
            MarkdownBlockHeightCache.put("${firstIdentity.conversationId}|${firstIdentity.messageId}|block|width-320", 42)
            MarkdownBlockHeightCache.put("${secondIdentity.conversationId}|${secondIdentity.messageId}|block|width-320", 58)
            cache.registerMemoryInvalidator(MarkdownParseCache) { conversationId, messageId ->
                when {
                    conversationId == null -> MarkdownParseCache.clearAll()
                    messageId == null -> MarkdownParseCache.invalidateConversation(conversationId)
                    else -> MarkdownParseCache.invalidateMessage(conversationId, messageId)
                }
            }
            cache.registerMemoryInvalidator(MarkdownBlockHeightCache) { conversationId, messageId ->
                when {
                    conversationId == null -> MarkdownBlockHeightCache.clearAll()
                    messageId == null -> MarkdownBlockHeightCache.invalidateConversation(conversationId)
                    else -> MarkdownBlockHeightCache.invalidateMessage(conversationId, messageId)
                }
            }

            cache.invalidateMessage(firstIdentity.conversationId, firstIdentity.messageId)

            assertNull(MarkdownParseCache.get(firstContent, firstIdentity))
            assertNotNull(MarkdownParseCache.get(secondContent, secondIdentity))
            assertNull(MarkdownBlockHeightCache.get("${firstIdentity.conversationId}|${firstIdentity.messageId}|block|width-320"))
            assertEquals(58, MarkdownBlockHeightCache.get("${secondIdentity.conversationId}|${secondIdentity.messageId}|block|width-320") ?: -1)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun markdownBlockHeightsAreCachedSeparatelyForDifferentLayoutProfiles() {
        MarkdownBlockHeightCache.resetForTests()

        MarkdownBlockHeightCache.put("message/block/content/width-320/font-1000", 42)
        MarkdownBlockHeightCache.put("message/block/content/width-400/font-1000", 58)

        assertEquals(42, MarkdownBlockHeightCache.get("message/block/content/width-320/font-1000") ?: -1)
        assertEquals(58, MarkdownBlockHeightCache.get("message/block/content/width-400/font-1000") ?: -1)
        assertNull(MarkdownBlockHeightCache.get("message/block/content/width-320/font-1250"))
        assertEquals(2L, MarkdownBlockHeightCache.metrics().hits)
        assertEquals(1L, MarkdownBlockHeightCache.metrics().misses)
    }

    @Test fun trackerCountsPendingWorkAndRetainsAssetsOnce() {
        val tracker = ChatSettleTracker()
        tracker.beginPending(); tracker.beginPending()
        assertEquals(2, tracker.pending)
        tracker.endPending(); tracker.endPending(); tracker.endPending()
        assertEquals("never negative", 0, tracker.pending)

        val key = AssetKey.Markdown("x")
        tracker.retain(key); tracker.retain(key)
        assertEquals(listOf(key), tracker.readyAssets())
        tracker.release(key)
        assertEquals(listOf(key), tracker.readyAssets())
        tracker.release(key)
        assertTrue(tracker.readyAssets().isEmpty())
    }
}
