package org.starfall.multigateway

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.integrations.modelapis.AiService
import java.util.concurrent.TimeUnit
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderConfiguration

@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class ConfigurationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val messages = listOf(StoredMessage("u", ChatRole.USER, listOf(MessageVersion(content = "Hello"))))

    @Test fun oldProfileKeepsSystemPromptAndDefaultsMcpPermissions() {
        val profile = json.decodeFromString<ChatProfile>("""{"id":"p","name":"Old","config":{"system_prompt":"Keep me","temperature":0.9,"top_p":0.7,"max_tokens":123},"active_mcp":[]}""")
        assertEquals("Keep me", profile.config.systemPrompt)
        val saved = json.parseToJsonElement(json.encodeToString(profile)).jsonObject
        assertEquals(setOf("system_prompt", "mcpAccess"), saved["config"]!!.jsonObject.keys)
        assertTrue(profile.config.mcpAccess.isEmpty())
        assertFalse(saved.containsKey("active_mcp"))
    }

    @Test fun providerAndModelConfigSurviveStorageRoundTrip() {
        val config = ProviderConfiguration(maxTokens = 123, supportStream = false, headers = mapOf("X-Test" to "yes"),
            modelConfigs = mapOf("custom/a" to ModelConfiguration(0.4, 0.8, 12), "custom/b" to ModelConfiguration()))
        assertEquals(config, json.decodeFromString<ProviderConfiguration>(json.encodeToString(config)))
        assertEquals(4000, json.decodeFromString<ProviderConfiguration>("{}").maxTokens)
    }

    @Test fun modelSwitchAndProviderSwitchUseSeparateSamplingAndRequestSettings() = runBlocking {
        val service = AiService(context)
        MockWebServer().use { server ->
            val provider = ModelProvider("p1", "First", ModelApiType.OPENAI, Authorization(key = "test"),
                baseUrl = server.url("/v1").toString(), config = ProviderConfiguration(maxTokens = 123,
                    headers = mapOf("X-Request" to "first"), modelConfigs = mapOf("a" to ModelConfiguration(0.2, 0.6))))
            val other = provider.copy(id = "p2", config = ProviderConfiguration(maxTokens = 456,
                modelConfigs = mapOf("a" to ModelConfiguration(0.8, 0.9))))
            for ((p, model, temp, limit) in listOf(
                Case(provider, "a", 0.2, 123), Case(provider, "b", null, 123), Case(other, "a", 0.8, 456)
            )) {
                server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: [DONE]\n\n"))
                service.streamContent(p, model, messages, "Prompt").toList()
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                assertEquals(model, body["model"]!!.jsonPrimitive.content)
                assertEquals(limit, body["max_tokens"]!!.jsonPrimitive.int)
                assertEquals(temp, body["temperature"]?.jsonPrimitive?.double)
                assertEquals(p.config.headers["X-Request"], request.getHeader("X-Request"))
                if (temp == null) assertFalse(body.containsKey("top_p"))
            }
        }
    }

    private data class Case(val provider: ModelProvider, val model: String, val temperature: Double?, val limit: Int)

    @Test fun nonStreamingAndTopKReachNativeApis() = runBlocking {
        val service = AiService(context)
        val responses = mapOf(
            ModelApiType.OPENAI to """{"id":"c","object":"chat.completion","created":1,"model":"custom","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}""",
            ModelApiType.ANTHROPIC to """{"id":"m","type":"message","role":"assistant","model":"custom","content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}""",
            ModelApiType.GOOGLE to """{"candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]}}]}""",
            ModelApiType.OLLAMA to """{"message":{"role":"assistant","content":"ok"},"done":true}"""
        )
        for ((type, response) in responses) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(response))
                val provider = ModelProvider("p", "Provider", type, Authorization(key = "test"),
                    baseUrl = server.url(if (type == ModelApiType.GOOGLE) "/v1beta" else "/v1").toString(),
                    config = ProviderConfiguration(supportStream = true, maxTokens = 37,
                        modelConfigs = mapOf("custom" to ModelConfiguration(0.3, 0.7, 9, supportStream = false))))
                assertEquals(type.name, "ok", service.streamContent(provider, "custom", messages).toList().joinToString(""))
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                when (type) {
                    ModelApiType.GOOGLE -> {
                        assertTrue(request.path!!.contains(":generateContent"))
                        val config = body["generationConfig"]!!.jsonObject
                        assertEquals(9.0, config["topK"]!!.jsonPrimitive.double, 0.0)
                        assertEquals(37, config["maxOutputTokens"]!!.jsonPrimitive.int)
                    }
                    ModelApiType.OLLAMA -> {
                        assertFalse(body["stream"]!!.jsonPrimitive.boolean)
                        assertEquals(9, body["options"]!!.jsonObject["top_k"]!!.jsonPrimitive.int)
                        assertEquals(37, body["options"]!!.jsonObject["num_predict"]!!.jsonPrimitive.int)
                    }
                    else -> {
                        assertNotEquals(true, body["stream"]?.jsonPrimitive?.boolean)
                        assertEquals(37, body["max_tokens"]!!.jsonPrimitive.int)
                        if (type == ModelApiType.ANTHROPIC) assertEquals(9, body["top_k"]!!.jsonPrimitive.int)
                        else assertFalse(body.containsKey("top_k"))
                    }
                }
            }
        }
    }
    @Test fun modelStreamOverrideTakesPriorityAndPersists() {
        for (providerValue in listOf(false, true)) {
            for (modelValue in listOf<Boolean?>(null, false, true)) {
                val provider = ModelProvider("p", "P", ModelApiType.OPENAI, baseUrl = "https://example.test",
                    config = ProviderConfiguration(supportStream = providerValue,
                        modelConfigs = mapOf("a" to ModelConfiguration(supportStream = modelValue))))
                val restored = json.decodeFromString<ModelProvider>(json.encodeToString(provider))
                assertEquals(modelValue ?: providerValue, restored.streamEnabledFor("a"))
                assertEquals(providerValue, restored.streamEnabledFor("other-model"))
            }
        }
        assertNull(json.decodeFromString<ModelConfiguration>("{}").supportStream)
    }

    @Test fun modelCanEnableStreamingWhenProviderDisablesIt() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: [DONE]\n\n"))
            val provider = ModelProvider("p", "P", ModelApiType.OPENAI, Authorization(key = "test"),
                baseUrl = server.url("/v1").toString(), config = ProviderConfiguration(supportStream = false,
                    modelConfigs = mapOf("custom" to ModelConfiguration(supportStream = true))))
            AiService(context).streamContent(provider, "custom", messages).toList()
            val body = json.parseToJsonElement(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).jsonObject
            assertTrue(body["stream"]!!.jsonPrimitive.boolean)
            assertFalse(provider.config.supportStream)
        }
    }

}
