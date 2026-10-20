package org.starfall.generative.shared.model.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val chain: MediaRequestChain = MediaRequestChain()
)
