package org.starfall.multigateway.shared.helpers.media

import org.starfall.multigateway.shared.model.media.MediaInputImage
import org.starfall.multigateway.shared.model.media.MediaInputVideo

import kotlinx.serialization.json.*
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.Base64
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.str
import org.starfall.multigateway.shared.networking.text

/** OpenAI Videos uses multipart; Agnes uses a separate JSON protocol. */
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

/**
 * Agnes takes public URLs only. A source image alone is the first frame (keyframe mode); together with a
 * source video both become references (reference mode), since keyframes and references cannot be mixed.
 */
internal fun agnesVideoInputs(options: JsonObject, imageUrl: String?, videoUrl: String?): JsonObject {
    var inputs = options
    if (videoUrl != null) {
        inputs = JsonObject(inputs + ("videos" to JsonArray(listOf(obj("url" to str(validateReferenceUrl(videoUrl, "videos")))))))
    }
    if (imageUrl != null) {
        val url = validateVideoImageUrl(imageUrl)
        inputs = if (videoUrl == null) JsonObject(inputs + ("first_frame" to str(url)))
        else JsonObject(inputs + ("images" to JsonArray((inputs["images"] as? JsonArray).orEmpty() + str(url))))
    }
    return inputs
}

internal fun agnesVideoRequest(model: String, prompt: String, options: JsonObject, imageUrl: String?, videoUrl: String? = null): JsonObject {
    val inputs = agnesVideoInputs(options, imageUrl, videoUrl)
    val mode = inputs.text("mode").ifBlank {
        when {
            listOf("first_frame", "last_frame").any { it in inputs } -> "keyframe"
            listOf("images", "audios", "videos").any { it in inputs } -> "reference"
            else -> "text"
        }
    }
    return buildJsonObject {
        put("seconds", "5"); put("size", "720P"); put("aspect_ratio", "16:9")
        inputs.forEach { (key, value) -> put(key, if (key == "seconds") str(value.jsonPrimitive.content) else value) }
        put("model", model); put("prompt", prompt); put("mode", mode)
    }
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
