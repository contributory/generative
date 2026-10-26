package org.starfall.generative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.intellij.markdown.ast.ASTNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mikepenz.markdown.model.State
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.logic.markdown.PersistentMarkdownAstCache
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown
import java.nio.file.Files

class PersistentMarkdownAstCacheTest {
    @Test
    fun roundTripRebuildsAstAndReferenceLinksWithoutSerializingMessageText() = runBlocking {
        val root = Files.createTempDirectory("markdown-render-cache").toFile()
        try {
            val cache = PersistentMarkdownAstCache(root)
            val identity = MarkdownRenderCacheIdentity("conversation-1", "message-1")
            val content = "# Header\n\n- one\n- two\n\n```kotlin\nval answer = 42\n```\n\n[manual][docs]\n\n[docs]: https://example.test/guide"
            val parsed = parseLibraryMarkdown(content) as State.Success
            val epoch = cache.captureEpoch(identity)

            assertTrue(cache.store(identity, content, parsed, epoch))
            val payload = cache.entryFileForTest(identity, content).readText()
            assertTrue(payload.contains("schemaVersion"))
            assertFalse("the source body is resolved from the canonical conversation", payload.contains("val answer = 42"))

            val loaded = cache.load(identity, content, cache.captureEpoch(identity))
            assertNotNull(loaded)
            assertEquals(astShape(parsed.node), astShape(loaded!!.node))
            assertEquals("https://example.test/guide", loaded.referenceLinkHandler.find("docs"))
            assertEquals(1L, cache.metrics().hits)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deleteTombstoneRejectsAnOldWriterAndRemovesThePlaintextEntry() = runBlocking {
        val root = Files.createTempDirectory("markdown-render-delete").toFile()
        try {
            val cache = PersistentMarkdownAstCache(root)
            val identity = MarkdownRenderCacheIdentity("conversation-2", "message-2")
            val content = "A body that is stored only in the conversation database."
            val parsed = parseLibraryMarkdown(content) as State.Success
            val oldEpoch = cache.captureEpoch(identity)
            assertTrue(cache.store(identity, content, parsed, oldEpoch))
            val file = cache.entryFileForTest(identity, content)
            assertTrue(file.isFile)

            cache.invalidateMessage(identity.conversationId, identity.messageId)

            assertFalse(cache.store(identity, content, parsed, oldEpoch))
            assertNull(cache.load(identity, content, cache.captureEpoch(identity)))
            withTimeout(5_000L) {
                while (file.exists()) delay(10L)
            }
            assertFalse(file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletionDuringAnInFlightCommitCannotResurrectTheAstEntry() = runBlocking {
        val root = Files.createTempDirectory("markdown-render-delete-race").toFile()
        try {
            val staged = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val cache = PersistentMarkdownAstCache(root).apply {
                beforeCommitForTest = {
                    staged.complete(Unit)
                    release.await()
                }
            }
            val identity = MarkdownRenderCacheIdentity("conversation-race", "message-race")
            val content = "A response parsed before its message was deleted."
            val parsed = parseLibraryMarkdown(content) as State.Success
            val oldEpoch = cache.captureEpoch(identity)
            val store = async { cache.store(identity, content, parsed, oldEpoch) }

            staged.await()
            cache.invalidateMessage(identity.conversationId, identity.messageId)
            release.complete(Unit)

            assertFalse(store.await())
            assertFalse(cache.entryFileForTest(identity, content).exists())
            assertNull(cache.load(identity, content, cache.captureEpoch(identity)))
            withTimeout(5_000L) {
                while (root.listFiles().orEmpty().any { it.name.endsWith(".tmp") }) delay(10L)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun conversationTombstoneRejectsOldWritesThenAllowsCanonicalReopen() = runBlocking {
        val root = Files.createTempDirectory("markdown-render-conversation-delete").toFile()
        try {
            val cache = PersistentMarkdownAstCache(root)
            val identity = MarkdownRenderCacheIdentity("conversation-3", "message-3")
            val content = "A retained message."
            val parsed = parseLibraryMarkdown(content) as State.Success
            val oldEpoch = cache.captureEpoch(identity)
            assertTrue(cache.store(identity, content, parsed, oldEpoch))

            cache.invalidateConversation(identity.conversationId)
            assertNull(cache.load(identity, content, cache.captureEpoch(identity)))
            assertFalse(cache.store(identity, content, parsed, oldEpoch))
            withTimeout(5_000L) {
                while (cache.entryFileForTest(identity, content).exists()) delay(10L)
            }

            cache.authorizeConversation(identity.conversationId, listOf(identity.messageId))
            assertTrue(cache.store(identity, content, parsed, cache.captureEpoch(identity)))
            assertNotNull(cache.load(identity, content, cache.captureEpoch(identity)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptPayloadIsRemovedAndFallsBackToAMiss() = runBlocking {
        val root = Files.createTempDirectory("markdown-render-corrupt").toFile()
        try {
            val cache = PersistentMarkdownAstCache(root)
            val identity = MarkdownRenderCacheIdentity("conversation-corrupt", "message-corrupt")
            val content = "A valid response before the cache file is corrupted."
            val parsed = parseLibraryMarkdown(content) as State.Success
            assertTrue(cache.store(identity, content, parsed, cache.captureEpoch(identity)))
            val file = cache.entryFileForTest(identity, content)
            file.writeText("not a valid Markdown AST payload")

            assertNull(cache.load(identity, content, cache.captureEpoch(identity)))
            assertFalse(file.exists())
            assertEquals(1L, cache.metrics().corruptEntries)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun diskBudgetEvictsAnEntryWithoutTouchingCanonicalText() = runBlocking {
        val root = Files.createTempDirectory("markdown-render-budget").toFile()
        try {
            val cache = PersistentMarkdownAstCache(root, maxBytes = 1L)
            val identity = MarkdownRenderCacheIdentity("conversation-4", "message-4")
            val content = "A cache entry larger than one byte."
            val parsed = parseLibraryMarkdown(content) as State.Success

            assertTrue(cache.store(identity, content, parsed, cache.captureEpoch(identity)))
            assertFalse(cache.entryFileForTest(identity, content).exists())
            assertEquals(1L, cache.metrics().evictions)
            assertNull(cache.load(identity, content, cache.captureEpoch(identity)))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun astShape(node: ASTNode): String = buildString {
        fun appendNode(current: ASTNode) {
            append('(').append(current.type.name).append(':')
                .append(current.startOffset).append('-').append(current.endOffset)
            current.children.forEach(::appendNode)
            append(')')
        }
        appendNode(node)
    }
}
