package org.starfall.multigateway.home.chat.logic


import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.ConversationSummary
import org.starfall.multigateway.shared.model.ConversationSummaryProgress
import org.starfall.multigateway.shared.model.ConversationSummaryRequest
import org.starfall.multigateway.shared.model.DEFAULT_CHAT_SUMMARY_PROMPT
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.SummaryRole
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.language.DEFAULT_CONTEXT_WINDOW_TOKENS
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.storage.ConversationRepository
import org.starfall.multigateway.home.chat.logic.tool.ToolChatViewModel
import org.starfall.multigateway.home.chat.logic.chunkSummaryMessages
import org.starfall.multigateway.home.chat.logic.effectiveContext
import org.starfall.multigateway.home.chat.logic.estimateTextTokens

internal class ConversationSummaryCoordinator(
    private val repository: ConversationRepository,
    private val toolChat: ToolChatViewModel,
    private val currentConversation: () -> Conversation?,
    private val onSaved: (Conversation) -> Unit
) {
    private val progressState = MutableStateFlow<ConversationSummaryProgress?>(null)
    val progress = progressState.asStateFlow()

    suspend fun summarize(conversation: Conversation, provider: ModelProvider, config: SystemToolConfig,
        request: ConversationSummaryRequest) {
        val targetTokens = request.targetTokens.coerceAtLeast(1)
        val cutoff = conversation.messages.last().id
        val basePrompt = config.prompt.ifBlank { DEFAULT_CHAT_SUMMARY_PROMPT } +
            "\n\nTarget summary length: approximately $targetTokens tokens. Return only the summary."
        val effective = effectiveContext(conversation, conversation.messages, basePrompt)
        val modelId = config.modelId
        fun report(label: String, fraction: Float) {
            progressState.value = ConversationSummaryProgress(conversation.id, cutoff, label, fraction)
        }
        suspend fun complete(messages: List<StoredMessage>, prompt: String) =
            toolChat.completeText(provider, modelId, messages, prompt, maxOutputTokens = targetTokens).trim()
        report("Preparing summary…", 0f)
        try {
            val text = if (!request.chunked) {
                report("Summarizing conversation…", 0.35f)
                complete(effective.messages, effective.systemPrompt)
            } else {
                val chunks = chunkSummaryMessages(effective.messages, request.tokensPerChunk.coerceAtLeast(1))
                val partials = chunks.mapIndexed { index, chunk ->
                    report("Summarizing part ${index + 1}/${chunks.size}…",
                        (index + 1).toFloat() / (chunks.size + 1).coerceAtLeast(1))
                    complete(chunk, config.prompt.ifBlank { DEFAULT_CHAT_SUMMARY_PROMPT } +
                        "\n\nThis is part ${index + 1} of ${chunks.size}. Produce a compact partial summary for later merging.")
                }
                report("Merging summaries…", 0.9f)
                val helperWindow = provider.config.modelConfigs[modelId]?.contextWindowTokens?.takeIf { it > 0 } ?: DEFAULT_CONTEXT_WINDOW_TOKENS
                val mergeBudget = (helperWindow * 0.6).toInt().coerceAtLeast(16)
                var merging = partials
                while (merging.sumOf { estimateTextTokens(it) + 4 } > mergeBudget && merging.size > 1) {
                    val batches = chunkSummaryMessages(merging.mapIndexed { index, part ->
                        StoredMessage("merge_$index", ChatRole.USER, listOf(MessageVersion(content = part)))
                    }, mergeBudget)
                    val reduced = batches.map { complete(it, basePrompt) }
                    check(reduced.sumOf { estimateTextTokens(it) } < merging.sumOf { estimateTextTokens(it) }) {
                        "Summary could not be compressed to fit the summary model's context window"
                    }
                    merging = reduced
                }
                complete(merging.mapIndexed { index, part ->
                    StoredMessage("summary_part_$index", ChatRole.USER, listOf(MessageVersion(content = "Part ${index + 1}:\n$part")))
                }, basePrompt)
            }
            check(text.isNotBlank()) { "Summary returned no content" }
            val latest = repository.loadById(conversation.id)
                ?: currentConversation()?.takeIf { it.id == conversation.id } ?: conversation
            val updated = latest.copy(summary = ConversationSummary(UUID.randomUUID().toString(), text, cutoff, SummaryRole.SYSTEM),
                updatedAt = System.currentTimeMillis())
            repository.saveConversation(updated)
            onSaved(updated)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            report("Summary failed: ${e.localizedMessage ?: "Unknown error"}", 0f)
            delay(2500)
        } finally { progressState.value = null }
    }
}
