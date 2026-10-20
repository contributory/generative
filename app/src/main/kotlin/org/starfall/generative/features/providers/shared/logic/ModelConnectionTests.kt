package org.starfall.generative.features.providers.shared.logic

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.starfall.generative.features.providers.shared.logic.isEmpty
import org.starfall.generative.shared.model.provider.ModelProvider

internal class ModelConnectionTests(
    private val scope: CoroutineScope,
    private val connect: suspend (ModelProvider, String) -> Result<String>
) {
    val running = mutableStateMapOf<String, Boolean>()
    val results = mutableStateMapOf<String, Result<String>>()
    val batchRunning = mutableStateOf(false)
    private val jobs = mutableMapOf<String, Job>()
    private var batchJob: Job? = null
    private var batchRevision = 0L

    // Manual per-model tests may run in parallel, but Test All is deliberately serialized.
    private val manualSemaphore = Semaphore(4)
    private val revisions = mutableMapOf<String, Long>()
    private fun revision(id: String): Long = revisions[id] ?: 0L

    fun test(provider: ModelProvider, modelId: String) {
        if (!scope.isActive || batchRunning.value || running[modelId] == true) return
        running[modelId] = true
        val runRevision = revision(modelId)
        results.remove(modelId)
        val job = scope.launch {
            try {
                val result = manualSemaphore.withPermit {
                    connectSafely(provider, modelId)
                }
                if (revision(modelId) == runRevision) results[modelId] = result
            } finally {
                if (revision(modelId) == runRevision) running[modelId] = false
            }
        }
        jobs[modelId] = job
    }

    fun testAll(
        provider: ModelProvider,
        modelIds: Collection<String>,
        skip: (String) -> Boolean = { false }
    ) {
        if (!scope.isActive || batchRunning.value || running.values.any { it }) return
        val ids = modelIds.distinct()
        val batchRevisions = ids.associateWith(::revision)
        if (ids.isEmpty()) return

        batchRunning.value = true
        val runBatchRevision = ++batchRevision
        ids.forEach(results::remove)
        val job = scope.launch {
            try {
                for (modelId in ids) {
                    ensureActive()
                    // Re-checked per model: it may have been marked for removal after the batch started.
                    if (skip(modelId)) continue
                    val expected = batchRevisions.getValue(modelId)
                    if (revision(modelId) != expected) continue
                    running[modelId] = true
                    // Each model runs in its own child job so cancelling one doesn't stop the batch.
                    // Intentionally one request at a time to avoid provider rate-limit bursts.
                    val child = launch {
                        try {
                            val result = connectSafely(provider, modelId)
                            if (revision(modelId) == expected) results[modelId] = result
                        } finally {
                            if (revision(modelId) == expected) running[modelId] = false
                        }
                    }
                    jobs[modelId] = child
                    child.join()
                    jobs.remove(modelId)
                }
            } finally {
                if (batchRevision == runBatchRevision) batchRunning.value = false
            }
        }
        batchJob = job
    }

    fun cancel(modelId: String) {
        revisions[modelId] = revision(modelId) + 1
        jobs.remove(modelId)?.cancel()
        running.remove(modelId)
    }
    fun cancelAll() {
        batchRevision++
        batchJob?.cancel(); batchJob = null
        jobs.values.toList().forEach { it.cancel() }; jobs.clear()
        running.keys.toList().forEach { revisions[it] = revision(it) + 1 }
        running.clear(); batchRunning.value = false
    }

    private suspend fun connectSafely(provider: ModelProvider, modelId: String): Result<String> =
        try {
            val result = connect(provider, modelId)
            currentCoroutineContext().ensureActive()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun forget(ids: Collection<String>) {
        ids.forEach {
            revisions[it] = revision(it) + 1
            running.remove(it)
            results.remove(it)
        }
    }
}
