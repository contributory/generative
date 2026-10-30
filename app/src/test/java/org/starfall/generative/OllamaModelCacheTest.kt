package org.starfall.generative

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.integrations.modelapis.AiService
import org.starfall.generative.integrations.modelapis.ollama.OllamaModelCache
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OllamaModelCacheTest {
    @Test fun existingCacheIsMigratedWithoutLosingModelsOrTimestamp() = runBlocking {
        val url = "https://legacy-ollama.example/v1/"
        val key = MessageDigest.getInstance("SHA-256")
            .digest(url.trim().trimEnd('/').toByteArray()).joinToString("") { "%02x".format(it) }
        context.getSharedPreferences("ollama-model-catalog", Context.MODE_PRIVATE).edit()
            .putString(key, """{"models":["legacy-model"],"fetchedAt":1234}""").commit()
        val directory = Files.createTempDirectory("ollama-cache-migration").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "ollama-model-catalog")),
                scope = scope,
                produceFile = { File(directory, "catalog.preferences_pb") }
            )
            val entry = OllamaModelCache(store).read(url)
            assertEquals(listOf("legacy-model"), entry?.models)
            assertEquals(1234L, entry?.fetchedAt)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun reopeningUsesPersistedModelsWithoutAnotherNetworkRequest() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"models":[{"name":"local-model"}]}"""))
            val url = server.url("/").toString()
            assertEquals(listOf("local-model"), AiService(context).fetchOllamaModels(url))
            assertEquals(listOf("local-model"), AiService(context).fetchOllamaModels(url))
            assertEquals(1, server.requestCount)
            assertEquals(listOf("local-model"), OllamaModelCache(context).read(url)?.models)
        } finally { server.shutdown() }
    }

    @Test fun staleModelsSurviveOfflineRefreshAndEndpointsStaySeparate() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val url = server.url("/").toString()
            val cache = OllamaModelCache(context, now = { 0 })
            cache.write(url, listOf("previous-model"))
            assertFalse(OllamaModelCache(context).isFresh(cache.read(url)!!))
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(listOf("previous-model"), AiService(context).fetchOllamaModels(url))
            assertNull(cache.read(server.url("/other/").toString()))
        } finally { server.shutdown() }
    }

    @Test fun successfulEmptyDiscoveryClearsRemovedModels() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val url = server.url("/").toString()
            OllamaModelCache(context, now = { 0 }).write(url, listOf("removed-model"))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"models":[]}"""))
            assertTrue(AiService(context).fetchOllamaModels(url).isEmpty())
            assertTrue(OllamaModelCache(context).read(url)!!.models.isEmpty())
        } finally { server.shutdown() }
    }
}
