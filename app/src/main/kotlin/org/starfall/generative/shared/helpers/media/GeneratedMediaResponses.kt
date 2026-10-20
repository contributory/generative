package org.starfall.generative.shared.helpers.media

import kotlinx.serialization.json.*
import org.starfall.generative.shared.model.media.GeneratedMediaReference

/** Extract output file references and remote URLs without performing network I/O. */
internal fun generatedMediaReferences(response: JsonElement, baseUrl: String): List<GeneratedMediaReference> {
    val references = mutableListOf<GeneratedMediaReference>()
    fun collect(value: JsonElement, key: String = "") {
        when (value) {
            is JsonObject -> value.forEach { (name, child) -> collect(child, name) }
            is JsonArray -> value.forEach { collect(it, key) }
            is JsonPrimitive -> {
                val text = value.contentOrNull.orEmpty()
                if (text.startsWith("tool-file:")) {
                    references += GeneratedMediaReference(text.removePrefix("tool-file:"), isRemoteUrl = false)
                } else if (key in listOf("url", "uri") && text.startsWith("http")) {
                    check(references.size < 10) { "Too many media files in response" }
                    val sameOrigin = runCatching {
                        java.net.URI(text).let { target ->
                            java.net.URI(baseUrl).let { origin ->
                                target.scheme == origin.scheme && target.host == origin.host && target.port == origin.port
                            }
                        }
                    }.getOrDefault(false)
                    references += GeneratedMediaReference(text, isRemoteUrl = true, useProviderAuth = sameOrigin)
                }
            }
        }
    }
    collect(response)
    return references
}

/** Provider video-operation response fields are parsed here, away from the polling client. */
internal fun videoJobId(job: JsonObject): String {
    fun string(key: String) = (job[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return string("id").ifBlank { string("task_id") }
}

internal fun isTerminalVideoJob(job: JsonObject): Boolean =
    job["status"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase() in setOf("completed", "failed", "cancelled", "canceled")

internal fun validateVideoJobId(id: String) {
    require(id.isEmpty() || id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid video job ID." }
}

internal fun videoResultUrl(job: JsonObject): String {
    fun string(value: JsonElement?) = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
    return string(job["url"]).ifBlank {
        string(job["video_url"]).ifBlank { string((job["metadata"] as? JsonObject)?.get("url")) }
    }
}

internal fun googleVideoOperationName(job: JsonObject): String {
    val name = (job["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    require(name.matches(Regex("[A-Za-z0-9_./-]+")) && !name.contains("..")) { "Invalid video operation" }
    return name
}

internal fun googleVideoOperationDone(job: JsonObject): Boolean =
    (job["done"] as? JsonPrimitive)?.booleanOrNull == true

internal fun validateGoogleVideoOperation(job: JsonObject) {
    check(job["error"] == null) { "Video generation failed" }
}
