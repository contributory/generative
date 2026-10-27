package org.starfall.generative.integrations.speech

import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.starfall.generative.shared.model.SpeechEngine
import org.starfall.generative.shared.model.SpeechService

internal const val CARTESIA_VERSION = "2026-08-14"

internal interface SpeechEngineAdapter {
    val base: String
    fun headers(key: String): Map<String, String>
    fun synthesis(service: SpeechService, text: String): Request
    fun voices(key: String, query: String, cursor: String?, owned: Boolean): Request
    fun page(body: JsonObject, cursor: String?): VoicePage
}

internal fun adapter(engine: SpeechEngine): SpeechEngineAdapter = when (engine) {
    SpeechEngine.ELEVENLABS -> ElevenLabsSpeechClient
    SpeechEngine.FISH_AUDIO -> FishAudioSpeechClient
    SpeechEngine.CARTESIA -> CartesiaSpeechClient
    else -> error("Select a standalone speech engine.")
}

private fun SpeechEngineAdapter.get(path: String, key: String, parameters: Map<String, String?>): Request {
    val url = (base + path).toHttpUrl().newBuilder()
    parameters.forEach { (name, value) -> if (!value.isNullOrBlank()) url.addQueryParameter(name, value) }
    return Request.Builder().url(url.build()).apply { headers(key).forEach { (k, v) -> header(k, v) } }.build()
}
private fun SpeechEngineAdapter.post(path: String, service: SpeechService, body: JsonObject): Request =
    Request.Builder().url(base + path).apply { headers(service.apiKey).forEach { (k, v) -> header(k, v) } }
        .header("Accept", "audio/*").post(body.toString().toRequestBody("application/json".toMediaType())).build()

private fun advanced(service: SpeechService, allowed: Set<String>) {
    require(service.extraBody.keys.all { it in allowed }) { "Unsupported advanced speech parameter." }
    require(service.apiKey.isNotBlank()) { "Enter an API key." }
    require(!service.modelId.isNullOrBlank()) { "Enter a synthesis model ID." }
    require(service.voice.isNotBlank() && !service.voice.equals("Default", true)) { "Choose a voice or enter its ID." }
}
private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.items(key: String) = (get(key) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
private fun JsonObject.more() = (get("has_more") as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.voice(id: String, name: String, languages: List<String> = emptyList()) = SpeechVoice(
    text(id), text(name).ifBlank { text(id) }, text("description").ifBlank { text("category") }, languages
)

internal object ElevenLabsSpeechClient : SpeechEngineAdapter {
    override val base = "https://api.elevenlabs.io"
    override fun headers(key: String) = mapOf("xi-api-key" to key)
    override fun synthesis(service: SpeechService, text: String): Request {
        advanced(service, setOf("voice_settings", "language_code", "seed", "apply_text_normalization"))
        require(service.responseFormat in listOf("mp3_44100_128", "mp3_22050_32")) { "Choose a supported MP3 output format." }
        val settings = service.extraBody["voice_settings"] as? JsonObject
        settings?.forEach { (key, value) ->
            require(key in setOf("stability", "similarity_boost", "style", "use_speaker_boost", "speed")) { "Unsupported voice setting." }
            if (key != "use_speaker_boost") {
                val range = if (key == "speed") 0.7..1.2 else 0.0..1.0
                require((value as? JsonPrimitive)?.doubleOrNull?.let { it in range } == true) { "Voice setting is out of range." }
            } else require((value as? JsonPrimitive)?.booleanOrNull != null)
        }
        if ("language_code" in service.extraBody) require(service.modelId in setOf("eleven_turbo_v2_5", "eleven_flash_v2_5", "eleven_v3")) {
            "Language enforcement is not enabled for this model."
        }
        val body = buildJsonObject {
            service.extraBody.forEach { (k, v) -> put(k, v) }
            put("text", text); put("model_id", service.modelId)
        }
        val path = base.toHttpUrl().newBuilder().addPathSegment("v1").addPathSegment("text-to-speech")
            .addPathSegment(service.voice).addQueryParameter("output_format", service.responseFormat).build()
        return post("/", service, body).newBuilder().url(path).header("Accept", "audio/mpeg").build()
    }
    override fun voices(key: String, query: String, cursor: String?, owned: Boolean) =
        get("/v2/voices", key, mapOf("page_size" to "50", "search" to query, "next_page_token" to cursor))
    override fun page(body: JsonObject, cursor: String?) = VoicePage(
        body.items("voices").map { it.voice("voice_id", "name") },
        body.text("next_page_token").takeIf { body.more() && it.isNotBlank() && it != cursor }
    )
}

internal object FishAudioSpeechClient : SpeechEngineAdapter {
    override val base = "https://api.fish.audio"
    override fun headers(key: String) = mapOf("Authorization" to "Bearer $key")
    override fun synthesis(service: SpeechService, text: String): Request {
        advanced(service, setOf("temperature", "top_p", "prosody", "chunk_length", "latency", "language", "sample_rate", "mp3_bitrate", "opus_bitrate", "normalize"))
        require(service.responseFormat in listOf("mp3", "wav", "opus")) { "Choose MP3, WAV or Opus." }
        listOf("temperature", "top_p").forEach { name -> service.extraBody[name]?.let {
            require((it as? JsonPrimitive)?.doubleOrNull?.let { v -> v in 0.0..1.0 } == true) { "$name must be between 0 and 1." }
        } }
        service.extraBody["chunk_length"]?.let { require((it as? JsonPrimitive)?.intOrNull in 100..300) }
        service.extraBody["latency"]?.let { require((it as? JsonPrimitive)?.content in listOf("normal", "balanced", "low")) }
        service.extraBody["prosody"]?.let { value ->
            val prosody = value as? JsonObject ?: error("Prosody must be a JSON object.")
            require(prosody.keys.all { it in setOf("speed", "volume", "normalize_loudness") }) { "Unsupported prosody parameter." }
            prosody["speed"]?.let { require((it as? JsonPrimitive)?.doubleOrNull?.let { v -> v.isFinite() && v > 0 } == true) }
            prosody["volume"]?.let { require((it as? JsonPrimitive)?.doubleOrNull?.isFinite() == true) }
            prosody["normalize_loudness"]?.let { require((it as? JsonPrimitive)?.booleanOrNull != null) }
        }
        val rate = (service.extraBody["sample_rate"] as? JsonPrimitive)?.intOrNull
        require(rate == null || rate in when(service.responseFormat) {
            "opus" -> listOf(48000); "mp3" -> listOf(32000, 44100); else -> listOf(8000, 16000, 24000, 32000, 44100)
        }) { "Unsupported sample rate for this audio format." }
        service.extraBody["mp3_bitrate"]?.let { require((it as? JsonPrimitive)?.intOrNull in listOf(64,128,192)) }
        service.extraBody["opus_bitrate"]?.let { require((it as? JsonPrimitive)?.intOrNull in listOf(-1000,24,32,48,64)) }
        val body = buildJsonObject {
            service.extraBody.forEach { (k, v) -> put(k, v) }
            put("text", text); put("reference_id", service.voice); put("format", service.responseFormat)
        }
        return post("/v1/tts", service, body).newBuilder().header("model", service.modelId!!).build()
    }
    override fun voices(key: String, query: String, cursor: String?, owned: Boolean) =
        get("/model", key, mapOf("page_size" to "20", "page_number" to (cursor ?: "1"), "title" to query, "self" to if (owned) "true" else null))
    override fun page(body: JsonObject, cursor: String?): VoicePage {
        val number = cursor?.toIntOrNull() ?: 1
        val voices = body.items("items").map { item -> item.voice("_id", "title",
            (item["languages"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }) }
        val hasMore = (body["has_more"] as? JsonPrimitive)?.booleanOrNull
            ?: ((body["total"] as? JsonPrimitive)?.intOrNull?.let { number * 20 < it } == true)
        return VoicePage(voices, (number + 1).toString().takeIf { hasMore && voices.isNotEmpty() })
    }
}

internal object CartesiaSpeechClient : SpeechEngineAdapter {
    override val base = "https://api.cartesia.ai"
    override fun headers(key: String) = mapOf("Authorization" to "Bearer $key", "Cartesia-Version" to CARTESIA_VERSION)
    override fun synthesis(service: SpeechService, text: String): Request {
        advanced(service, setOf("language", "locale", "normalization", "generation_config"))
        require(!("language" in service.extraBody && "locale" in service.extraBody)) { "Choose either language or locale." }
        service.extraBody["generation_config"]?.let { value ->
            val config = value as? JsonObject ?: error("Generation config must be a JSON object.")
            require(config.keys.all { it in setOf("speed", "volume") }) { "Unsupported generation parameter." }
            config.values.forEach { require((it as? JsonPrimitive)?.doubleOrNull?.let { v -> v.isFinite() && v > 0 } == true) }
            require(service.modelId?.startsWith("sonic-3") == true) { "Generation config requires a compatible Sonic model." }
        }
        require(service.responseFormat == "wav") { "Choose WAV output for Cartesia." }
        val body = buildJsonObject {
            service.extraBody.forEach { (k, v) -> put(k, v) }
            put("model_id", service.modelId); put("transcript", text); put("voice", service.voice)
            put("output_format", buildJsonObject { put("container", "wav"); put("encoding", "pcm_s16le"); put("sample_rate", 44100) })
        }
        return post("/tts/bytes", service, body)
    }
    override fun voices(key: String, query: String, cursor: String?, owned: Boolean) =
        get("/voices", key, mapOf("limit" to "50", "q" to query, "starting_after" to cursor, "is_owner" to if (owned) "true" else null))
    override fun page(body: JsonObject, cursor: String?) = VoicePage(
        body.items("data").map { it.voice("id", "name", listOfNotNull(it.text("language").takeIf(String::isNotBlank))) },
        body.text("next_page").takeIf { body.more() && it.isNotBlank() && it != cursor }
    )
}
