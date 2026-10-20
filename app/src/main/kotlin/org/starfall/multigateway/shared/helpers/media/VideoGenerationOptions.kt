package org.starfall.multigateway.shared.helpers.media

import org.starfall.multigateway.shared.model.media.ImageOptionField
import org.starfall.multigateway.shared.model.media.VideoApi

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.starfall.multigateway.shared.model.provider.ModelProvider

internal fun videoApi(provider: ModelProvider): VideoApi = VideoApi.OPENAI

internal fun validateVideoImageUrl(value: String): String = validateReferenceUrl(value, "images")

internal fun validateReferenceUrl(value: String, kind: String): String {
    val url = value.toHttpUrlOrNull()
    require(url != null && url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty()) {
        "Reference $kind require a publicly accessible HTTPS URL without embedded credentials."
    }
    return value
}

fun videoOptionFields(provider: ModelProvider, model: String): List<ImageOptionField> = emptyList()

fun validateVideoOptions(provider: ModelProvider, model: String, options: JsonObject) {
    require(options.toString().length <= 65536) { "Video settings must be under 64 KB." }
    require(listOf("model", "prompt", "input_reference", "image", "instances").none { it in options }) {
        "Model, prompt, and attached files are supplied by chat."
    }
    val allowed = emptySet<String>()
    require(options.keys.all { it in allowed }) { "Unsupported video settings: ${(options.keys - allowed).joinToString()}." }
    videoOptionFields(provider, model).forEach { field ->
        val value = options[field.path] ?: return@forEach
        val primitive = value as? JsonPrimitive ?: error("${field.label} must be ${field.kind}.")
        if (field.kind == "integer") {
            val number = primitive.longOrNull
            require((!primitive.isString || field.path == "seconds") && number != null && (field.min == null || number >= field.min) && (field.max == null || number <= field.max)) {
                "Invalid ${field.label.lowercase()}."
            }
        } else require(primitive.isString && (field.choices.isEmpty() || primitive.content in field.choices)) { "Invalid ${field.label.lowercase()}." }
    }
}
