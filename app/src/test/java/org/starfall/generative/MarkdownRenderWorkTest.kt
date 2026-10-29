package org.starfall.generative

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderWork

class MarkdownRenderWorkTest {
    @Test fun sharedBudgetIncludesSuspendingLibraryWork() = runBlocking {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        (1..20).map {
            async(Dispatchers.Default) {
                MarkdownRenderWork.run {
                    val current = running.incrementAndGet()
                    peak.updateAndGet { maxOf(it, current) }
                    try { delay(5) } finally { running.decrementAndGet() }
                }
            }
        }.awaitAll()
        assertEquals("A dispatcher limit alone would allow concurrent suspended work", 1, peak.get())
    }

    @Test fun cancelledWaiterDoesNotLeakTheBudget() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = launch {
            MarkdownRenderWork.run { entered.complete(Unit); release.await() }
        }
        entered.await()
        val cancelled = launch { MarkdownRenderWork.run { fail("Cancelled work must not run") } }
        yield()
        cancelled.cancelAndJoin()
        release.complete(Unit)
        owner.join()
        assertEquals("available", withTimeout(2_000) { MarkdownRenderWork.run { "available" } })
    }
}
