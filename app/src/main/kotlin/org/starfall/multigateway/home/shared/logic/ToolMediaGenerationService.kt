package org.starfall.multigateway.home.shared.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import org.starfall.multigateway.integrations.media.MAX_MEDIA_INPUT_BYTES
import org.starfall.multigateway.integrations.media.SystemMediaTools
import org.starfall.multigateway.integrations.media.isAgnesProvider
import org.starfall.multigateway.integrations.modelapis.AiService
import org.starfall.multigateway.shared.model.ToolActivity
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.networking.ToolHttp
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.str
import org.starfall.multigateway.home.shared.logic.summarizeToolResult

/** Handles direct image/video generation and emits its progress as tool events. */
internal class ToolMediaGenerationService(
    private val http: ToolHttp,
    private val aiService: AiService
) {
    suspend fun executeConfiguredTool(
        name: String,
        provider: ModelProvider,
        model: String,
        prompt: String,
        imageOptions: JsonObject,
        inputImage: String?,
        inputImages: List<String>,
        videoOptions: JsonObject,
        inputImageUrl: String?
    ): JsonObject = SystemMediaTools(http).generate(
        name, provider, model, prompt, imageOptions,
        inputImage, inputImages, videoOptions = videoOptions, inputImageUrl = inputImageUrl
    )

    fun generateMedia(
        provider: ModelProvider,
        model: String,
        kind: ModelType,
        prompt: String,
        imageOptions: JsonObject = obj(),
        attachments: List<String> = emptyList(),
        videoOptions: JsonObject = obj()
    ): Flow<GenerationEvent> = flow {
        require(kind == ModelType.IMAGE_GENERATION || kind == ModelType.VIDEO_GENERATION)
        val name = if (kind == ModelType.IMAGE_GENERATION) "generate_image" else "generate_video"
        var activity = ToolActivity(UUID.randomUUID().toString(), name,
            arguments = obj("prompt" to str(prompt)).toString())
        emit(GenerationEvent.Tool(activity))
        try {
            require(kind != ModelType.VIDEO_GENERATION || attachments.size <= 1) {
                "Video generation accepts one reference image. Remove extra attachments."
            }
            require(attachments.size <= 16) { "Image generation accepts at most 16 reference images." }
            val maxBytes = if (kind == ModelType.IMAGE_GENERATION && provider.type.isOpenAi && !isAgnesProvider(provider)) 50L * 1024 * 1024 - 1 else MAX_MEDIA_INPUT_BYTES
            val inputs = aiService.importToolAttachments(attachments, http.requireFiles(), maxBytes)
            if (inputs.isNotEmpty()) {
                activity = activity.copy(arguments = buildJsonObject {
                    put("prompt", prompt)
                    if (kind == ModelType.VIDEO_GENERATION) put("input_image", inputs.single())
                    else put("input_images", JsonArray(inputs.map(::str)))
                }.toString())
                emit(GenerationEvent.Tool(activity))
            }
            val result = SystemMediaTools(http).generate(name, provider, model, prompt, imageOptions,
                inputImage = if (kind == ModelType.VIDEO_GENERATION) inputs.singleOrNull() else null,
                inputImages = if (kind == ModelType.IMAGE_GENERATION) inputs else emptyList(),
                videoOptions = videoOptions)
            val summary = summarizeToolResult(result, http.requireFiles())
            emit(GenerationEvent.Tool(activity.copy(status = "success", summary = summary.preview,
                files = summary.files, response = summary.content.toString())))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = obj(
                "isError" to JsonPrimitive(true),
                "status" to str("error"),
                "error" to str(e.message.orEmpty().take(500))
            )
            emit(GenerationEvent.Tool(activity.copy(
                status = "error",
                summary = e.message.orEmpty().take(500),
                response = error.toString()
            )))
            throw e
        }
    }

}
