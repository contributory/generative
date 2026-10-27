package org.starfall.generative.shared.model

import kotlinx.serialization.Serializable
import org.starfall.generative.shared.model.provider.ModelProvider

@Serializable
data class SpeechService(
    val id: String,
    val name: String,
    val icon: String? = null,
    val provider: String = "system", // "system" or ModelProvider.id
    val modelId: String? = null,
    val voice: String = "Default",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val apiKey: String = "",
    val sortOrder: Int = Int.MAX_VALUE,
    val instructions: String = "",
    val responseFormat: String = "mp3",
    val languageCode: String = "",
    val extraBody: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap())
)

/** Returns the configured TTS service, falling back to Android System TTS and then the first service. */
fun List<SpeechService>.effectiveTtsProviderId(selectedSpeechServiceId: String?): String? =
    firstOrNull { it.id == selectedSpeechServiceId }?.id
        ?: firstOrNull { it.provider.equals("system", ignoreCase = true) }?.id
        ?: firstOrNull()?.id
