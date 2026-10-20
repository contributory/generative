package org.starfall.multigateway

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.multigateway.home.shared.logic.ToolRequestService
import org.starfall.multigateway.integrations.modelapis.AiService
import org.starfall.multigateway.integrations.modelapis.account.AccountProviderAdapterRegistry
import org.starfall.multigateway.integrations.modelapis.account.AccountTokenState
import org.starfall.multigateway.integrations.modelapis.account.AccountTokenStore
import org.starfall.multigateway.integrations.modelapis.openaicodex.CodexTokenState
import org.starfall.multigateway.integrations.modelapis.openaicodex.CodexTokenStore
import org.starfall.multigateway.integrations.modelapis.openaicodex.OpenAICodexAdapter
import org.starfall.multigateway.shared.files.AttachmentResolver
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.auth.AuthMethod
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.generation.TokenUsage
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderConfiguration
import org.starfall.multigateway.shared.networking.*

@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class AccountTokenUsageTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prepareKeystore() = installTestAndroidKeyStore()

    @Test(timeout = 60000) fun accountAdaptersEmitActualUsageInBothModesUsingTheirWireProtocol() = runBlocking {
        verifyAccountUsage(includeUsage = true)
    }

    @Test(timeout = 60000) fun absentAccountUsageKeepsFallbackAvailableInBothModes() = runBlocking {
        verifyAccountUsage(includeUsage = false)
    }

    @Test(timeout = 60000) fun accountToolRequestsReportWireUsageAndReturnCleanReplayTurnsInBothModes() = runBlocking {
        verifyAccountUsage(includeUsage = true, toolRequest = true)
    }

    @Test(timeout = 60000) fun rawResponsesPreserveUsageInBothModesWithoutSummingCumulativeChunks() = runBlocking {
        for (type in listOf(ModelApiType.OPENAI, ModelApiType.OPENAI_RESPONSES,
            ModelApiType.ANTHROPIC, ModelApiType.GOOGLE, ModelApiType.OLLAMA)) {
            for (stream in listOf(false, true)) {
                MockWebServer().use { server ->
                    val expected = response(type, includeUsage = true)
                    server.enqueue(MockResponse().setBody(if (stream) streamBody(type, true) else expected.toString()))
                    val provider = ModelProvider("raw", "Raw", type, baseUrl = server.url("/").toString())
                    val text = StringBuilder()
                    val actual = ToolHttp().modelResponse(server.url("/generateContent").toString(),
                        obj(), provider, stream, { text.append(it) })
                    assertEquals("$type stream=$stream", usageFields(expected), usageFields(actual))
                    assertEquals(TokenUsage(60, 7), providerTokenUsage(type, actual))
                    if (stream) assertEquals("hello", text.toString())
                    else assertEquals(expected, actual)
                    val request = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
                    if (stream && type == ModelApiType.OPENAI) {
                        assertEquals(JsonPrimitive(true), request["stream_options"]?.jsonObject?.get("include_usage"))
                    } else assertNull(request["stream_options"])
                }
            }
        }
    }

    private suspend fun verifyAccountUsage(includeUsage: Boolean, toolRequest: Boolean = false) {
        val routes = listOf(
            ModelApiType.CLAUDE_CODE to ModelApiType.ANTHROPIC,
            ModelApiType.ANTIGRAVITY to ModelApiType.GOOGLE,
            ModelApiType.GITHUB_COPILOT to ModelApiType.OPENAI,
            ModelApiType.GITHUB_COPILOT to ModelApiType.ANTHROPIC,
            ModelApiType.GITHUB_COPILOT to ModelApiType.OPENAI_RESPONSES,
            ModelApiType.OPENAI_CODEX to ModelApiType.OPENAI_RESPONSES
        )
        for ((account, wire) in routes) {
            for (stream in listOf(false, true)) {
                MockWebServer().use { server ->
                    val id = "usage-${account.name}-${wire.name}-$stream-$includeUsage"
                    val auth = if (account == ModelApiType.OPENAI_CODEX) {
                        CodexTokenStore(context).save(id, CodexTokenState("test-access", "test-refresh", accountId = "test-account"))
                        Authorization(AuthMethod.OAUTH, value = OpenAICodexAdapter.AUTH_MARKER)
                    } else {
                        AccountTokenStore(context, account.name.lowercase()).save(id,
                            AccountTokenState("test-access", "test-refresh", projectId = "test-project"))
                        Authorization(AuthMethod.OAUTH, value = "account:${account.name.lowercase()}:v1")
                    }
                    if (account == ModelApiType.GITHUB_COPILOT) {
                        val endpoint = when (wire) {
                            ModelApiType.ANTHROPIC -> "/v1/messages"
                            ModelApiType.OPENAI_RESPONSES -> "/responses"
                            else -> "/chat/completions"
                        }
                        server.enqueue(MockResponse().setBody("""{"data":[{"id":"chat","supported_endpoints":["$endpoint"]}]}"""))
                    }
                    fun envelope(value: JsonObject) = if (account == ModelApiType.ANTIGRAVITY)
                        obj("response" to value) else value
                    val body = if (stream) streamBody(wire, includeUsage, ::envelope)
                        else envelope(response(wire, includeUsage)).toString()
                    // Codex must also consume a terminal usage event without a trailing blank line.
                    server.enqueue(MockResponse().setHeader("Content-Type", if (stream) "text/event-stream" else "application/json")
                        .setBody(if (account == ModelApiType.OPENAI_CODEX && stream) body.trimEnd() else body))
                    val provider = ModelProvider(id, "Usage", account, auth = auth,
                        baseUrl = server.url(if (account == ModelApiType.OPENAI_CODEX) "/responses" else "/v1").toString(),
                        config = ProviderConfiguration(supportStream = stream))
                    val label = "$account wire=$wire stream=$stream usage=$includeUsage"
                    val text = StringBuilder()
                    val usages = mutableListOf<TokenUsage>()
                    if (toolRequest) {
                        val turn = ToolRequestService(ToolHttp(), AiService(context)).request(provider, "chat",
                            listOf(obj("role" to str("user"), "content" to str("hi"))), emptyList(), "instructions", false,
                            onText = { text.append(it) }, onReasoning = {}, onUsage = { usages += it })
                        if (!stream) text.append(turn.text("content"))
                        assertFalse(label, turn.toString().contains("\"usage\":"))
                        assertFalse(label, turn.toString().contains("\"usageMetadata\":"))
                    } else {
                        val adapter = AccountProviderAdapterRegistry(context, AttachmentResolver(context)).get(account)!!
                        val events = adapter.streamEvents(provider, "chat",
                            listOf(StoredMessage("u", ChatRole.USER, listOf(MessageVersion("hi")))), "instructions", 100).toList()
                        events.filterIsInstance<GenerationEvent.Text>().forEach { text.append(it.text) }
                        usages += events.filterIsInstance<GenerationEvent.Usage>().map { it.usage }
                    }
                    assertEquals(label, "hello", text.toString())
                    assertEquals(label, if (includeUsage) listOf(TokenUsage(60, 7)) else emptyList<TokenUsage>(), usages)
                    if (account == ModelApiType.GITHUB_COPILOT) server.takeRequest()
                    val request = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
                    assertFalse(label, request.toString().contains("\"usage\":"))
                    assertFalse(label, request.toString().contains("\"usageMetadata\":"))
                    if (wire == ModelApiType.OPENAI && stream) {
                        assertEquals(JsonPrimitive(true), request["stream_options"]?.jsonObject?.get("include_usage"))
                    }
                }
            }
        }
    }

    private fun response(type: ModelApiType, includeUsage: Boolean): JsonObject {
        val content = json(when (type) {
            ModelApiType.ANTHROPIC -> """{"id":"msg_test","type":"message","role":"assistant","model":"claude-test","content":[{"type":"text","text":"hello"}],"stop_reason":"end_turn","stop_sequence":null,"usage":{}}"""
            ModelApiType.GOOGLE -> """{"candidates":[{"content":{"parts":[{"text":"hello"}]}}]}"""
            ModelApiType.OPENAI_RESPONSES -> """{"id":"resp_test","object":"response","created_at":0,"status":"completed","model":"gpt-test","output":[{"id":"msg_test","type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"hello","annotations":[]}]}],"usage":{}}"""
            ModelApiType.OLLAMA -> """{"message":{"role":"assistant","content":"hello"},"done":true}"""
            else -> """{"id":"chatcmpl-test","object":"chat.completion","created":0,"model":"chat","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],"usage":{}}"""
        })
        if (!includeUsage) return content
        val fields = when (type) {
            ModelApiType.ANTHROPIC -> json("""{"usage":{"input_tokens":10,"cache_creation_input_tokens":20,"cache_read_input_tokens":30,"output_tokens":7}}""")
            ModelApiType.GOOGLE -> json("""{"usageMetadata":{"promptTokenCount":60,"candidatesTokenCount":7}}""")
            ModelApiType.OPENAI_RESPONSES -> json("""{"usage":{"input_tokens":60,"output_tokens":7}}""")
            ModelApiType.OLLAMA -> json("""{"prompt_eval_count":60,"eval_count":7}""")
            else -> json("""{"usage":{"prompt_tokens":60,"completion_tokens":7}}""")
        }
        return JsonObject(content + fields)
    }

    private fun streamBody(type: ModelApiType, includeUsage: Boolean,
        envelope: (JsonObject) -> JsonObject = { it }): String {
        val full = response(type, includeUsage)
        val events = when (type) {
            ModelApiType.ANTHROPIC -> buildList {
                add(obj("type" to str("message_start"), "message" to obj(
                    "id" to str("msg_test"), "type" to str("message"), "role" to str("assistant"),
                    "model" to str("claude-test"), "content" to JsonArray(emptyList()),
                    "stop_reason" to JsonNull, "stop_sequence" to JsonNull,
                    "usage" to if (includeUsage)
                        JsonObject(full.requireObject("usage") + ("output_tokens" to JsonPrimitive(0)))
                    else obj()
                )))
                add(json("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hello"}}"""))
                if (includeUsage) {
                    add(json("""{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":3}}"""))
                    add(json("""{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":7}}"""))
                    add(json("""{"type":"message_stop"}"""))
                }
            }
            ModelApiType.GOOGLE -> if (includeUsage) listOf(
                JsonObject(full + ("usageMetadata" to json("""{"promptTokenCount":60,"candidatesTokenCount":3}"""))),
                obj("usageMetadata" to full.getValue("usageMetadata"))
            ) else listOf(full)
            ModelApiType.OPENAI_RESPONSES -> listOf(
                json("""{"type":"response.output_text.delta","delta":"hello"}"""),
                obj("type" to str("response.completed"), "response" to full)
            )
            ModelApiType.OLLAMA -> listOf(json("""{"message":{"content":"hello"},"done":false}"""),
                JsonObject(usageFields(full) + ("done" to JsonPrimitive(true))))
            else -> buildList {
                add(json("""{"id":"chatcmpl-test","object":"chat.completion.chunk","created":0,"model":"chat","choices":[{"index":0,"delta":{"content":"hello"},"finish_reason":null}]}"""))
                if (includeUsage) {
                    add(json("""{"id":"chatcmpl-test","object":"chat.completion.chunk","created":0,"model":"chat","choices":[],"usage":{"prompt_tokens":60,"completion_tokens":3,"total_tokens":63}}"""))
                    add(obj("id" to str("chatcmpl-test"), "object" to str("chat.completion.chunk"),
                        "created" to JsonPrimitive(0), "model" to str("chat"), "choices" to JsonArray(emptyList()),
                        "usage" to full.getValue("usage")))
                }
            }
        }
        return if (type == ModelApiType.OLLAMA) events.joinToString("\n", postfix = "\n") { it.toString() }
            else events.joinToString("") { "data: ${envelope(it)}\n\n" }
    }

    private fun usageFields(value: JsonObject) = value.filterKeys {
        it in setOf("usage", "usageMetadata", "prompt_eval_count", "eval_count")
    }

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
}
