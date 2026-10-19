package org.starfall.multigateway.integrations.media

import org.starfall.multigateway.shared.model.media.VideoApi
import org.starfall.multigateway.shared.model.media.MediaInputImage
import org.starfall.multigateway.shared.model.media.MediaInputVideo
import org.starfall.multigateway.shared.helpers.media.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.networking.ToolHttp
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.providerBase
import org.starfall.multigateway.shared.networking.str
import org.starfall.multigateway.shared.networking.text

class SystemMediaTools(private val http: ToolHttp) {
    suspend fun generate(kind: String, provider: ModelProvider, model: String, prompt: String, imageOptions: JsonObject = obj(), inputImage: String? = null, inputImages: List<String> = emptyList(), videoOptions: JsonObject = obj(), inputImageUrl: String? = null, inputVideo: String? = null, inputVideoUrl: String? = null): JsonObject = withContext(Dispatchers.IO) {
        val files = http.requireFiles()
        require(kind in setOf("generate_image", "generate_video")) { "Unsupported media tool" }
        require(prompt.isNotBlank() && prompt.length <= 32000) { "A prompt of 1–32000 characters is required" }
        require(inputImages.isEmpty() || kind == "generate_image") { "input_images is only supported by generate_image. Video generation accepts one input_image." }
        require(inputImages.size <= 16) { "Image generation accepts at most 16 reference images." }
        if (inputImages.isNotEmpty()) require(provider.type.isOpenAi || provider.type == ModelApiType.GOOGLE) {
            "This provider does not support image editing. Select an OpenAI-compatible or Gemini image provider."
        }
        val referenceImages = inputImages.map { resolveInputImage(it, files, if (provider.type.isOpenAi && !isAgnesProvider(provider)) 50L * 1024 * 1024 - 1 else MAX_MEDIA_INPUT_BYTES) }
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
                if (isAgnesProvider(provider)) {
                    http.postMedia("${agnesApiBase(provider)}/images/generations", agnesImageRequest(model, prompt, imageOptions, referenceImages), provider)
                } else if (referenceImages.isEmpty()) {
                    http.postMedia("$base/images/generations", imageGenerationRequest(provider.type, model, prompt, imageOptions), provider)
                } else {
                    http.postMedia("$base/images/edits", openAiImageEditRequest(model, prompt, imageOptions, referenceImages), provider)
                }
            } else {
                VideoGeneration(http).generate(provider, model, prompt, image, videoOptions, inputImageUrl, video, inputVideoUrl)
            }
            ModelApiType.GOOGLE -> {
                val root = if(Regex("/v1(?:beta|alpha)?$").containsMatchIn(base)) base else "$base/v1beta"
                if(kind == "generate_image") {
                    val method = if(model.contains("imagen",ignoreCase = true)) "predict" else "generateContent"
                    val body = if (referenceImages.isEmpty()) imageGenerationRequest(provider.type, model, prompt, imageOptions)
                        else googleImageEditRequest(model, prompt, imageOptions, referenceImages)
                    http.post("$root/models/$model:$method", body, provider)
                } else {
                    require(videoOptions.isEmpty() && inputImageUrl == null) { "Google video generation uses attached images and provider defaults." }
                    require(image == null || (video == null && inputVideoUrl == null)) { "Google video generation accepts either a source image or a source video, not both." }
                    require(video == null || inputVideoUrl == null) { "Use one source video: an attachment or input_video_url." }
                    var job = http.post("$root/models/$model:predictLongRunning", googleVideoRequest(prompt, image, video, inputVideoUrl), provider)
                    val name = googleVideoOperationName(job)
                    job = withTimeout(15*60*1000L) {
                        var state = job
                        while (!googleVideoOperationDone(state)) {
                            delay(3000)
                            state = http.json(http.request("$root/$name", provider).get().build())
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
