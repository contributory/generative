package org.starfall.generative.features.home.chat.logic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.starfall.generative.R
import org.starfall.generative.shared.media.SpeechAudioPlayer
import org.starfall.generative.shared.media.SpeechSynthesisService
import org.starfall.generative.shared.media.TtsHelper
import org.starfall.generative.shared.media.speechText
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.effectiveTtsProviderId
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.storage.preferences.AppPreferences

internal class ChatSpeechCoordinator(
    private val scope: CoroutineScope,
    private val preferences: StateFlow<AppPreferences>,
    private val providers: StateFlow<List<ModelProvider>>,
    private val services: StateFlow<List<SpeechService>>,
    private val tts: TtsHelper,
    private val synthesis: SpeechSynthesisService,
    private val player: SpeechAudioPlayer
) {
    private var job: Job? = null
    private var requestNumber = 0L
    private val activeMessage = MutableStateFlow<String?>(null)
    val speakingMessageId = activeMessage.asStateFlow()
    private val activeTestService = MutableStateFlow<String?>(null)
    val activeTestServiceId = activeTestService.asStateFlow()

    fun speakWithService(service: SpeechService, text: String, messageId: String? = null, isTest: Boolean = false) {
        val plainText = speechText(text, includeCodeBlocks = preferences.value.ttsReadCodeBlocks)
        if (plainText.isBlank()) return
        stop()
        val request = requestNumber
        activeMessage.value = messageId
        if (isTest) activeTestService.value = service.id
        val onFinished = {
            if (request == requestNumber) {
                activeMessage.value = null
                activeTestService.value = null
            }
        }
        if (service.provider.equals("system", ignoreCase = true)) {
            tts.speak(plainText, service.speed, service.pitch, service.voice, onFinished)
            return
        }
        job = scope.launch {
            try {
                val provider = providers.value.find { it.id == service.provider }
                if (provider == null) {
                    tts.reportError(R.string.speech_provider_missing)
                    onFinished()
                    return@launch
                }
                val modelId = service.modelId
                if (modelId == null) {
                    tts.reportError(R.string.speech_model_missing)
                    onFinished()
                    return@launch
                }
                if (provider.config.modelConfigs[modelId]?.modelType != ModelType.TEXT_TO_SPEECH ||
                    provider.config.modelIds?.contains(modelId) == false) {
                    tts.reportError(R.string.speech_model_unavailable)
                    onFinished()
                    return@launch
                }
                val audio = synthesis.synthesize(provider, service, plainText)
                ensureActive()
                player.play(audio, onFinished, onError = { tts.reportError(R.string.speech_playback_failed) })
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                tts.reportError(e.localizedMessage, R.string.speech_synthesis_failed)
                onFinished()
            }
        }
    }

    fun speak(text: String, messageId: String? = null) {
        if (messageId != null && activeMessage.value == messageId) { stop(); return }
        val available = services.value
        val selectedId = available.effectiveTtsProviderId(preferences.value.selectedSpeechServiceId)
        val service = selectedId?.let { id -> available.find { it.id == id } }
            ?: return
        speakWithService(service, text, messageId)
    }

    fun stop() {
        requestNumber++
        activeMessage.value = null
        activeTestService.value = null
        job?.cancel()
        job = null
        tts.stop()
        player.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
        player.shutdown()
    }
}
