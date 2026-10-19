package org.starfall.multigateway.shared.helpers.media

import org.starfall.multigateway.shared.model.media.MediaInputImage
import org.starfall.multigateway.shared.model.media.ImageOptionField
import org.starfall.multigateway.shared.model.provider.ModelProvider

import kotlinx.serialization.json.*
import java.io.File
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.str
import org.starfall.multigateway.shared.networking.text
import org.starfall.multigateway.shared.model.api.ModelApiType

internal const val MAX_MEDIA_INPUT_BYTES = 20L * 1024 * 1024

internal fun resolveInputImage(reference: String, files: ToolFiles, maxBytes: Long = MAX_MEDIA_INPUT_BYTES): MediaInputImage {
    require(reference.startsWith("tool-file:")) { "input_image must be a tool-file: URI returned by an attachment or tool." }
    val name = reference.removePrefix("tool-file:")
    require(name.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9]+"))) { "Invalid input_image tool-file: reference." }
    val file = files.resolve(name) ?: error("The input_image file is unavailable. Attach or generate the image again.")
    require(file.length() in 1..maxBytes) { "Input image is empty or exceeds the selected API's file-size limit (${(maxBytes + 1024 * 1024 - 1) / (1024 * 1024)} MB)." }
    val header = ByteArray(12)
    val count = file.inputStream().use { it.read(header) }
    val mime = when {
        count >= 8 && header.take(8) == listOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "image/png"
        count >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> "image/jpeg"
        count == 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> error("input_image must contain a PNG, JPEG, or WebP image; other MIME types are unsupported.")
    }
    return MediaInputImage(file, mime)
}

internal fun mediaInputImagesArgument(arguments: JsonObject): List<String> {
    val value = arguments["input_images"] ?: return emptyList()
    require(value is JsonArray && value.size in 1..16) { "input_images must be an array of 1–16 tool-file: image URIs." }
    return value.map {
        require(it is JsonPrimitive && it.isString && it.content.isNotBlank()) { "Each input_images entry must be a tool-file: URI string." }
        it.content
    }
}

internal fun mediaInputImageArgument(arguments: JsonObject): String? {
    val value = arguments["input_image"] ?: return null
    require(value is JsonPrimitive && value.isString && value.content.isNotBlank()) {
        "input_image must be a non-empty tool-file: URI string. Omit it for text-to-video."
    }
    return value.content
}

internal fun mediaInputImageUrlArgument(arguments: JsonObject): String? {
    val value = arguments["input_image_url"] ?: return null
    require(value is JsonPrimitive && value.isString && value.content.isNotBlank()) { "input_image_url must be a public HTTPS URL string." }
    return validateVideoImageUrl(value.content)
}

/** Parameters exposed to the model for a selected media tool and model. */
data class MediaToolParameter(val key: String, val label: String)

fun mediaToolOptionalParams(name: String, provider: ModelProvider? = null, model: String = ""): List<MediaToolParameter> {
    val inputs = when (name) {
        "generate_video" -> listOf(
            MediaToolParameter("input_image", "Reference image (attached file)"),
            MediaToolParameter("input_image_url", "Reference image URL")
        )
        "generate_image" -> listOf(MediaToolParameter("input_images", "Reference images (attached files)"))
        else -> emptyList()
    }
    val options = if (provider == null || model.isBlank()) emptyList() else mediaToolOptionFields(name, provider, model)
        .map { MediaToolParameter(it.path, it.label) }
    return inputs + options
}

private fun mediaToolOptionFields(name: String, provider: ModelProvider, model: String): List<ImageOptionField> = when (name) {
    "generate_image" -> imageOptionFields(provider, model)
    "generate_video" -> videoOptionFields(provider, model)
    else -> emptyList()
}

internal fun mediaToolSchema(
    name: String,
    disabledParams: Set<String> = emptySet(),
    provider: ModelProvider? = null,
    model: String = ""
): JsonObject = obj(
    "type" to str("object"),
    "properties" to buildJsonObject {
        put("prompt", obj("type" to str("string"), "description" to str("Detailed instructions for the image or video to generate.")))
        if (name == "generate_video" && "input_image" !in disabledParams) put("input_image", obj(
            "type" to str("string"),
            "description" to str("Optional tool-file: URI of an attached image to animate. Reuse it directly.")
        ))
        if (name == "generate_video" && "input_image_url" !in disabledParams) put("input_image_url", obj(
            "type" to str("string"),
            "description" to str("Optional public HTTPS source-image URL for Agnes Video. Use either input_image or input_image_url, not both.")
        ))
        if (name == "generate_image" && "input_images" !in disabledParams) put("input_images", obj(
            "type" to str("array"), "items" to obj("type" to str("string")),
            "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(16),
            "description" to str("Optional tool-file: URIs of attached images to edit or use as references. Reuse directly.")
        ))
        if (provider != null && model.isNotBlank()) {
            mediaToolOptionFields(name, provider, model).filter { it.path !in disabledParams }.forEach { field ->
                put(field.path, buildJsonObject {
                    put("type", str(when (field.kind) { "integer" -> "integer"; "number" -> "number"; else -> "string" }))
                    put("description", str("Optional ${field.label}. Overrides the configured default for this call."))
                    if (field.choices.isNotEmpty()) put("enum", JsonArray(field.choices.map(::str)))
                    field.min?.let { put("minimum", JsonPrimitive(it)) }
                    field.max?.let { put("maximum", JsonPrimitive(it)) }
                })
            }
        }
    },
    "required" to JsonArray(listOf(str("prompt")))
)

/** Merge only exposed per-call options over the user's saved defaults. */
internal fun configuredMediaOptions(
    name: String, provider: ModelProvider, model: String, defaults: JsonObject,
    arguments: JsonObject, disabledParams: Set<String>
): JsonObject {
    var result = defaults
    mediaToolOptionFields(name, provider, model).forEach { field ->
        if (field.path !in disabledParams) arguments[field.path]?.let { value ->
            result = result.withOption(field.path, value)
        }
    }
    return result
}

internal fun toolAttachmentReferences(attachments: JsonArray): String =
    if (attachments.isEmpty()) "" else attachments.mapIndexed { index, item ->
        val attachment = item.jsonObject
        "Attachment ${index + 1}: ${attachment.text("mimeType")}; URI: ${attachment.text("uri")}"
    }.joinToString("\n", prefix = "\nAvailable attachments for tool inputs (reuse these URIs directly):\n")
