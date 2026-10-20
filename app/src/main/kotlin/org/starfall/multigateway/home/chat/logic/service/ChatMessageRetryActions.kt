package org.starfall.multigateway.home.chat.logic.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import org.starfall.multigateway.home.chat.logic.prepareRegeneration
import org.starfall.multigateway.home.chat.logic.prepareUserMessageRetry
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.storage.preferences.AppPreferences

/** Owns retry/resend/regenerate branching rules, leaving the main message action service smaller. */
internal class ChatMessageRetryActions(
    private val isGenerating: StateFlow<Boolean>,
    private val summaryJob: () -> Job?,
    private val pendingConversationWrites: () -> Int,
    private val currentConversation: MutableStateFlow<Conversation?>,
    private val appPreferences: StateFlow<AppPreferences>,
    private val selectedChatModel: (Conversation?, AppPreferences) -> Pair<ModelProvider?, String>,
    private val generatedVersion: (ModelProvider?, String, String) -> MessageVersion,
    private val mediaTargetOf: (StoredMessage?) -> ChatMessageActions.MediaTarget?,
    private val startMediaResponseAction: (
        Conversation, String, ChatMessageActions.MediaTarget, Conversation?
    ) -> Boolean,
    private val startUserMessageResponseAction: (
        Conversation, String, ModelProvider, String, AppPreferences, Conversation?, Boolean
    ) -> Boolean,
) {
    private fun startUserMessageResponse(
        conversation: Conversation,
        responseId: String,
        provider: ModelProvider,
        modelId: String,
        preferences: AppPreferences,
        rollbackConversation: Conversation? = null,
        resume: Boolean = false,
    ): Boolean = startUserMessageResponseAction(
        conversation, responseId, provider, modelId, preferences, rollbackConversation, resume
    )

    fun resendUserMessage(messageId: String): Boolean {
        if (isGenerating.value || summaryJob()?.isActive == true || pendingConversationWrites() > 0) return false
        val current = currentConversation.value ?: return false
        val prefs = appPreferences.value
        val mediaTarget = mediaTargetOf(current.messages.getOrNull(current.messages.indexOfFirst { it.id == messageId } + 1))
        val (baseProvider, model) = mediaTarget?.let { it.provider to it.modelId } ?: selectedChatModel(current, prefs)
        if (baseProvider == null || model.isBlank()) return false
        val responseId = UUID.randomUUID().toString()
        val conv = prepareUserMessageRetry(current, messageId, responseId,
            generatedVersion(baseProvider, model, System.currentTimeMillis().toString()))?.copy(
            providerId = baseProvider.id,
            modelId = model,
            profileId = null
        ) ?: return false
        val userIndex = conv.messages.indexOfFirst { it.id == messageId }
        val responseMessageId = conv.messages.getOrNull(userIndex + 1)
            ?.takeIf { it.role == ChatRole.MODEL }?.id ?: return false
        if (mediaTarget != null) return startMediaResponseAction(conv, responseMessageId, mediaTarget, null)
        return startUserMessageResponse(conv, responseMessageId, baseProvider, model, prefs)
    }

    /**
     * Retry a failed response from the point where it failed. The partial answer, reasoning and
     * tool results stay in the same message and generation continues into it, unlike resend or
     * regenerate which restart from the user message.
     */
    fun retryFromError(messageId: String) {
        if (isGenerating.value || summaryJob()?.isActive == true || pendingConversationWrites() > 0) return
        val current = currentConversation.value ?: return
        val failed = current.messages.lastOrNull()?.takeIf {
            it.id == messageId && it.role == ChatRole.MODEL && it.activeVersion.generationError != null
        } ?: return
        val prefs = appPreferences.value
        val mediaTarget = mediaTargetOf(failed)
        val (baseProvider, model) = mediaTarget?.let { it.provider to it.modelId } ?: selectedChatModel(current, prefs)
        if (baseProvider == null || model.isBlank()) return
        val version = failed.activeVersion
        val hasPartial = version.content.isNotBlank() || !version.reasoningContent.isNullOrBlank() ||
            version.toolActivity.any { !it.name.endsWith(": connect") }
        val cleared = failed.copy(versions = failed.versions.mapIndexed { index, item ->
            if (index != failed.activeVersionIndex) item else item.copy(
                generationError = null,
                generationInterrupted = false,
                processingFinishedAt = null,
                // Connection handshakes are recreated by the new request.
                toolActivity = item.toolActivity.filterNot { it.name.endsWith(": connect") }
            )
        })
        val conv = current.copy(
            messages = current.messages.dropLast(1) + cleared,
            providerId = baseProvider.id,
            modelId = model,
            profileId = null,
            updatedAt = System.currentTimeMillis()
        )
        // A media request has no partial answer to continue; it restarts from the user prompt.
        if (mediaTarget != null) {
            startMediaResponseAction(conv, messageId, mediaTarget, null)
            return
        }
        startUserMessageResponse(conv, messageId, baseProvider, model, prefs, resume = hasPartial)
    }

    fun regenerateMessage(messageId: String) {
        if (isGenerating.value || summaryJob()?.isActive == true || pendingConversationWrites() > 0) return
        val current = currentConversation.value ?: return
        val prefs = appPreferences.value
        val mediaTarget = mediaTargetOf(current.messages.firstOrNull { it.id == messageId })
        val (baseProvider, model) = mediaTarget?.let { it.provider to it.modelId } ?: selectedChatModel(current, prefs)
        if (baseProvider == null || model.isBlank()) return
        var conv = prepareRegeneration(current, messageId,
            generatedVersion(baseProvider, model, System.currentTimeMillis().toString()))?.copy(
            providerId = baseProvider.id,
            modelId = model,
            profileId = null
        ) ?: return

        val summaryBoundary = current.summary?.throughMessageId
        if (summaryBoundary != null && conv.messages.none { it.id == summaryBoundary }) {
            conv = conv.copy(summary = null)
        }
        if (mediaTarget != null) {
            startMediaResponseAction(conv, messageId, mediaTarget, current)
            return
        }
        startUserMessageResponse(
            conv,
            messageId,
            baseProvider,
            model,
            prefs,
            rollbackConversation = current
        )
    }

}
