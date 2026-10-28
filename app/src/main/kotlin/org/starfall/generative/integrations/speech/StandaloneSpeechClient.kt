package org.starfall.generative.integrations.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.networking.ToolHttp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// Deliberately omit debug logging: synthesis bodies and directory credentials are private.
class StandaloneSpeechClient(private val http: ToolHttp = ToolHttp(client = OkHttpClient.Builder()
    .followRedirects(false).callTimeout(90, TimeUnit.SECONDS).build())) {
    suspend fun synthesize(service: SpeechService, text: String): ByteArray = withContext(Dispatchers.IO) {
        require(text.isNotBlank() && text.length <= 10_000) { "Speech text must contain 1 to 10000 characters." }
        http.withResponse(adapter(service.engine).synthesis(service, text)) { response ->
            http.requireSuccess(response)
            val mime = response.header("Content-Type").orEmpty().substringBefore(';').lowercase()
            require(mime.startsWith("audio/") || mime == "application/octet-stream") { "Speech API did not return audio." }
            val bytes = response.body.byteStream().use { source ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 32 * 1024 * 1024) { "Speech audio exceeds 32 MB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.isNotEmpty() && bytes.size <= 32 * 1024 * 1024) { "Speech audio is empty or exceeds 32 MB." }
            require(org.starfall.generative.shared.media.audioMime(bytes) != null) { "Speech API returned unsupported audio." }
            bytes
        }
    }
    suspend fun models(service: SpeechService): List<SpeechSynthesisModel> = SpeechModelCatalog(http).load(service)

    suspend fun voices(service: SpeechService, query: String, cursor: String? = null, owned: Boolean = false): VoicePage =
        withContext(Dispatchers.IO) {
            require(service.apiKey.isNotBlank()) { "Enter an API key to fetch voices." }
            val engine = adapter(service.engine)
            engine.page(http.json(engine.voices(service.apiKey, query, cursor, owned)), cursor)
                .let { page -> page.copy(voices = page.voices.filter { it.id.isNotBlank() }.take(100)) }
        }
}

data class SpeechVoice(val id: String, val name: String, val description: String = "", val languages: List<String> = emptyList())
data class VoicePage(val voices: List<SpeechVoice>, val nextCursor: String? = null)
