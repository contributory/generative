package org.starfall.multigateway.shared.helpers.media

import org.starfall.multigateway.shared.model.media.MediaInputImage
import org.starfall.multigateway.shared.model.media.MediaInputVideo

import kotlinx.serialization.json.*
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.str
import java.util.Base64

/** OpenAI-compatible video APIs use multipart requests. */
internal fun openAiVideoRequest(model: String, prompt: String, image: MediaInputImage? = null, video: MediaInputVideo? = null): MultipartBody {
    require(image == null || video == null) { "OpenAI-compatible video APIs accept one input_reference: an image or a video." }
    val reference = image?.let { it.file to it.mimeType } ?: video?.let { it.file to it.mimeType }
    return MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("model", model)
        .addFormDataPart("prompt", prompt)
        .apply {
            reference?.let { (file, mime) ->
                addFormDataPart("input_reference", file.name, file.asRequestBody(mime.toMediaType()))
            }
        }.build()
}

/** Gemini Veo REST uses an inline image, and an inline video or a hosted video URI, in the generation instance. */
internal fun googleVideoRequest(prompt: String, image: MediaInputImage? = null, video: MediaInputVideo? = null, videoUrl: String? = null): JsonObject =
    obj("instances" to JsonArray(listOf(buildJsonObject {
        put("prompt", prompt)
        image?.let {
            put("image", obj("inlineData" to obj(
                "mimeType" to str(it.mimeType),
                "data" to str(Base64.getEncoder().encodeToString(it.file.readBytes()))
            )))
        }
        video?.let {
            put("video", obj("inlineData" to obj(
                "mimeType" to str(it.mimeType),
                "data" to str(Base64.getEncoder().encodeToString(it.file.readBytes()))
            )))
        }
        videoUrl?.let { put("video", obj("uri" to str(it))) }
    })))
