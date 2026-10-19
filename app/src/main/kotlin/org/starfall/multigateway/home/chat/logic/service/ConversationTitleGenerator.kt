package org.starfall.multigateway.home.chat.logic.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.starfall.multigateway.home.chat.logic.ChatGeneration
import org.starfall.multigateway.home.chat.logic.tool.ToolChatViewModel
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.DEFAULT_TITLE_GENERATION_PROMPT
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.storage.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Generates titles independently of the message-send transaction. */
internal class ConversationTitleGenerator(
    private val scope: CoroutineScope,
    private val conversationRepo: ConversationRepository,
    private val toolChat: ToolChatViewModel,
    private val currentConversation: MutableStateFlow<Conversation?>,
    private val configuredTextModel: (String) -> Pair<ModelProvider, SystemToolConfig>?,
    private val existingGeneration: (String) -> ChatGeneration?,
    private val generationFor: (String) -> ChatGeneration,
) {
    fun generate(conversation: Conversation, expectedFallbackTitle: String) {
        val configured = configuredTextModel("title_generation") ?: return
        val (provider, config) = configured
        scope.launch {
            val conversationId = conversation.id
            val titleMessages = conversation.messages
                .filter { it.role == ChatRole.USER || it.role == ChatRole.MODEL }
                .map { message -> StoredMessage(
                    id = message.id,
                    role = message.role,
                    versions = listOf(MessageVersion(content = message.content))
                ) }
                .filter { it.content.isNotBlank() }
            val raw = runCatching {
                toolChat.completeText(
                    provider,
                    config.modelId,
                    titleMessages,
                    config.prompt.ifBlank { DEFAULT_TITLE_GENERATION_PROMPT }
                )
            }.getOrNull().orEmpty()
            val title = raw.lineSequence().firstOrNull().orEmpty()
                .trim().trim('"', '\'', '`').take(80)
            if (title.isBlank()) return@launch

            if (existingGeneration(conversationId)?.snapshot?.id == conversationId) {
                generationFor(conversationId).updateConversation { current ->
                    if (current.id == conversationId && current.title == expectedFallbackTitle) {
                        current.copy(title = title, updatedAt = System.currentTimeMillis())
                    } else current
                }
            } else {
                val current = currentConversation.value?.takeIf { it.id == conversationId }
                    ?: conversationRepo.loadById(conversationId)
                    ?: return@launch
                if (current.title != expectedFallbackTitle) return@launch
                val updated = current.copy(title = title, updatedAt = System.currentTimeMillis())
                conversationRepo.saveConversation(updated)
                if (currentConversation.value?.id == conversationId) currentConversation.value = updated
            }
        }
    }
}
