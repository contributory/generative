package org.starfall.multigateway

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.multigateway.integrations.media.MediaRequestChainExecutor
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.helpers.media.MediaTemplateContext
import org.starfall.multigateway.shared.helpers.media.MediaTemplateInput
import org.starfall.multigateway.shared.helpers.media.MediaStepTemplateContext
import org.starfall.multigateway.shared.helpers.media.resolveMediaTemplate
import org.starfall.multigateway.shared.helpers.media.selectMediaPath
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.media.MediaFileTransport
import org.starfall.multigateway.shared.model.media.MediaFinalResponse
import org.starfall.multigateway.shared.model.media.MediaGenerationMode
import org.starfall.multigateway.shared.model.media.MediaRequestChain
import org.starfall.multigateway.shared.model.media.MediaRequestStep
import org.starfall.multigateway.shared.model.media.MediaRequestProfile
import org.starfall.multigateway.shared.model.media.MediaResponseType
import org.starfall.multigateway.shared.model.media.MediaResponseObservationMode
import org.starfall.multigateway.shared.model.media.MediaValueKind
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.networking.ToolHttp
import java.nio.file.Files

class MediaRequestChainTest {
    private fun context(
        files: List<MediaTemplateInput> = emptyList(),
        responses: List<String> = emptyList(),
        responseHeaders: List<Map<String, String>> = emptyList(),
        steps: List<MediaStepTemplateContext> = emptyList()
    ) =
        MediaTemplateContext(
            prompt = "draw a tree",
            model = "image-model",
            apiToken = "secret",
            inputFiles = files,
            responses = responses.map { Json.parseToJsonElement(it) },
            responseHeaders = responseHeaders,
            steps = steps
        )

    @Test fun requestVariablesAreOptionalAndMappingsCanBeEmpty() {
        listOf(
            "{\"prompt\":\"{{prompt}}\"}",
            "{\"references_images\":\"{{input_files}}\"}",
            "{}"
        ).forEach { source ->
            val resolved = resolveMediaTemplate(Json.parseToJsonElement(source), context(), MediaFileTransport.BASE64)
            assertTrue(resolved is JsonObject)
        }
        val missing = resolveMediaTemplate(
            Json.parseToJsonElement("""{"files":"{{input_files}}","file":"{{input_file[0]}}"}"""),
            context(),
            MediaFileTransport.BASE64
        ).jsonObject
        assertEquals("[]", missing["files"].toString())
        assertEquals(JsonNull, missing["file"])
    }

    @Test fun requestVariablesAllowWhitespaceInsideDoubleBraces() {
        val resolved = resolveMediaTemplate(
            Json.parseToJsonElement("""{"prompt":"{{ prompt }}","model":"{{  model  }}"}"""),
            context(),
            MediaFileTransport.BASE64
        ).jsonObject
        assertEquals("draw a tree", resolved["prompt"]!!.jsonPrimitive.content)
        assertEquals("image-model", resolved["model"]!!.jsonPrimitive.content)
    }

    @Test fun responseHeadersCanBeReferencedByPreviousStepNumberAndHeaderName() {
        val resolved = resolveMediaTemplate(
            Json.parseToJsonElement("""{"task":"{{step1.response.headers.X-Task-Id}}"}"""),
            context(
                steps = listOf(
                    MediaStepTemplateContext(
                        url = "https://api.example.test/create",
                        requestMethod = "POST",
                        requestHeaders = emptyMap(),
                        requestBody = JsonObject(emptyMap()),
                        responseBody = JsonObject(emptyMap()),
                        responseHeaders = mapOf("X-Task-Id" to "task-123"),
                        responseStatusCode = 200
                    )
                )
            ),
            MediaFileTransport.BASE64
        ).jsonObject
        assertEquals("task-123", resolved["task"]!!.jsonPrimitive.content)
    }

    @Test fun stepVariablesExposeRequestAndResponseMetadata() {
        val resolved = resolveMediaTemplate(
            Json.parseToJsonElement(
                """{"url":"{{step1.url}}","requestHeader":"{{step1.request.headers.X-Request-Id}}","requestBody":"{{step1.request.body.prompt}}","responseBody":"{{step1.response.body.data.id}}","responseHeader":"{{step1.response.headers.X-Task-Id}}","status":"{{step1.response.status}}"}"""
            ),
            context(
                steps = listOf(
                    MediaStepTemplateContext(
                        url = "https://api.example.test/create",
                        requestMethod = "POST",
                        requestHeaders = mapOf("X-Request-Id" to "req-123"),
                        requestBody = Json.parseToJsonElement("""{"prompt":"draw"}"""),
                        responseBody = Json.parseToJsonElement("""{"data":{"id":"task-123"}}"""),
                        responseHeaders = mapOf("X-Task-Id" to "task-123"),
                        responseStatusCode = 202
                    )
                )
            ),
            MediaFileTransport.BASE64
        ).jsonObject
        assertEquals("https://api.example.test/create", resolved["url"]!!.jsonPrimitive.content)
        assertEquals("req-123", resolved["requestHeader"]!!.jsonPrimitive.content)
        assertEquals("draw", resolved["requestBody"]!!.jsonPrimitive.content)
        assertEquals("task-123", resolved["responseBody"]!!.jsonPrimitive.content)
        assertEquals("task-123", resolved["responseHeader"]!!.jsonPrimitive.content)
        assertEquals("202", resolved["status"]!!.jsonPrimitive.content)
    }

    @Test fun inputFilesSupportListsIndexesAndBothTransports() {
        val files = listOf(MediaTemplateInput("reference.png", "image/png", "AQI="))
        val source = Json.parseToJsonElement(
            """{"all":"{{input_files}}","first":"{{input_file[0]}}"}"""
        )
        val base64 = resolveMediaTemplate(source, context(files), MediaFileTransport.BASE64).jsonObject
        assertEquals("AQI=", base64["first"]!!.jsonPrimitive.content)
        assertEquals("AQI=", base64["all"]!!.jsonArray.single().jsonPrimitive.content)

        val multipart = resolveMediaTemplate(source, context(files), MediaFileTransport.FILE).jsonObject
        assertEquals("__multigateway_media_file_0", multipart["first"]!!.jsonPrimitive.content)
        assertEquals("__multigateway_media_file_0", multipart["all"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test fun responsePathsSupportIndexesWildcardsAndLaterStepVariables() {
        val response = Json.parseToJsonElement(
            """{"data":{"id":"task-123","images":[{"content":"one"},{"content":"two"}]}}"""
        )
        assertEquals(2, selectMediaPath(response, "data.images[].content").size)
        assertEquals("one", selectMediaPath(response, "data.images[0].content").single().jsonPrimitive.content)
        val request = resolveMediaTemplate(
            Json.parseToJsonElement("""{"id":"{{step1.response.body.data.id}}"}"""),
            context(
                steps = listOf(
                    MediaStepTemplateContext(
                        url = "https://api.example.test/create",
                        requestMethod = "POST",
                        requestHeaders = emptyMap(),
                        requestBody = JsonObject(emptyMap()),
                        responseBody = response,
                        responseHeaders = emptyMap(),
                        responseStatusCode = 200
                    )
                )
            ),
            MediaFileTransport.BASE64
        )
        assertEquals("task-123", request.jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test fun requestChainSettingsRoundTripWithHeadersAndFinalResponse() {
        val chain = MediaRequestChain(
            steps = listOf(
                MediaRequestStep(
                    endpoint = "/video/create",
                    headers = mapOf("X-Request" to "{{model}}"),
                    body = "{\"prompt\":\"{{prompt}}\"}"
                ),
                MediaRequestStep(
                    endpoint = "/video/result/{{step1.response.body.data.id}}",
                    method = "GET",
                    body = "{}"
                )
            ),
            finalResponse = MediaFinalResponse(
                type = MediaResponseType.JSON,
                mediaPath = "data.video.content",
                valueKind = MediaValueKind.LINK
            )
        )
        val restored = Json.decodeFromString<MediaRequestChain>(Json.encodeToString(chain))
        assertEquals(chain, restored)
    }

    @Test fun imageAndVideoChainsRemainSeparatePerProviderAndModel() {
        val imageChain = MediaRequestChain(steps = listOf(MediaRequestStep(endpoint = "/image")))
        val videoChain = MediaRequestChain(steps = listOf(MediaRequestStep(endpoint = "/video")))
        val imageProfile = MediaRequestProfile("image-profile", "Images", imageChain)
        val videoProfile = MediaRequestProfile("video-profile", "Videos", videoChain)
        val saved = SystemToolConfig(providerId = "provider", modelId = "image")
            .withImageRequestProfiles(mapOf(imageProfile.id to imageProfile), imageProfile.id)
        val restored = Json.decodeFromString<SystemToolConfig>(Json.encodeToString(saved))
        assertEquals("/image", restored.activeImageRequestProfile!!.chain.steps.single().endpoint)
        assertEquals(null, restored.activeVideoRequestProfile)
        assertEquals("/image", restored.copy(modelId = "other").activeImageRequestProfile!!.chain.steps.single().endpoint)
        assertEquals("/video", restored.withVideoRequestProfiles(mapOf(videoProfile.id to videoProfile), videoProfile.id)
            .activeVideoRequestProfile!!.chain.steps.single().endpoint)
    }

    @Test fun oldSettingsUseDefaultModeAndExplicitModeCanSwitchWithoutLosingEitherConfig() {
        val chain = MediaRequestChain(steps = listOf(MediaRequestStep(endpoint = "/custom")))
        val old = Json.decodeFromString<SystemToolConfig>("""{"providerId":"p","modelId":"image","imageOptionsByModel":{"p/image":{"quality":"high"}}}""")
        assertEquals(MediaGenerationMode.DEFAULT, old.imageGenerationMode)

        val profile = MediaRequestProfile("custom-profile", "Custom", chain)
        val custom = old.withImageRequestProfiles(mapOf(profile.id to profile), profile.id)
        assertEquals(MediaGenerationMode.CUSTOM, custom.imageGenerationMode)
        assertEquals("high", custom.imageOptions["quality"]!!.jsonPrimitive.content)

        val defaultAgain = custom.withImageGenerationMode(MediaGenerationMode.DEFAULT)
        assertEquals(MediaGenerationMode.DEFAULT, defaultAgain.imageGenerationMode)
        assertEquals("/custom", defaultAgain.activeImageRequestProfile!!.chain.steps.single().endpoint)
        assertEquals("high", defaultAgain.imageOptions["quality"]!!.jsonPrimitive.content)
    }

    @Test fun requestChainPollsPendingResponsesAndExtractsConfiguredLink() = runBlocking {
        val server = MockWebServer().apply { start() }
        val root = Files.createTempDirectory("media-chain").toFile()
        try {
            server.enqueue(MockResponse().setBody("""{"data":{"id":"task-1"}}"""))
            server.enqueue(MockResponse().setBody("""{"status":"processing"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"completed","data":{"video":"${server.url("/video.mp4")}"}}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(okio.Buffer().write(ByteArray(32).also {
                "ftyp".toByteArray().copyInto(it, 4)
            })))
            val provider = ModelProvider(
                "p", "test", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString(),
                auth = Authorization(value = "token")
            )
            val chain = MediaRequestChain(
                steps = listOf(
                    MediaRequestStep(endpoint = "/video/create", body = "{\"model\":\"{{model}}\",\"prompt\":\"{{prompt}}\"}"),
                    MediaRequestStep(
                        endpoint = "/video/status/{{step1.response.body.data.id}}",
                        method = "GET",
                        headers = mapOf("Authorization" to "Token custom"),
                        body = "{}",
                        observation = org.starfall.multigateway.shared.model.media.MediaResponseObservation(
                            statusPath = "status",
                            retry = org.starfall.multigateway.shared.model.media.MediaRetryPolicy(
                                maxAttempts = 2,
                                delayMillis = 0,
                                maxWaitMillis = 1_000
                            )
                        )
                    )
                ),
                finalResponse = MediaFinalResponse(
                    type = MediaResponseType.JSON,
                    mediaPath = "data.video",
                    valueKind = MediaValueKind.LINK
                )
            )
            val result = MediaRequestChainExecutor(ToolHttp(ToolFiles(root))).execute(
                chain, provider, "video-model", "animate", emptyList()
            )
            val create = server.takeRequest()
            assertEquals("/v1/video/create", create.path)
            assertEquals("Bearer token", create.getHeader("Authorization"))
            val pending = server.takeRequest()
            assertEquals("/v1/video/status/task-1", pending.path)
            assertEquals("Token custom", pending.getHeader("Authorization"))
            val complete = server.takeRequest()
            assertEquals("/v1/video/status/task-1", complete.path)
            assertEquals("Token custom", complete.getHeader("Authorization"))
            assertEquals("/video.mp4", server.takeRequest().path)
            assertTrue(result["files"]!!.toString().contains(".mp4"))
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }

    @Test fun endpointsAcceptCompleteUrlsAndResolveVariablesBeforeSending() = runBlocking {
        val server = MockWebServer().apply { start() }
        val root = Files.createTempDirectory("media-chain-url").toFile()
        try {
            server.enqueue(MockResponse().setHeader("X-Task-Id", "header-456").setBody("""{"id":"task-123"}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            ))))
            val provider = ModelProvider(
                "p", "test", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString(),
                auth = Authorization(value = "token")
            )
            val chain = MediaRequestChain(
                steps = listOf(
                    MediaRequestStep(
                        endpoint = "/create",
                        headers = mapOf("X-Request-Id" to "req-123"),
                        body = "{\"prompt\":\"{{prompt}}\"}"
                    ),
                    MediaRequestStep(
                        endpoint = server.url("/media/").toString() + "{{step1.response.body.id}}?header={{step1.response.headers.X-Task-Id}}&model={{model}}",
                        method = "GET",
                        headers = mapOf(
                            "X-Previous" to "{{step1.response.headers.X-Task-Id}}",
                            "X-Original-Request" to "{{step1.request.headers.X-Request-Id}}"
                        ),
                        body = "{}"
                    )
                ),
                finalResponse = MediaFinalResponse(type = MediaResponseType.IMAGE)
            )

            val result = MediaRequestChainExecutor(ToolHttp(ToolFiles(root))).execute(
                chain, provider, "image-model", "draw a tree", emptyList()
            )

            assertEquals("/v1/create", server.takeRequest().path)
            val mediaRequest = server.takeRequest()
            assertEquals("/media/task-123?header=header-456&model=image-model", mediaRequest.path)
            assertEquals("header-456", mediaRequest.getHeader("X-Previous"))
            assertEquals("req-123", mediaRequest.getHeader("X-Original-Request"))
            assertTrue(result["files"]!!.jsonArray.isNotEmpty())
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }

    @Test fun responseObserverOnlyControlsRetryAndDoesNotBlockTheNextStep() = runBlocking {
        val server = MockWebServer().apply { start() }
        val root = Files.createTempDirectory("media-chain-observer").toFile()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"failed"}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            ))))
            val provider = ModelProvider(
                "p", "test", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString(),
                auth = Authorization(value = "token")
            )
            val chain = MediaRequestChain(
                steps = listOf(
                    MediaRequestStep(
                        endpoint = "/create",
                        body = "{}",
                        observation = org.starfall.multigateway.shared.model.media.MediaResponseObservation(
                            statusPath = "status",
                            pendingValues = listOf("processing"),
                            errorValues = listOf("failed")
                        )
                    ),
                    MediaRequestStep(endpoint = "/media", method = "GET", body = "{}")
                ),
                finalResponse = MediaFinalResponse(type = MediaResponseType.IMAGE)
            )

            val result = MediaRequestChainExecutor(ToolHttp(ToolFiles(root))).execute(
                chain, provider, "image-model", "draw a tree", emptyList()
            )

            assertEquals("/v1/create", server.takeRequest().path)
            assertEquals("/v1/media", server.takeRequest().path)
            assertTrue(result["files"]!!.jsonArray.isNotEmpty())
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }

    @Test fun finalResponseCanReadTheBodyFromAnEarlierStep() = runBlocking {
        val server = MockWebServer().apply { start() }
        val root = Files.createTempDirectory("media-chain-final-source").toFile()
        try {
            server.enqueue(MockResponse().setBody("""{"image":"${server.url("/media.png")}"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"done"}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            ))))
            val provider = ModelProvider(
                "p", "test", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString(),
                auth = Authorization(value = "token")
            )
            val chain = MediaRequestChain(
                steps = listOf(
                    MediaRequestStep(endpoint = "/create", body = "{}"),
                    MediaRequestStep(endpoint = "/status", method = "GET", body = "{}")
                ),
                finalResponse = MediaFinalResponse(
                    type = MediaResponseType.JSON,
                    mediaPath = "image",
                    sourceStep = 1
                )
            )

            val result = MediaRequestChainExecutor(ToolHttp(ToolFiles(root))).execute(
                chain, provider, "image-model", "draw a tree", emptyList()
            )

            assertEquals("/v1/create", server.takeRequest().path)
            assertEquals("/v1/status", server.takeRequest().path)
            assertEquals("/media.png", server.takeRequest().path)
            assertTrue(result["files"]!!.jsonArray.isNotEmpty())
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }

    @Test fun responseObserverCanSucceedWhenAJsonPathExistsWithoutReadingItsValue() = runBlocking {
        val server = MockWebServer().apply { start() }
        val root = Files.createTempDirectory("media-chain-path-observer").toFile()
        try {
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("""{"data":{"result":"still-processing"}}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(ByteArray(32))))
            val provider = ModelProvider(
                "p", "test", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString(),
                auth = Authorization(value = "token")
            )
            val chain = MediaRequestChain(
                steps = listOf(
                    MediaRequestStep(
                        endpoint = "/poll",
                        body = "{}",
                        observation = org.starfall.multigateway.shared.model.media.MediaResponseObservation(
                            statusPath = "data.result",
                            mode = MediaResponseObservationMode.PATH_EXISTS,
                            pendingValues = emptyList(),
                            retry = org.starfall.multigateway.shared.model.media.MediaRetryPolicy(
                                maxAttempts = 2,
                                delayMillis = 0,
                                maxWaitMillis = 1_000
                            )
                        )
                    ),
                    MediaRequestStep(endpoint = "/media", method = "GET", body = "{}")
                ),
                finalResponse = MediaFinalResponse(type = MediaResponseType.IMAGE)
            )

            val result = MediaRequestChainExecutor(ToolHttp(ToolFiles(root))).execute(
                chain, provider, "image-model", "draw a tree", emptyList()
            )

            assertEquals("/v1/poll", server.takeRequest().path)
            assertEquals("/v1/poll", server.takeRequest().path)
            assertEquals("/v1/media", server.takeRequest().path)
            assertTrue(result["files"]!!.jsonArray.isNotEmpty())
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }

    @Test fun disabledResponseObserverSkipsRetryAndContinuesToTheNextStep() = runBlocking {
        val server = MockWebServer().apply { start() }
        val root = Files.createTempDirectory("media-chain-observer-disabled").toFile()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"processing"}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(ByteArray(32))))
            val provider = ModelProvider(
                "p", "test", ModelApiType.OPENAI,
                baseUrl = server.url("/v1").toString(),
                auth = Authorization(value = "token")
            )
            val chain = MediaRequestChain(
                steps = listOf(
                    MediaRequestStep(
                        endpoint = "/poll",
                        body = "{}",
                        observation = org.starfall.multigateway.shared.model.media.MediaResponseObservation(
                            enabled = false,
                            statusPath = "status",
                            pendingValues = listOf("processing")
                        )
                    ),
                    MediaRequestStep(endpoint = "/media", method = "GET", body = "{}")
                ),
                finalResponse = MediaFinalResponse(type = MediaResponseType.IMAGE)
            )

            MediaRequestChainExecutor(ToolHttp(ToolFiles(root))).execute(
                chain, provider, "image-model", "draw a tree", emptyList()
            )

            assertEquals("/v1/poll", server.takeRequest().path)
            assertEquals("/v1/media", server.takeRequest().path)
        } finally {
            server.shutdown()
            root.deleteRecursively()
        }
    }
}
