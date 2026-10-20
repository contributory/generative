package org.starfall.generative.shared.helpers.media

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.starfall.generative.shared.model.media.MediaFileTransport
import java.util.Base64
import java.util.Locale

internal const val MEDIA_FILE_TOKEN_PREFIX = "__generative_media_file_"

internal data class MediaTemplateInput(
    val name: String,
    val mimeType: String,
    val base64: String
)

internal data class MediaStepTemplateContext(
    val url: String,
    val requestMethod: String,
    val requestHeaders: Map<String, String>,
    val requestBody: JsonElement,
    val responseBody: JsonElement,
    val responseHeaders: Map<String, String>,
    val responseStatusCode: Int
)

internal data class MediaTemplateContext(
    val prompt: String,
    val model: String,
    val apiToken: String,
    val inputFiles: List<MediaTemplateInput>,
    val responses: List<JsonElement> = emptyList(),
    val responseHeaders: List<Map<String, String>> = emptyList(),
    val steps: List<MediaStepTemplateContext> = emptyList()
)

private val placeholderPattern = Regex("""\{\{\s*([^{}]+?)\s*\}\}""")

/** Accept a provider-relative endpoint or a complete HTTP(S) URL. */
internal fun isValidMediaEndpointTemplate(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed.startsWith("//") || trimmed.contains('#')) return false
    val candidate = placeholderPattern.replace(trimmed) { "placeholder" }
    val lower = candidate.lowercase(Locale.ROOT)
    val absolute = lower.startsWith("http://") || lower.startsWith("https://")
    if (absolute) {
        val url = candidate.toHttpUrlOrNull() ?: return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) return false
    } else if (candidate.contains("://")) {
        return false
    }
    return candidate.split('/', '?', '#').none { it == ".." }
}

internal fun isAbsoluteMediaEndpoint(value: String): Boolean {
    val lower = value.trim().lowercase(Locale.ROOT)
    return lower.startsWith("http://") || lower.startsWith("https://")
}

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
        context.inputFiles.getOrNull(index)?.let { resolveInputFile(it, index, fileTransport) } ?: JsonNull
    }
    name.startsWith("step") -> resolveStepPlaceholder(name, context)
    // Keep decoding profiles created before the stepN.* syntax was introduced.
    name.startsWith("response_") -> resolveLegacyResponsePlaceholder(name, context.responses)
    name.startsWith("headers_") -> resolveLegacyHeaderPlaceholder(name, context.responseHeaders)
    else -> error("Unsupported media request variable {{$name}}.")
}

private fun resolveInputFile(
    input: MediaTemplateInput,
    index: Int,
    transport: MediaFileTransport
): JsonElement = when (transport) {
    MediaFileTransport.BASE64 -> JsonPrimitive(input.base64)
    MediaFileTransport.FILE, MediaFileTransport.RAW -> JsonPrimitive("$MEDIA_FILE_TOKEN_PREFIX$index")
}

private fun resolveStepPlaceholder(name: String, context: MediaTemplateContext): JsonElement {
    val match = Regex("step(\\d+)\\.(.+)").matchEntire(name)
        ?: error("Invalid step variable {{$name}}. Use {{step1.response.body.path}} or {{step1.response.headers.header}}.")
    val index = match.groupValues[1].toIntOrNull()?.minus(1)
        ?: error("Invalid step variable {{$name}}.")
    require(index in context.steps.indices) { "{{$name}} refers to a step that does not exist." }
    val step = context.steps[index]
    val expression = match.groupValues[2]
    return when {
        expression == "url" || expression == "request.url" -> JsonPrimitive(step.url)
        expression == "method" || expression == "request.method" -> JsonPrimitive(step.requestMethod)
        expression == "response.status" || expression == "response.status_code" -> JsonPrimitive(step.responseStatusCode)
        expression == "response.body" -> step.responseBody
        expression.startsWith("response.body.") ->
            resolveJsonPath(step.responseBody, expression.removePrefix("response.body."), name)
        expression.startsWith("response.headers.") ->
            resolveHeaderAt(step.responseHeaders, expression.removePrefix("response.headers."), name)
        expression.startsWith("request.headers.") ->
            resolveHeaderAt(step.requestHeaders, expression.removePrefix("request.headers."), name)
        expression == "request.body" -> step.requestBody
        expression.startsWith("request.body.") ->
            resolveJsonPath(step.requestBody, expression.removePrefix("request.body."), name)
        else -> error("Unsupported step variable {{$name}}. Use stepN.response.body, stepN.response.headers.name, stepN.request.headers.name, or stepN.url.")
    }
}

private fun resolveLegacyResponsePlaceholder(name: String, responses: List<JsonElement>): JsonElement {
    val match = Regex("response_(\\d+)(?:\\.(.*))?$").matchEntire(name)
        ?: error("Invalid response variable {{$name}}.")
    val index = match.groupValues[1].toInt() - 1
    return resolveResponseAt(index, match.groupValues[2], responses, name)
}

private fun resolveResponseAt(index: Int, path: String, responses: List<JsonElement>, name: String): JsonElement {
    require(index in responses.indices) { "{{$name}} refers to a response that does not exist." }
    if (path.isBlank()) return responses[index]
    return resolveJsonPath(responses[index], path, name)
}

private fun resolveLegacyHeaderPlaceholder(name: String, responseHeaders: List<Map<String, String>>): JsonElement {
    val match = Regex("headers_(\\d+)\\.(.+)").matchEntire(name)
        ?: error("Invalid response header variable {{$name}}. Use {{step1.response.headers.someheaderkey}}.")
    val index = match.groupValues[1].toIntOrNull()?.minus(1)
        ?: error("Invalid response header variable {{$name}}.")
    require(index in responseHeaders.indices) { "{{$name}} refers to a response that does not exist." }
    return resolveHeaderAt(responseHeaders[index], match.groupValues[2], name)
}

private fun resolveHeaderAt(headers: Map<String, String>, headerName: String, name: String): JsonElement {
    require(headerName.isNotBlank()) { "{{$name}} must include a response header name." }
    val value = headers.entries.firstOrNull { it.key.equals(headerName, ignoreCase = true) }?.value
        ?: error("{{$name}} refers to a response header that does not exist.")
    return JsonPrimitive(value)
}

private fun resolveJsonPath(root: JsonElement, path: String, name: String): JsonElement {
    val selected = runCatching { selectMediaPath(root, path) }
        .getOrElse { error("{{$name}} refers to an invalid JSON path: ${it.message.orEmpty()}") }
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
