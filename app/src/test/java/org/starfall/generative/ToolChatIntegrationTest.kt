package org.starfall.generative


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.starfall.generative.shared.model.*
import org.starfall.generative.shared.files.*
import org.starfall.generative.shared.networking.*
import org.starfall.generative.shared.storage.preferences.*
import org.starfall.generative.features.mcp.shared.ui.*
import org.starfall.generative.integrations.mcp.*
import org.starfall.generative.features.machines.shared.ui.*
import org.starfall.generative.integrations.remote.*
import org.starfall.generative.features.home.chat.*
import org.starfall.generative.integrations.modelapis.*
import org.starfall.generative.shared.icons.*
import org.starfall.generative.shared.media.*
import java.nio.file.Files
import org.starfall.generative.shared.model.auth.Authorization
import org.starfall.generative.shared.model.generation.GenerationEvent
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.provider.ProviderConfiguration
import org.starfall.generative.features.home.chat.logic.tool.ToolChatViewModel

@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class ToolChatIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun contentApiDisplaysVideoWithoutExposingMediaUrlOrOfferingSendFile() = contentApiScenario()

    private fun contentApiScenario() = runBlocking {
        val server = MockWebServer()
        server.start()
        val root = Files.createTempDirectory("content-api-loop").toFile()
        val url = server.url("/cdn/video?signature=private").toString()
        val video = ByteArray(32).also { "ftyp".toByteArray().copyInto(it, 4) }
        var rounds = 0
        var followup = ""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                fun json(value: JsonElement) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
                if (request.method == "DELETE") return MockResponse().setResponseCode(204)
                if (request.method == "GET" && request.path == "/mcp") return MockResponse().setResponseCode(405)
                if (request.path?.startsWith("/cdn/video") == true) {
                    assertNull(request.getHeader("Authorization"))
                    return MockResponse().setHeader("Content-Type", "video/mp4").setBody(okio.Buffer().write(video))
                }
                val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                if (request.path == "/mcp") {
                    val result = when (body.text("method")) {
                        "notifications/initialized" -> return MockResponse().setResponseCode(202)
                        "initialize" -> obj("protocolVersion" to str("2025-06-18"), "capabilities" to obj("tools" to obj()), "serverInfo" to obj("name" to str("test"), "version" to str("1")))
                        "tools/list" -> obj("tools" to JsonArray(listOf(obj("name" to str("extract"), "inputSchema" to obj("type" to str("object"))))))
                        "tools/call" -> obj("content" to JsonArray(listOf(obj("type" to str("text"), "text" to str(obj(
                            "success" to JsonPrimitive(true), "title" to str("Video title"),
                            "original_url" to str("https://v.douyin.com/example/"),
                            "media" to JsonArray(listOf(obj("url" to str(url), "type" to str("video"))))
                        ).toString())))))
                        else -> error("Unexpected method")
                    }
                    return json(obj("jsonrpc" to str("2.0"), "id" to body.getValue("id"), "result" to result))
                }
                rounds++
                val message = if (rounds == 1) {
                    val name = body["tools"]!!.jsonArray.first().jsonObject["function"]!!.jsonObject.text("name")
                    obj("role" to str("assistant"), "content" to str("Fetching video."),
                        "tool_calls" to JsonArray(listOf(obj("id" to str("call1"), "type" to str("function"),
                            "function" to obj("name" to str(name), "arguments" to str("{}"))))))
                } else if (rounds == 2) {
                    followup = body.toString()
                    assertFalse(body["tools"]!!.jsonArray.any { it.jsonObject["function"]!!.jsonObject.text("name") == "send_file" })
                    obj("role" to str("assistant"), "content" to str("I found a video."))
                } else {
                    obj("role" to str("assistant"), "content" to str("The video is displayed above."))
                }
                return json(obj("choices" to JsonArray(listOf(obj("message" to message)))))
            }
        }
        try {
            val provider = ModelProvider("p", "Chat", ModelApiType.OPENAI, baseUrl = server.url("/v1").toString(),
                config = ProviderConfiguration(supportStream = false))
            val preset = McpInfo("content-api", "Content API", url = server.url("/mcp").toString())
            val store = ToolFiles(root)
            val http = ToolHttp(store)
            val events = ToolChatViewModel(http, McpService(http), AiService(context)).generate(provider, "chat",
                listOf(StoredMessage("u", ChatRole.USER, listOf(MessageVersion("Get this video")))),
                "", listOf(preset), listOf(provider), { ToolSettings() }).toList()
            assertEquals(2, rounds)
            assertFalse(followup.contains(url))
            assertFalse(followup.contains("signature=private"))
            assertTrue(followup.contains("v.douyin.com/example"))
            assertTrue(followup.contains("Video title"))
            val activity = events.filterIsInstance<GenerationEvent.Tool>()
                .last { it.activity.name.endsWith("/ extract") }.activity
            assertEquals("success", activity.status)
            assertTrue(activity.inlineMedia)
            assertTrue(activity.files.any { it.endsWith(".mp4") })
            assertArrayEquals(video, store.resolve(activity.files.first { it.endsWith(".mp4") })!!.readBytes())
            assertTrue(store.list().any { it.extension == "mp4" })
            assertFalse(activity.response.contains(url))
        } finally { server.shutdown(); root.deleteRecursively() }
    }

    @Test fun directImageGenerationSendsOriginalPromptWithoutCallingTextModel() = runBlocking {
        val server = MockWebServer()
        server.start()
        val root = Files.createTempDirectory("direct-image-test").toFile()
        val bytes = ByteArray(100) { 42 }.also { it[0] = 0x89.toByte(); it[1] = 0x50 }
        val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
        server.enqueue(MockResponse().addHeader("Content-Type", "application/json")
            .setBody("{\"data\":[{\"b64_json\":\"$b64\"}]}"))
        try {
            val provider = ModelProvider("p", "local", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString())
            val http = ToolHttp(ToolFiles(root))
            val engine = ToolChatViewModel(http, McpService(http), AiService(context))
            val prompt = "A watercolor landscape with a blue lake"
            val events = engine.generateMedia(provider, "image-model", ModelType.IMAGE_GENERATION,
                prompt, buildJsonObject { put("quality", "high") }).toList()
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("/v1/images/generations", request.path)
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(prompt, body["prompt"]!!.jsonPrimitive.content)
            assertEquals("image-model", body["model"]!!.jsonPrimitive.content)
            assertEquals("high", body["quality"]!!.jsonPrimitive.content)
            assertTrue(events.filterIsInstance<GenerationEvent.Text>().isEmpty())
            val result = events.filterIsInstance<GenerationEvent.Tool>().last().activity
            assertEquals("success", result.status)
            assertTrue(result.files.isNotEmpty())
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }


    @Test fun failedImageToolIsReturnedToModelAsExplicitErrorResult() = runBlocking {
        val server = MockWebServer()
        server.start()
        val root = Files.createTempDirectory("tool-chat-error-test").toFile()
        var rounds = 0
        var followupBody = ""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/v1/images/generations" -> MockResponse()
                        .setResponseCode(500)
                        .addHeader("Content-Type", "application/json")
                        .setBody("""{"error":{"message":"image backend unavailable"}}""")
                    "/v1/chat/completions" -> {
                        rounds++
                        if (rounds == 1) {
                            MockResponse().addHeader("Content-Type", "text/event-stream").setBody(
                                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call1","function":{"name":"generate_image","arguments":"{\"prompt\":\"a tree\"}"}}]}}]}

data: [DONE]

"""
                            )
                        } else {
                            followupBody = request.body.readUtf8()
                            MockResponse().addHeader("Content-Type", "text/event-stream").setBody(
                                """data: {"choices":[{"delta":{"content":"The image tool failed."}}]}

data: [DONE]

"""
                            )
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        try {
            val provider = ModelProvider(
                "p", "local", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString(),
                config = ProviderConfiguration(
                    modelIds = listOf("chat", "image"),
                    modelConfigs = mapOf(
                        "chat" to ModelConfiguration(supportsToolCalls = true),
                        "image" to ModelConfiguration(modelType = ModelType.IMAGE_GENERATION)
                    )
                )
            )
            val files = ToolFiles(root)
            val http = ToolHttp(files)
            val events = ToolChatViewModel(http, McpService(http), AiService(context)).generate(
                provider,
                "chat",
                listOf(StoredMessage("u", ChatRole.USER, listOf(MessageVersion("Draw a tree")))),
                "",
                emptyList(),
                listOf(provider),
                {
                    ToolSettings(
                        system = mapOf(
                            "generate_image" to SystemToolConfig(true, "p", "image")
                        )
                    )
                }
            ).toList()

            assertEquals(2, rounds)
            val followup = Json.parseToJsonElement(followupBody).jsonObject
            val toolContent = followup["messages"]!!.jsonArray.last().jsonObject.text("content")
            val toolError = Json.parseToJsonElement(toolContent).jsonObject
            assertTrue(toolError["isError"]!!.jsonPrimitive.boolean)
            assertEquals("error", toolError.text("status"))
            assertTrue(toolError.text("error").contains("image backend unavailable") || toolError.text("error").contains("HTTP 500"))
            assertEquals(
                "The image tool failed.",
                events.filterIsInstance<GenerationEvent.Text>().joinToString("") { it.text }
            )
            val tool = events.filterIsInstance<GenerationEvent.Tool>().last().activity
            assertEquals("error", tool.status)
            assertTrue(tool.response.contains("\"isError\":true"))
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }

    @Test fun modelCallsImageToolAndResumesWithStreamedAnswerWithoutBase64InChat() = runBlocking {
        val server=MockWebServer();server.start()
        val root=Files.createTempDirectory("tool-chat-test").toFile()
        var rounds=0
        var mediaCalls=0
        var imageRequest=""
        val bytes=ByteArray(24000){42};bytes[0]=0x89.toByte();bytes[1]=0x50
        val b64=java.util.Base64.getEncoder().encodeToString(bytes)
        server.dispatcher=object:Dispatcher(){
            override fun dispatch(request:RecordedRequest):MockResponse {
                return if(request.path=="/v1/images/generations") {
                    mediaCalls++
                    imageRequest=request.body.readUtf8()
                    MockResponse().addHeader("Content-Type","application/json").setBody("{\"data\":[{\"b64_json\":\"$b64\"}]}")
                } else if(request.path=="/v1/chat/completions") {
                    rounds++
                    if(rounds==1) MockResponse().addHeader("Content-Type","text/event-stream").setBody(
                        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call1\",\"function\":{\"name\":\"generate_image\",\"arguments\":\"{\\\"prompt\\\":\\\"a tree\\\"}\"}}]}}]}\n\ndata: [DONE]\n\n")
                    else {
                        val body=request.body.readUtf8()
                        assertTrue(body.contains("tool_call_id"));assertFalse(body.contains(b64))
                        assertFalse(body.contains("\"name\":\"send_file\""))
                        MockResponse().addHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Image ready.\"}}]}\n\ndata: [DONE]\n\n")
                    }
                } else MockResponse().setResponseCode(404)
            }
        }
        try {
            val provider=ModelProvider("p","local",ModelApiType.OPENAI,baseUrl=server.url("/v1").toString(),config=ProviderConfiguration(
                modelIds=listOf("chat","image"),modelConfigs=mapOf("chat" to ModelConfiguration(supportsToolCalls=true),"image" to ModelConfiguration(modelType=ModelType.IMAGE_GENERATION))))
            val files=ToolFiles(root);val http=ToolHttp(files)
            val engine=ToolChatViewModel(http,McpService(http),AiService(context))
            val events=engine.generate(provider,"chat",listOf(StoredMessage("u",ChatRole.USER,listOf(MessageVersion("Draw a tree")))),"",emptyList(),listOf(provider),{ToolSettings(system=mapOf("generate_image" to SystemToolConfig(true,"p","image").withImageOptions(buildJsonObject { put("quality","high"); put("output_format","webp") })))}).toList()
            assertEquals(1,mediaCalls);assertEquals(2,rounds)
            val imageBody=Json.parseToJsonElement(imageRequest).jsonObject
            assertEquals("high",imageBody["quality"]!!.jsonPrimitive.content)
            assertEquals("webp",imageBody["output_format"]!!.jsonPrimitive.content)
            assertEquals("Image ready.",events.filterIsInstance<GenerationEvent.Text>().joinToString(""){it.text})
            val tool=events.filterIsInstance<GenerationEvent.Tool>().last().activity
            assertEquals("success",tool.status);assertTrue(tool.files.any{it.endsWith(".png")})
            assertFalse(events.toString().contains(b64))
            assertArrayEquals(bytes,files.list().first{it.extension=="png"}.readBytes())
        } finally {server.shutdown();root.deleteRecursively()}
    }
}
