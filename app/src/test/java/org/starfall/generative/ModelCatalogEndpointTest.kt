package org.starfall.generative

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.integrations.modelapis.ModelCatalogAdapter
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.provider.ModelProvider
import java.util.concurrent.TimeUnit

class ModelCatalogEndpointTest {
    @Test fun ollamaUsesTagsAndHonoursCustomCatalog() = runBlocking {
        MockWebServer().use { server ->
            HttpClient(OkHttp).use { client ->
                val adapter = ModelCatalogAdapter(client, Json)
                val provider = ModelProvider(id = "ollama", name = "Ollama", type = ModelApiType.OLLAMA,
                    baseUrl = server.url("/api").toString())
                val response = """{"models":[{"name":"llama:latest","details":{"parameter_size":"8B"}},{"name":"gemma:latest"}]}"""
                server.enqueue(MockResponse().setBody(response))
                val models = adapter.fetch(provider)
                assertEquals(listOf("llama:latest", "gemma:latest"), models.map { it.id })
                assertEquals("8B", models.first().metadata["details"]!!.jsonObject["parameter_size"]!!.jsonPrimitive.content)
                assertEquals("/api/tags", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
                assertEquals(1, server.requestCount)
                server.enqueue(MockResponse().setBody(response))
                val custom = provider.copy(config = provider.config.copy(customEndpoints = mapOf("catalog" to server.url("/custom/models").toString())))
                assertEquals(2, adapter.fetch(custom).size)
                assertEquals("/custom/models", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
                assertEquals(2, server.requestCount)
            }
        }
    }
}
