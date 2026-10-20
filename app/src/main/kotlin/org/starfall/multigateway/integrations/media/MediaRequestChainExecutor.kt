package org.starfall.multigateway.integrations.media

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.helpers.media.MEDIA_FILE_TOKEN_PREFIX
import org.starfall.multigateway.shared.helpers.media.MediaTemplateContext
import org.starfall.multigateway.shared.helpers.media.MediaTemplateInput
import org.starfall.multigateway.shared.helpers.media.MAX_MEDIA_INPUT_VIDEO_BYTES
import org.starfall.multigateway.shared.helpers.media.decodeMediaBase64
import org.starfall.multigateway.shared.helpers.media.resolveMediaString
import org.starfall.multigateway.shared.helpers.media.resolveMediaTemplate
import org.starfall.multigateway.shared.helpers.media.selectMediaPath
import org.starfall.multigateway.shared.model.media.MediaFileTransport
import org.starfall.multigateway.shared.model.media.MediaRequestChain
import org.starfall.multigateway.shared.model.media.MediaRequestMode
import org.starfall.multigateway.shared.model.media.MediaRequestStep
import org.starfall.multigateway.shared.model.media.MediaResponseType
import org.starfall.multigateway.shared.model.media.MediaValueKind
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.networking.ToolHttp
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.providerBase
import org.starfall.multigateway.shared.networking.str
import java.io.File
import java.util.Locale

internal class MediaRequestChainExecutor(private val http: ToolHttp) {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private data class InputFile(
        val file: File,
        val template: MediaTemplateInput
    )

    private data class StepResult(
        val value: JsonElement,
        val rawFile: String? = null
    )

    private enum class Observation { READY, PENDING, ERROR }

    suspend fun execute(
        chain: MediaRequestChain,
        provider: ModelProvider,
        model: String,
        prompt: String,
        inputReferences: List<String>
    ): JsonObject {
        require(chain.steps.isNotEmpty()) { "At least one media request step is required." }
        val inputs = resolveInputs(http.requireFiles(), inputReferences)
        val responses = mutableListOf<JsonElement>()
        var last = StepResult(JsonObject(emptyMap()))
        chain.steps.forEachIndexed { index, step ->
            var attempts = 0
            val startedAt = System.currentTimeMillis()
            while (true) {
                attempts++
                val context = MediaTemplateContext(
                    prompt = prompt,
                    model = model,
                    apiToken = provider.auth.token,
                    inputFiles = inputs.map { it.template },
                    responses = responses.toList()
                )
                last = executeStep(
                    provider,
                    step,
                    context,
                    inputs,
                    isFinal = index == chain.steps.lastIndex,
                    finalResponseType = chain.finalResponse.type
                )
                val observation = observe(step, last.value)
                when (observation) {
                    Observation.READY -> break
                    Observation.ERROR -> error("Media API returned an error: ${safeResponse(last.value, provider)}")
                    Observation.PENDING -> {
                        val policy = step.observation.retry
                        val elapsed = System.currentTimeMillis() - startedAt
                        val canRetry = attempts < policy.maxAttempts.coerceAtLeast(1) &&
                            elapsed + policy.delayMillis.coerceAtLeast(0) <= policy.maxWaitMillis.coerceAtLeast(0)
                        if (!canRetry) {
                            error("Media API did not become ready within the configured retry limit: ${safeResponse(last.value, provider)}")
                        }
                        delay(policy.delayMillis.coerceAtLeast(0))
                    }
                }
            }
            responses += last.value
            if (index == chain.steps.lastIndex) return finish(chain, last, provider)
        }
        error("Media request chain did not produce a final response.")
    }

    private fun resolveInputs(files: ToolFiles, references: List<String>): List<InputFile> = references.map { reference ->
        val name = files.nameOf(reference) ?: reference.removePrefix("tool-file:")
        val file = files.resolve(name) ?: error("Input file is no longer available.")
        require(file.length() in 1..MAX_MEDIA_INPUT_VIDEO_BYTES) {
            "Input files must be non-empty and no larger than ${MAX_MEDIA_INPUT_VIDEO_BYTES / (1024 * 1024)} MB."
        }
        InputFile(file, MediaTemplateInput(
            name = file.name,
            mimeType = detectMime(file),
            base64 = java.util.Base64.getEncoder().encodeToString(file.readBytes())
        ))
    }

    private suspend fun executeStep(
        provider: ModelProvider,
        step: MediaRequestStep,
        context: MediaTemplateContext,
        inputs: List<InputFile>,
        isFinal: Boolean,
        finalResponseType: MediaResponseType
    ): StepResult {
        val endpoint = resolveEndpoint(provider, resolveText(step.endpoint, context))
        val headers = step.headers.mapValues { (_, value) -> resolveText(value, context) }
        val body = step.body.trim().ifBlank { "{}" }
        val template = runCatching { json.parseToJsonElement(body) }
            .getOrElse { error("Request step JSON is invalid: ${it.message.orEmpty()}") }
        val resolved = resolveMediaTemplate(template, context, step.fileTransport)
        val requestBody = when (step.mode) {
            MediaRequestMode.JSON -> {
                require(!containsFileToken(resolved)) { "JSON request steps must use base64 for input files." }
                resolved.toString().toRequestBody("application/json".toMediaType())
            }
            MediaRequestMode.FORM -> formBody(resolved, inputs)
        }
        val builder = http.request(
            endpoint,
            provider,
            headers,
            provider.auth.token.takeIf { it.isNotBlank() }
        )
        val request = applyMethod(builder, step.method, requestBody).build()
        val responseType = if (isFinal) finalResponseType else MediaResponseType.JSON
        return when (responseType) {
            MediaResponseType.JSON -> http.withResponse(request) { response ->
                http.requireSuccess(response)
                val text = response.body.string()
                require(text.length <= 8 * 1024 * 1024) { "Media API JSON response exceeds 8 MB." }
                StepResult(json.parseToJsonElement(text))
            }
            MediaResponseType.IMAGE, MediaResponseType.VIDEO, MediaResponseType.OCTET_STREAM ->
                http.withResponse(request) { response ->
                    http.requireSuccess(response)
                    val mime = response.header("Content-Type").orEmpty().substringBefore(';').ifBlank {
                        when (responseType) {
                            MediaResponseType.IMAGE -> "image/*"
                            MediaResponseType.VIDEO -> "video/*"
                            else -> "application/octet-stream"
                        }
                    }
                    val name = http.requireFiles().save(response.body.byteStream(), mime)
                    StepResult(obj("file" to str("tool-file:$name")), name)
                }
        }
    }

    private fun applyMethod(builder: Request.Builder, method: String, body: RequestBody): Request.Builder = when (method.trim().uppercase(Locale.ROOT)) {
        "GET" -> builder.get()
        "POST" -> builder.post(body)
        "PUT" -> builder.put(body)
        "PATCH" -> builder.patch(body)
        "DELETE" -> builder.delete(body)
        else -> error("Unsupported media request method '$method'.")
    }

    private fun formBody(value: JsonElement, inputs: List<InputFile>): RequestBody {
        val objectValue = value as? JsonObject ?: error("Form request mappings must be a JSON object.")
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
        objectValue.forEach { (key, child) ->
            val fileTokens = fileTokens(child)
            if (fileTokens.isNotEmpty()) {
                fileTokens.forEach { index ->
                    val input = inputs.getOrNull(index) ?: error("Input file $index is not available.")
                    form.addFormDataPart(key, input.file.name, input.file.asRequestBody(input.template.mimeType.toMediaType()))
                }
            } else {
                form.addFormDataPart(key, formValue(child))
            }
        }
        return form.build()
    }

    private fun formValue(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    private fun fileTokens(value: JsonElement): List<Int> = when (value) {
        is JsonPrimitive -> fileTokenIndex(value.content)?.let(::listOf).orEmpty()
        is JsonArray -> value.flatMap(::fileTokens)
        is JsonObject -> value.values.flatMap(::fileTokens)
    }

    private fun containsFileToken(value: JsonElement): Boolean = fileTokens(value).isNotEmpty() || when (value) {
        is JsonObject -> value.values.any(::containsFileToken)
        is JsonArray -> value.any(::containsFileToken)
        else -> false
    }

    private fun fileTokenIndex(value: String): Int? = value.removePrefix(MEDIA_FILE_TOKEN_PREFIX)
        .takeIf { it != value }?.toIntOrNull()

    private fun observe(step: MediaRequestStep, response: JsonElement): Observation {
        val path = step.observation.statusPath.trim()
        if (path.isBlank()) return Observation.READY
        val values = selectMediaPath(response, path).mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }
        require(values.isNotEmpty()) { "Response observer path '$path' did not contain a string value." }
        val ready = step.observation.readyValues.map(String::lowercase)
        val pending = step.observation.pendingValues.map(String::lowercase)
        val errors = step.observation.errorValues.map(String::lowercase)
        return when {
            values.any { it in errors } -> Observation.ERROR
            values.any { it in pending } -> Observation.PENDING
            values.any { it in ready } -> Observation.READY
            else -> error("Response observer did not recognize value(s): ${values.joinToString()}")
        }
    }

    private suspend fun finish(
        chain: MediaRequestChain,
        result: StepResult,
        provider: ModelProvider
    ): JsonObject {
        val files = http.requireFiles()
        val names = when (chain.finalResponse.type) {
            MediaResponseType.IMAGE, MediaResponseType.VIDEO, MediaResponseType.OCTET_STREAM ->
                result.rawFile?.let(::listOf) ?: error("Final response is configured as media but the last response was not raw media.")
            MediaResponseType.JSON -> {
                val path = chain.finalResponse.mediaPath.trim()
                require(path.isNotBlank()) { "A JSON final response requires a configurable media path." }
                val values = selectMediaPath(result.value, path)
                require(values.size <= 10) { "A response may contain at most 10 media values." }
                values.map { value ->
                    val text = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
                    require(text.isNotBlank()) { "The configured JSON media path must contain string values." }
                    when (chain.finalResponse.valueKind) {
                        MediaValueKind.LINK -> {
                            require(text.startsWith("https://") || text.startsWith("http://")) {
                                "The configured JSON media value is not an HTTP(S) link."
                            }
                            http.download(text, if (sameOrigin(text, providerBase(provider))) provider else null)
                        }
                        MediaValueKind.BASE64 -> files.save(
                            decodeMediaBase64(text).inputStream(),
                            dataUrlMime(text)
                        )
                    }
                }
            }
        }
        require(names.isNotEmpty()) { "Media API returned no generated media." }
        return obj(
            "files" to JsonArray(names.distinct().map { str("tool-file:$it") }),
            "message" to str("Media saved and displayed to the user.")
        )
    }

    private fun resolveText(value: String, context: MediaTemplateContext): String {
        val element = resolveMediaString(value, context, MediaFileTransport.BASE64)
        require(element is JsonPrimitive && element.isString) { "Endpoint and header values must resolve to strings." }
        return element.content
    }

    private fun resolveEndpoint(provider: ModelProvider, endpoint: String): String {
        val trimmed = endpoint.trim()
        require(trimmed.isNotBlank() && !trimmed.contains("://") && !trimmed.startsWith("//") && !trimmed.contains('#')) {
            "Media request endpoints must be provider-relative paths."
        }
        require(!trimmed.split('/', '?', '#').any { it == ".." }) { "Media request endpoint traversal is not allowed." }
        return providerBase(provider).trimEnd('/') + "/" + trimmed.trimStart('/')
    }

    private fun sameOrigin(value: String, base: String): Boolean = runCatching {
        val left = java.net.URI(value)
        val right = java.net.URI(base)
        left.scheme == right.scheme && left.host == right.host && left.port == right.port
    }.getOrDefault(false)

    private fun safeResponse(value: JsonElement, provider: ModelProvider): String {
        val raw = value.toString()
        val redacted = provider.auth.token.takeIf { it.isNotBlank() }
            ?.let { raw.replace(it, "[redacted]") } ?: raw
        return redacted.filter { it >= ' ' }.take(2_000)
    }

    private fun dataUrlMime(value: String): String? = value.takeIf { it.startsWith("data:") }
        ?.substringBefore(';')?.removePrefix("data:")

    private fun detectMime(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        else -> "application/octet-stream"
    }
}
