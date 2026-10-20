package org.starfall.multigateway.shared.helpers.media

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.starfall.multigateway.shared.model.media.MediaFileTransport
import java.util.Base64

internal const val MEDIA_FILE_TOKEN_PREFIX = "__multigateway_media_file_"

internal data class MediaTemplateInput(
    val name: String,
    val mimeType: String,
    val base64: String
)

internal data class MediaTemplateContext(
    val prompt: String,
    val model: String,
    val apiToken: String,
    val inputFiles: List<MediaTemplateInput>,
    val responses: List<JsonElement> = emptyList()
)

private val placeholderPattern = Regex("""\{\{\s*([^{}]+?)\s*\}\}""")

internal fun resolveMediaTemplate(
    value: JsonElement,
    context: MediaTemplateContext,
    fileTransport: MediaFileTransport
): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.mapValues { (_, child) -> resolveMediaTemplate(child, context, fileTransport) })
    is JsonArray -> JsonArray(value.map { resolveMediaTemplate(it, context, fileTransport) })
    is JsonPrimitive -> if (value.isString && placeholderPattern.containsMatchIn(value.content)) {
        resolveMediaString(value.content, context, fileTransport)
    } else value
    JsonNull -> value
}

internal fun resolveMediaString(
    value: String,
    context: MediaTemplateContext,
    fileTransport: MediaFileTransport
): JsonElement {
    val matches = placeholderPattern.findAll(value).toList()
    if (matches.isEmpty()) return JsonPrimitive(value)
    if (matches.size == 1 && matches.single().range == value.indices) {
        return resolvePlaceholder(matches.single().groupValues[1].trim(), context, fileTransport)
    }
    var result = value
    matches.asReversed().forEach { match ->
        val resolved = resolvePlaceholder(match.groupValues[1].trim(), context, fileTransport)
        require(resolved is JsonPrimitive && resolved.isString) {
            "A list or response object can only be used as a complete JSON value."
        }
        result = result.substring(0, match.range.first) + resolved.content + result.substring(match.range.last + 1)
    }
    return JsonPrimitive(result)
}

private fun resolvePlaceholder(
    name: String,
    context: MediaTemplateContext,
    fileTransport: MediaFileTransport
): JsonElement = when {
    name == "prompt" -> JsonPrimitive(context.prompt)
    name == "model" -> JsonPrimitive(context.model)
    name == "api_token" -> {
        require(context.apiToken.isNotBlank()) { "{{api_token}} is not available for this provider." }
        JsonPrimitive(context.apiToken)
    }
    name == "input_files" -> buildJsonArray {
        context.inputFiles.forEachIndexed { index, input ->
            add(resolveInputFile(input, index, fileTransport))
        }
    }
    name.matches(Regex("input_file\\[\\d+\\]")) -> {
        val index = name.substringAfter('[').substringBefore(']').toInt()
        require(index in context.inputFiles.indices) { "{{input_file[$index]}} is not available." }
        resolveInputFile(context.inputFiles[index], index, fileTransport)
    }
    name.startsWith("response_") -> resolveResponsePlaceholder(name, context.responses)
    else -> error("Unsupported media request variable {{$name}}.")
}

private fun resolveInputFile(
    input: MediaTemplateInput,
    index: Int,
    transport: MediaFileTransport
): JsonElement = when (transport) {
    MediaFileTransport.BASE64 -> JsonPrimitive(input.base64)
    MediaFileTransport.FILE -> JsonPrimitive("$MEDIA_FILE_TOKEN_PREFIX$index")
}

private fun resolveResponsePlaceholder(name: String, responses: List<JsonElement>): JsonElement {
    val match = Regex("response_(\\d+)(?:\\.(.*))?$").matchEntire(name)
        ?: error("Invalid response variable {{$name}}.")
    val index = match.groupValues[1].toInt() - 1
    require(index in responses.indices) { "{{$name}} refers to a response that does not exist." }
    val path = match.groupValues[2]
    if (path.isBlank()) return responses[index]
    val selected = selectMediaPath(responses[index], path)
    return if (selected.size == 1) selected.single() else JsonArray(selected)
}

internal fun selectMediaPath(root: JsonElement, path: String): List<JsonElement> {
    val segments = parseMediaPath(path)
    require(segments.isNotEmpty()) { "A JSON response path is required." }
    var current = listOf(root)
    segments.forEach { segment ->
        current = current.flatMap { value ->
            when (segment) {
                is MediaPathSegment.Key -> listOfNotNull((value as? JsonObject)?.get(segment.value))
                is MediaPathSegment.Index -> listOfNotNull((value as? JsonArray)?.getOrNull(segment.value))
                MediaPathSegment.Wildcard -> (value as? JsonArray).orEmpty()
            }
        }
        require(current.isNotEmpty()) { "JSON response path '$path' did not match a value." }
    }
    return current
}

private sealed interface MediaPathSegment {
    data class Key(val value: String) : MediaPathSegment
    data class Index(val value: Int) : MediaPathSegment
    data object Wildcard : MediaPathSegment
}

private fun parseMediaPath(path: String): List<MediaPathSegment> {
    val segments = mutableListOf<MediaPathSegment>()
    var cursor = 0
    while (cursor < path.length) {
        when (path[cursor]) {
            '.' -> cursor++
            '[' -> {
                val end = path.indexOf(']', cursor + 1)
                require(end >= 0) { "Invalid JSON response path '$path'." }
                val index = path.substring(cursor + 1, end)
                segments += if (index.isBlank()) MediaPathSegment.Wildcard
                else MediaPathSegment.Index(index.toIntOrNull() ?: error("Invalid array index in '$path'."))
                cursor = end + 1
            }
            else -> {
                val start = cursor
                while (cursor < path.length && path[cursor] != '.' && path[cursor] != '[') cursor++
                segments += MediaPathSegment.Key(path.substring(start, cursor).also {
                    require(it.isNotBlank()) { "Invalid JSON response path '$path'." }
                })
            }
        }
    }
    return segments
}

internal fun decodeMediaBase64(value: String): ByteArray {
    val encoded = value.substringAfter(',', value).replace("\\/", "/")
    return runCatching { Base64.getDecoder().decode(encoded) }
        .getOrElse { error("Response media is not valid base64.") }
}
