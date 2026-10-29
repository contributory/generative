package org.starfall.generative.integrations.media

import org.starfall.generative.shared.model.media.MediaInputImage
import org.starfall.generative.shared.model.media.MediaInputVideo
import org.starfall.generative.shared.model.media.MediaGenerationMode
import org.starfall.generative.shared.model.media.MediaRequestChain
import org.starfall.generative.shared.helpers.media.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.networking.ToolHttp
import org.starfall.generative.shared.networking.obj
import org.starfall.generative.shared.networking.providerBase
import org.starfall.generative.shared.networking.str
import org.starfall.generative.shared.networking.text

class SystemMediaTools(private val http: ToolHttp) {
    suspend fun generate(kind: String, provider: ModelProvider, model: String, prompt: String, imageOptions: JsonObject = obj(), inputImage: String? = null, inputImages: List<String> = emptyList(), videoOptions: JsonObject = obj(), inputImageUrl: String? = null, inputVideo: String? = null, inputVideoUrl: String? = null, generationMode: MediaGenerationMode = MediaGenerationMode.DEFAULT, requestChain: MediaRequestChain? = null): JsonObject = withContext(Dispatchers.IO) {
        val files = http.requireFiles()
        require(kind in setOf("generate_image", "generate_video")) { "Unsupported media tool" }
        require(prompt.isNotBlank() && prompt.length <= 32000) { "A prompt of 1–32000 characters is required" }
        if (generationMode == MediaGenerationMode.CUSTOM) {
            val customChain = requestChain ?: error("Custom media generation is selected, but no request chain is configured.")
            require(inputImageUrl == null && inputVideoUrl == null) {
                "Configured request chains use attached files; map URL inputs explicitly in a future request variable."
            }
            val references = if (kind == "generate_image") inputImages else listOfNotNull(inputImage, inputVideo)
            return@withContext MediaRequestChainExecutor(http).execute(customChain, provider, model, prompt, references)
        }
        require(inputImages.isEmpty() || kind == "generate_image") { "input_images is only supported by generate_image. Video generation accepts one input_image." }
        require(inputImages.size <= 16) { "Image generation accepts at most 16 reference images." }
        if (inputImages.isNotEmpty()) require(provider.type.isOpenAi || provider.type == ModelApiType.GOOGLE) {
            "This provider does not support image editing. Select an OpenAI-compatible or Gemini image provider."
        }
        val referenceImages = inputImages.map { resolveInputImage(it, files, if (provider.type.isOpenAi) 50L * 1024 * 1024 - 1 else MAX_MEDIA_INPUT_BYTES) }
        val image = inputImage?.let {
            require(kind == "generate_video") { "input_image is only supported by generate_video." }
            require(provider.type.isOpenAi || provider.type == ModelApiType.GOOGLE) {
                "This provider does not support image-to-video. Select an OpenAI-compatible or Google video provider."
            }
            resolveInputImage(it, files)
        }
        require(inputImageUrl == null || kind == "generate_video") { "input_image_url is only supported by generate_video." }
        val video = inputVideo?.let {
            require(kind == "generate_video") { "input_video is only supported by generate_video." }
            require(provider.type.isOpenAi || provider.type == ModelApiType.GOOGLE) {
                "This provider does not support video-to-video. Select an OpenAI-compatible or Google video provider."
            }
            resolveInputVideo(it, files)
        }
        require(inputVideoUrl == null || kind == "generate_video") { "input_video_url is only supported by generate_video." }
        inputVideoUrl?.let { validateReferenceUrl(it, "videos") }
        val base = providerBase(provider)
        val response = when(provider.type) {
            ModelApiType.OPENAI, ModelApiType.OPENAI_RESPONSES -> if(kind == "generate_image") {
                if (referenceImages.isEmpty()) {
                    http.postMedia(provider.config.customEndpoints["imageGeneration"]?.takeIf { it.isNotBlank() } ?: "$base/images/generations", imageGenerationRequest(provider.type, model, prompt, imageOptions), provider)
                } else {
                    http.postMedia(provider.config.customEndpoints["imageEdit"]?.takeIf { it.isNotBlank() } ?: "$base/images/edits", openAiImageEditRequest(model, prompt, imageOptions, referenceImages), provider)
                }
            } else {
                VideoGeneration(http).generate(provider, model, prompt, image, videoOptions, inputImageUrl, video, inputVideoUrl)
            }
            ModelApiType.GOOGLE -> {
                val root = base
                if(kind == "generate_image") {
                    val method = if(model.contains("imagen",ignoreCase = true)) "predict" else "generateContent"
                    val body = if (referenceImages.isEmpty()) imageGenerationRequest(provider.type, model, prompt, imageOptions)
                        else googleImageEditRequest(model, prompt, imageOptions, referenceImages)
                    http.post(provider.config.customEndpoints["generate"]?.takeIf { it.isNotBlank() }?.replace("{model}", model)?.replace("{method}", method) ?: "$root/models/$model:$method", body, provider)
                } else {
                    require(inputImageUrl == null) { "Google video generation needs an attached source image, not an image URL." }
                    validateVideoOptions(provider, model, videoOptions)
                    require(image == null || (video == null && inputVideoUrl == null)) { "Google video generation accepts either a source image or a source video, not both." }
                    require(video == null || inputVideoUrl == null) { "Use one source video: an attachment or input_video_url." }
                    var job = http.post(provider.config.customEndpoints["generate"]?.takeIf { it.isNotBlank() }?.replace("{model}", model)?.replace("{method}", "predictLongRunning") ?: "$root/models/$model:predictLongRunning", googleVideoRequest(prompt, image, video, inputVideoUrl, videoOptions), provider)
                    val name = googleVideoOperationName(job)
                    job = withTimeout(15*60*1000L) {
                        var state = job
                        while (!googleVideoOperationDone(state)) {
                            delay(3000)
                            state = http.json(http.request(provider.config.customEndpoints["operation"]?.takeIf { it.isNotBlank() }?.replace("{operation}", name) ?: "$root/$name", provider).get().build())
                        }
                        state
                    }
                    validateGoogleVideoOperation(job)
                    job
                }
            }
            else -> error("This provider has no supported image/video generation API. Use an OpenAI-compatible or Google provider.")
        }
        val names = generatedMediaReferences(response, base).map { reference ->
            if (!reference.isRemoteUrl) reference.value
            else http.download(reference.value, if (reference.useProviderAuth) provider else null)
        }
        check(names.isNotEmpty()) { "Provider returned no supported media. Check the selected model and endpoint." }
        obj("files" to JsonArray(names.distinct().map { str("tool-file:$it") }), "message" to str("Media saved and displayed to the user."))
    }
}
