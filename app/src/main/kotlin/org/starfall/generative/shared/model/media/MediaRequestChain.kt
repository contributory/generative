package org.starfall.generative.shared.model.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class MediaRequestMode {
    @SerialName("form") FORM,
    @SerialName("json") JSON,
    @SerialName("raw") RAW
}

@Serializable
enum class MediaFileTransport {
    @SerialName("file") FILE,
    @SerialName("base64") BASE64,
    @SerialName("raw") RAW
}

@Serializable
enum class MediaResponseType {
    @SerialName("json") JSON,
    @SerialName("image") IMAGE,
    @SerialName("video") VIDEO,
    @SerialName("audio") AUDIO,
    @SerialName("octet-stream") OCTET_STREAM
}

@Serializable
enum class MediaGenerationMode {
    @SerialName("default") DEFAULT,
    @SerialName("custom") CUSTOM
}

@Serializable
enum class MediaValueKind {
    @SerialName("link") LINK,
    @SerialName("base64") BASE64
}

@Serializable
data class MediaRetryPolicy(
    val maxAttempts: Int = 1,
    val delayMillis: Long = 1_000L,
    val maxWaitMillis: Long = 60_000L
)

@Serializable
enum class MediaResponseObservationMode {
    /** The observer succeeds as soon as the configured JSON path exists. */
    @SerialName("path_exists") PATH_EXISTS,
    /** The observer reads the path's string values and retries only for pending values. */
    @SerialName("value") VALUE
}

@Serializable
data class MediaResponseObservation(
    /** Null preserves legacy profiles: a non-empty status path means the observer was enabled. */
    val enabled: Boolean? = null,
    val statusPath: String = "",
    val mode: MediaResponseObservationMode = MediaResponseObservationMode.VALUE,
    val readyValues: List<String> = listOf("completed", "complete", "succeeded", "success", "done"),
    val pendingValues: List<String> = listOf("queued", "pending", "processing", "running", "in_progress"),
    val errorValues: List<String> = listOf("failed", "failure", "error", "cancelled", "canceled"),
    val retry: MediaRetryPolicy = MediaRetryPolicy()
) {
    val isEnabled: Boolean
        get() = enabled ?: statusPath.isNotBlank()
}

@Serializable
data class MediaRequestStep(
    val endpoint: String = "/",
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val mode: MediaRequestMode = MediaRequestMode.JSON,
    val fileTransport: MediaFileTransport = MediaFileTransport.BASE64,
    /** Optional MIME type for raw request bodies. */
    val contentType: String = "",
    val body: String = "{}",
    val observation: MediaResponseObservation = MediaResponseObservation()
)

@Serializable
data class MediaFinalResponse(
    val type: MediaResponseType = MediaResponseType.JSON,
    val mediaPath: String = "",
    val valueKind: MediaValueKind = MediaValueKind.LINK,
    /** 1-based step whose response body is read; null keeps the final step as the source. */
    val sourceStep: Int? = null
)

@Serializable
data class MediaRequestChain(
    val steps: List<MediaRequestStep> = listOf(MediaRequestStep()),
    val finalResponse: MediaFinalResponse = MediaFinalResponse()
)

@Serializable
data class MediaRequestProfile(
    val id: String,
    val name: String,
    val chain: MediaRequestChain = MediaRequestChain(),
    /** Default uses the provider's built-in API; Custom executes [chain]. */
    val mode: MediaGenerationMode = MediaGenerationMode.CUSTOM,
    val options: JsonObject = JsonObject(emptyMap()),
    val disabledParams: Set<String> = emptySet(),
    /** Distinguishes an intentional "enable everything" selection from an unconfigured profile. */
    val paramsConfigured: Boolean = false,
    /** Android-keystore encrypted; backups on another installation require re-entering the key. */
    val encryptedCredential: String = "",
    /** Custom chains can run without a provider/model when their endpoints are self-contained. */
    val requiresModel: Boolean = true
)

/** Only a Custom request chain can bypass the model requirement. */
val MediaRequestProfile.modelIndependent: Boolean
    get() = mode == MediaGenerationMode.CUSTOM && !requiresModel

/** Domain-specific profile names; both use the same serialized shape for backwards compatibility. */
typealias ImageGenProfile = MediaRequestProfile
typealias VideoGenProfile = MediaRequestProfile

const val DEFAULT_IMAGE_GEN_PROFILE_ID = "imagegen-default"
const val DEFAULT_VIDEO_GEN_PROFILE_ID = "videogen-default"

fun defaultMediaProfile(video: Boolean): MediaRequestProfile = MediaRequestProfile(
    id = if (video) DEFAULT_VIDEO_GEN_PROFILE_ID else DEFAULT_IMAGE_GEN_PROFILE_ID,
    name = "Default",
    mode = MediaGenerationMode.DEFAULT
)

/** Always expose a non-removable built-in Default, including for legacy Custom-only libraries. */
fun Map<String, MediaRequestProfile>.withDefaultMediaProfile(video: Boolean): Map<String, MediaRequestProfile> {
    val builtIn = defaultMediaProfile(video)
    val existing = this[builtIn.id]
    // Keep the built-in first, so a model without an explicit selection always has a Default.
    return linkedMapOf(builtIn.id to (existing?.copy(mode = MediaGenerationMode.DEFAULT, requiresModel = true) ?: builtIn)).apply {
        putAll(this@withDefaultMediaProfile.filterKeys { it != builtIn.id })
    }
}

/** Music has a separate Custom-only library; it never receives a synthetic Default. */
enum class MediaKind(val toolName: String) {
    IMAGE("generate_image"), VIDEO("generate_video"), MUSIC("generate_music")
}

fun MediaRequestProfile.hasUnsafeMusicHeaders(): Boolean = chain.steps.any { step ->
    step.headers.any { (name, value) ->
        val sensitive = name.lowercase().let {
            it.contains("authorization") || it.contains("api-key") || it.contains("api_key") || it.contains("token") || it == "cookie"
        }
        sensitive && !Regex("(?i)(?:Bearer |Basic )?\\{\\{\\s*api_token\\s*\\}\\}").matches(value.trim())
    }
}

fun MediaRequestProfile.validMusicProfile(): Boolean = mode == MediaGenerationMode.CUSTOM &&
    (encryptedCredential.isBlank() || encryptedCredential.startsWith("keystore:v1:")) &&
    (encryptedCredential.isNotBlank() || chain.steps.none { step ->
        (step.headers.values + step.endpoint + step.body).any { Regex("\\{\\{\\s*api_token\\s*\\}\\}").containsMatchIn(it) }
    }) && name.isNotBlank() && chain.steps.isNotEmpty() && chain.steps.size <= 20 && !hasUnsafeMusicHeaders() &&
    chain.steps.all { step ->
        val endpoint = step.endpoint.replace(Regex("\\{\\{[^{}]+\\}\\}"), "template-value")
        endpoint.startsWith("https://") &&
        runCatching { java.net.URI(endpoint).let { it.host != null && it.userInfo == null && it.fragment == null } }.getOrDefault(false)
    } && chain.finalResponse.type in setOf(MediaResponseType.AUDIO, MediaResponseType.JSON) &&
    (chain.finalResponse.type != MediaResponseType.JSON || chain.finalResponse.mediaPath.isNotBlank())
