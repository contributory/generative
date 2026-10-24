package org.starfall.generative

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.shared.model.*
import org.starfall.generative.features.providers.shared.logic.ModelConnectionTests
import org.starfall.generative.features.providers.shared.model.ModelConnectionProgress
import org.starfall.generative.features.providers.shared.model.ModelConnectionStatus
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.provider.ModelProvider

class ModelConnectionTestsTest {
    private val provider = ModelProvider("p", "Provider", ModelApiType.OPENAI, baseUrl = "https://example.test/v1")

    @Test fun testsAtMostFourModelsInParallelAndDeduplicatesPendingRequests() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        var calls = 0
        val tests = ModelConnectionTests(CoroutineScope(coroutineContext + Dispatchers.Unconfined)) { _, id ->
            calls++
            active++
            maximum = maxOf(maximum, active)
            try { gate.await(); Result.success(id) } finally { active-- }
        }
        repeat(6) { tests.test(provider, "model-$it") }
        tests.test(provider, "model-0")
        assertEquals(4, active)
        gate.complete(Unit)
        yield()
        assertEquals(6, calls)
        assertEquals(4, maximum)
        assertEquals(6, tests.results.size)
        assertFalse(tests.running.values.any { it })
    }


    @Test fun testAllRunsStrictlySequentiallyAndBlocksManualTestsDuringBatch() = runBlocking {
        val firstGate = CompletableDeferred<Unit>()
        val firstStarted = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        var active = 0
        var maximum = 0
        val tests = ModelConnectionTests(CoroutineScope(coroutineContext + Dispatchers.Unconfined)) { _, id ->
            calls += id
            active++
            maximum = maxOf(maximum, active)
            try {
                if (id == "model-0") {
                    firstStarted.complete(Unit)
                    firstGate.await()
                }
                Result.success(id)
            } finally {
                active--
            }
        }

        tests.testAll(provider, listOf("model-0", "model-1", "model-2"))
        firstStarted.await()
        assertTrue(tests.batchRunning.value)
        assertEquals(listOf("model-0"), calls)
        assertEquals(1, active)

        tests.test(provider, "manual")
        assertFalse(tests.running["manual"] == true)
        assertFalse("manual" in calls)

        firstGate.complete(Unit)
        withTimeout(1_000) {
            while (tests.batchRunning.value) yield()
        }

        assertEquals(listOf("model-0", "model-1", "model-2"), calls)
        assertEquals(1, maximum)
        assertFalse(tests.running.values.any { it })
    }

    @Test fun cancellationClearsRunningStateAndDoesNotBecomeAConnectionFailure() = runBlocking {
        val job = SupervisorJob()
        val tests = ModelConnectionTests(CoroutineScope(job + Dispatchers.Unconfined)) { _, _ ->
            awaitCancellation()
        }
        tests.test(provider, "pending")
        assertTrue(tests.running["pending"] == true)
        job.cancel()
        yield()
        assertFalse(tests.running["pending"] == true)
        assertFalse(tests.results.containsKey("pending"))
        tests.test(provider, "after-cancellation")
        assertFalse(tests.running.containsKey("after-cancellation"))
    }

    @Test fun blankRepliesBecomeWarningsAndDoNotCountAsTestedFailures() = runBlocking {
        val tests = ModelConnectionTests(CoroutineScope(coroutineContext + Dispatchers.Unconfined)) { _, id ->
            when (id) {
                "empty" -> Result.success("")
                "whitespace" -> Result.success("   ")
                "gone" -> Result.failure(IllegalStateException("HTTP 404"))
                else -> Result.success("OK")
            }
        }
        tests.testAll(provider, listOf("ok", "empty", "whitespace", "gone"))
        assertEquals(ModelConnectionStatus.PASSED, tests.status("ok"))
        assertEquals(ModelConnectionStatus.WARNING, tests.status("empty"))
        assertEquals(ModelConnectionStatus.WARNING, tests.status("whitespace"))
        assertEquals(ModelConnectionStatus.FAILED, tests.status("gone"))
        assertNull(tests.status("unchecked"))

        val models = listOf("ok", "empty", "whitespace", "gone", "unchecked")
        assertEquals(ModelConnectionProgress(tested = 4, total = 5, passed = 1, warnings = 2, failures = 1), tests.progress(models))
        assertEquals(ModelConnectionProgress(tested = 0, total = 1), tests.progress(listOf("unchecked")))
    }
}
