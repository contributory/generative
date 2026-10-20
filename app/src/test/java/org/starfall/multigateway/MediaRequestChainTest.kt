package org.starfall.multigateway

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import org.starfall.multigateway.shared.model.media.MediaValueKind
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.networking.ToolHttp
import java.nio.file.Files

class MediaRequestChainTest {
    private fun context(files: List<MediaTemplateInput> = emptyList(), responses: List<String> = emptyList()) =
        MediaTemplateContext(
            prompt = "draw a tree",
            model = "image-model",
            apiToken = "secret",
            inputFiles = files,
            responses = responses.map { Json.parseToJsonElement(it) }
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
            Json.parseToJsonElement("""{"id":"{{response_1.data.id}}"}"""),
            context(responses = listOf(response.toString())),
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
                    endpoint = "/video/result/{{response_1.data.id}}",
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
                        endpoint = "/video/status/{{response_1.data.id}}",
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
}
