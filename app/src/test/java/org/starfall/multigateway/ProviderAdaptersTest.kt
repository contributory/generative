package org.starfall.multigateway

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.starfall.multigateway.shared.model.*
import java.util.concurrent.TimeUnit
import org.starfall.multigateway.shared.model.auth.AuthMethod
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.language.SELECTABLE_REASONING_EFFORTS

@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class ProviderAdaptersTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val adapters by lazy { ProviderAdaptersTestSupport(context) }
    private val messages = listOf(StoredMessage(id = "1", role = ChatRole.USER,
        versions = listOf(MessageVersion(content = "Hello"))))

    private fun provider(server: MockWebServer, type: ModelApiType, key: String = "test-key") = ModelProvider(
        id = key, name = "Test", type = type,
        baseUrl = server.url(if (type == ModelApiType.GOOGLE) "/v1beta" else "/v1").toString(),
        auth = Authorization(key = key))

    private fun sse(data: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(data)
    private fun openAiResponse(text: String) = sse("data: {\"id\":\"chat-test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"custom-model\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$text\"}}]}\n\ndata: [DONE]\n\n")

    @Test fun runtimeSwitchKeepsEndpointAndCredentialsSeparate() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.enqueue(openAiResponse("first")); second.enqueue(openAiResponse("second"))
            for ((server, key, expected) in listOf(Triple(first, "key-a", "first"), Triple(second, "key-b", "second"))) {
                val result = adapters.streamOpenAi(provider(server, ModelApiType.OPENAI, key), "custom-model", messages, "system", 0.5, 0.9, 99).toList()
                assertEquals(expected, result.filterIsInstance<GenerationEvent.Text>().joinToString("") { it.text })
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("/v1/chat/completions", request.path)
                assertEquals("Bearer $key", request.getHeader("Authorization"))
                assertTrue(request.body.readUtf8().contains("custom-model"))
            }
        } }
    }

    @Test fun queryAuthDoesNotInventBearerCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(openAiResponse("ok"))
            val provider = provider(server, ModelApiType.OPENAI).copy(auth = Authorization(AuthMethod.QUERY_PARAM, "token", "secret"))
            adapters.streamOpenAi(provider, "custom", messages, "", null, null, 50).toList()
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("secret", request.requestUrl!!.queryParameter("token"))
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test fun anthropicUsesNativePathAndParsesSdkEvents() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(sse("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\nevent: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"))
            assertEquals("hello", adapters.streamAnthropic(provider(server, ModelApiType.ANTHROPIC), "custom-claude", messages, "", null, null, 50)
                .toList().filterIsInstance<GenerationEvent.Text>().joinToString("") { it.text })
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/messages", request.path)
            assertEquals("test-key", request.getHeader("x-api-key"))
        }
    }

    @Test fun anthropicEffortUsesAdaptiveThinkingOnTheWire() = runBlocking {
        MockWebServer().use { server ->
            val model = "claude-opus-4-6"
            server.enqueue(sse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"))
            val base = provider(server, ModelApiType.ANTHROPIC)
            val configured = base.copy(config = base.config.copy(modelConfigs = mapOf(model to
                ModelConfiguration(supportsThinking = true, reasoningEffort = "xhigh",
                    reasoningEfforts = SELECTABLE_REASONING_EFFORTS))))
            adapters.streamAnthropic(configured, model, messages, "", 0.7, 0.9, 512, 40).toList()
            val body = Json.parseToJsonElement(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).jsonObject
            assertEquals("xhigh", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertFalse(body["thinking"]!!.jsonObject.containsKey("budget_tokens"))
            assertFalse(body.containsKey("temperature"))
            assertFalse(body.containsKey("top_p"))
            assertFalse(body.containsKey("top_k"))
        }
    }

    @Test fun anthropicEffortPreservesLegacyBudgetEncoding() = runBlocking {
        MockWebServer().use { server ->
            val model = "claude-sonnet-4-5"
            server.enqueue(sse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"))
            val base = provider(server, ModelApiType.ANTHROPIC)
            val configured = base.copy(config = base.config.copy(modelConfigs = mapOf(model to
                ModelConfiguration(supportsThinking = true, reasoningEffort = "xhigh",
                    reasoningEfforts = SELECTABLE_REASONING_EFFORTS))))
            adapters.streamAnthropic(configured, model, messages, "", null, null, 6000).toList()
            val body = Json.parseToJsonElement(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).jsonObject
            assertEquals("enabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals(5999, body["thinking"]!!.jsonObject["budget_tokens"]!!.jsonPrimitive.int)
            assertFalse(body.containsKey("output_config"))
        }
    }

    @Test fun googlePreservesApiVersionAndCustomAuth() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(sse("data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"hello\"}]}}]}\n\n"))
            val provider = provider(server, ModelApiType.GOOGLE).copy(auth = Authorization(AuthMethod.CUSTOM_HEADER, "X-Gateway-Key", "secret"))
            assertEquals("hello", adapters.streamGoogle(provider, "models/custom-gemini", messages, "", null, null, 50)
                .toList().filterIsInstance<GenerationEvent.Text>().joinToString("") { it.text })
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1beta/models/custom-gemini:streamGenerateContent", request.requestUrl!!.encodedPath)
            assertEquals("secret", request.getHeader("X-Gateway-Key"))
            assertNull(request.getHeader("x-goog-api-key"))
        }
    }

    @Test fun rejectedAnthropicCredentialsFailConnectionTest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"Invalid key\"}}"))
            assertTrue(adapters.testConnection(provider(server, ModelApiType.ANTHROPIC)).isFailure)
            assertEquals("/v1/models", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        }
    }
    @Test fun cancellingAStreamDoesNotWaitForTheServer() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            val start = System.nanoTime()
            try {
                kotlinx.coroutines.withTimeout(1000) {
                    adapters.streamOpenAi(provider(server, ModelApiType.OPENAI), "custom", messages, "", null, null, 50).toList()
                }
                fail("Expected cancellation")
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 5)
            }
        }
    }

}
