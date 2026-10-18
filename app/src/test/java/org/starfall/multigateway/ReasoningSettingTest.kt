package org.starfall.multigateway

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.multigateway.shared.model.*
import java.util.concurrent.TimeUnit
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderConfiguration
import org.starfall.multigateway.shared.model.language.withConversationReasoning
import org.starfall.multigateway.shared.model.language.reasoningEffortDisabled
import org.starfall.multigateway.shared.model.language.withSelectedReasoningEffort
import org.starfall.multigateway.shared.model.language.reasoningBudget
import org.starfall.multigateway.shared.model.language.requestReasoningEffort

@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class ReasoningSettingTest {
    @Test fun conversationOffOverridesModelLevelAndAutoRestoresIt() {
        val config = ModelConfiguration(reasoningEffort = "high")
        assertTrue(config.withConversationReasoning("none").reasoningEffortDisabled)
        assertEquals(config, config.withConversationReasoning(null))
        assertEquals("low", config.withConversationReasoning("low").reasoningEffort)
        val saved = Json.encodeToString(config.withConversationReasoning("none"))
        assertTrue(Json.decodeFromString<ModelConfiguration>(saved).reasoningEffortDisabled)
    }

    @Test fun selectedModelOffIsStoredAsAReasoningLevel() {
        val config = ModelConfiguration(reasoningEffort = "high", thinkingLevel = "high")
        val off = config.withSelectedReasoningEffort("none")

        assertEquals("none", off.reasoningEffort)
        assertTrue(off.reasoningEffortDisabled)
        assertEquals("none", off.requestReasoningEffort)
        assertEquals("high", off.thinkingLevel)
        assertEquals(off, Json.decodeFromString<ModelConfiguration>(Json.encodeToString(off)))
    }

    @Test fun draggingBackUpFromOffRestoresARealLevelWithoutExtraFlags() {
        val off = ModelConfiguration(reasoningEffort = "high").withSelectedReasoningEffort("none")
        val back = off.withSelectedReasoningEffort("medium")
        assertFalse(back.reasoningEffortDisabled)
        assertEquals("medium", back.reasoningEffort)
        assertEquals("medium", back.requestReasoningEffort)
        assertEquals("medium", back.thinkingLevel)
        assertFalse(Json.encodeToString(off).contains("reasoning_enabled"))
    }

    @Test fun offReachesNativeProviderRequests() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val adapters = ProviderAdaptersTestSupport(context)
        val messages = listOf(StoredMessage("u", ChatRole.USER, listOf(MessageVersion(content = "Hello"))))
        for (type in listOf(ModelApiType.OPENAI, ModelApiType.ANTHROPIC, ModelApiType.GOOGLE)) {
            MockWebServer().use { server ->
                val event = when (type) {
                    ModelApiType.ANTHROPIC -> "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
                    ModelApiType.GOOGLE -> "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"ok\"}]}}]}\n\n"
                    else -> "data: [DONE]\n\n"
                }
                server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(event))
                val provider = ModelProvider("p", "P", type, Authorization(value = "key"),
                    baseUrl = server.url(if (type == ModelApiType.GOOGLE) "/v1beta" else "/v1").toString(),
                    config = ProviderConfiguration(modelConfigs = mapOf("custom" to ModelConfiguration(
                        reasoningEffort = "none"
                    ))))
                when (type) {
                    ModelApiType.ANTHROPIC -> adapters.streamAnthropic(provider, "custom", messages, "", null, null, 2048).toList()
                    ModelApiType.GOOGLE -> adapters.streamGoogle(provider, "custom", messages, "", null, null, 2048).toList()
                    else -> adapters.streamOpenAi(provider, "custom", messages, "", null, null, 2048, "none").toList()
                }
                val body = Json.parseToJsonElement(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).jsonObject
                when (type) {
                    ModelApiType.ANTHROPIC -> assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
                    ModelApiType.GOOGLE -> assertEquals(0, body["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingBudget"]!!.jsonPrimitive.int)
                    else -> assertEquals("none", body["reasoning_effort"]!!.jsonPrimitive.content)
                }
            }
        }
    }

    @Test fun reasoningBudgetFollowsConfiguredEffortAndTokenLimit() {
        assertEquals(1024, ModelConfiguration(reasoningEffort = "low").reasoningBudget(10_000))
        assertEquals(2048, ModelConfiguration(reasoningEffort = "medium").reasoningBudget(10_000))
        assertEquals(4096, ModelConfiguration(reasoningEffort = "high").reasoningBudget(10_000))
        assertEquals(8192, ModelConfiguration(reasoningEffort = "xhigh").reasoningBudget(10_000))
        assertEquals(1499, ModelConfiguration(reasoningEffort = "xhigh").reasoningBudget(1500))
    }

}
