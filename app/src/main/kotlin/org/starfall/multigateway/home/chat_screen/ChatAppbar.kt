package org.starfall.multigateway.home.chat_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.SummaryRole
import org.starfall.multigateway.shared.model.generation.hasToolCalls
import org.starfall.multigateway.shared.model.language.DEFAULT_CONTEXT_WINDOW_TOKENS
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.sendsThinkingBack
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons

@Composable
fun ChatAppBar(
    modelName: String,
    providerName: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelPicker: () -> Unit = {},
    onOpenSummary: () -> Unit = {},
    contextWindowStatus: ContextWindowStatus? = null,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var contextMenuVisible by remember { mutableStateOf(false) }
    val displayedModelName = modelName.substringAfterLast('/').ifBlank { modelName }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp + statusBarHeight)
            // Background first so it extends under the status bar, with rounded bottom corners.
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .padding(top = statusBarHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenDrawer),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 4.dp,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                FluentIcon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open side navigation",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(start = 68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenModelPicker)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = displayedModelName.ifBlank { stringResource(R.string.select_model) },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (providerName.isNotBlank()) {
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                contextWindowStatus?.let { status ->
                    Box {
                        ContextWindowIndicator(
                            status = status,
                            onClick = { contextMenuVisible = true }
                        )
                        DropdownMenu(
                            expanded = contextMenuVisible,
                            onDismissRequest = { contextMenuVisible = false },
                            modifier = Modifier.width(228.dp)
                        ) {
                            ContextWindowDetails(
                                status = status,
                                onCompact = {
                                    contextMenuVisible = false
                                    onOpenSummary()
                                }
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    IconButton(onClick = onNewChat, modifier = Modifier.fillMaxSize()) {
                        FluentIcon(
                            imageVector = Icons.Outlined.Compose,
                            contentDescription = "New chat",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FluentIcon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContextWindowDetails(status: ContextWindowStatus, onCompact: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Context window", style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.context_window_compact_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onCompact)
        ) {
            ContextWindowRing(status, 96.dp)
        }
        Text(
            text = "${formatContextTokens(status.estimatedTokens)} / ${formatContextTokens(status.limit.toLong())} tokens",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
internal fun ContextWindowRing(status: ContextWindowStatus, size: androidx.compose.ui.unit.Dp) {
    val progress = (status.estimatedTokens.toFloat() / status.limit.coerceAtLeast(1)).coerceIn(0f, 1f)
    val color = when {
        status.percentage >= 90 -> MaterialTheme.colorScheme.error
        status.percentage >= 75 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeWidth = 4.dp
        )
        Text(
            text = "${status.percentage}%",
            style = if (size < 50.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
internal fun ContextWindowIndicator(
    status: ContextWindowStatus,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ContextWindowRing(status, 28.dp)
        }
    }
}

data class ContextWindowStatus(val estimatedTokens: Long, val limit: Int) {
    val shouldSuggestSummary get() = estimatedTokens * 100 >= limit.toLong() * 90
    val shouldAutoSummarize get() = estimatedTokens * 100 >= limit.toLong() * 99
    val percentage get() = (estimatedTokens * 100 / limit.coerceAtLeast(1)).coerceAtMost(100).toInt()
}
/** Conservative text estimate; the local counter does not claim tokenizer precision. */
internal fun estimateTextTokens(text: String): Long {
    var wide = 0L
    var otherBytes = 0L
    text.forEach { char ->
        if (char.code in 0x2E80..0x9FFF || char.code in 0xAC00..0xD7AF || char.isSurrogate()) wide++
        else otherBytes += when { char.code < 128 -> 1; char.code < 2048 -> 2; else -> 3 }
    }
    return wide + (otherBytes + 3) / 4
}
internal fun estimateMessageTokens(message: StoredMessage, includeReasoning: Boolean): Long =
    4 + estimateTextTokens(message.content) +
        (if (includeReasoning) estimateTextTokens(message.reasoningContent.orEmpty()) else 0) +
        message.activeVersion.toolActivity.sumOf { estimateTextTokens(it.arguments) + estimateTextTokens(it.response) } +
        message.files.size * 1024L
internal fun contextWindowStatus(conversation: Conversation, prompt: String, config: ModelConfiguration,
    prefixMessages: List<StoredMessage> = emptyList()): ContextWindowStatus {
    val effective = effectiveContext(conversation, conversation.messages, prompt)
    val allMessages = prefixMessages + effective.messages
    val includeReasoning = config.sendsThinkingBack(allMessages.hasToolCalls())
    val count = estimateTextTokens(effective.systemPrompt) +
        allMessages.sumOf { estimateMessageTokens(it, includeReasoning) }
    return ContextWindowStatus(count, config.contextWindowTokens.takeIf { it > 0 } ?: DEFAULT_CONTEXT_WINDOW_TOKENS)
}
internal fun chunkSummaryMessages(messages: List<StoredMessage>, tokenLimit: Int): List<List<StoredMessage>> {
    val limit = tokenLimit.coerceAtLeast(16)
    val chunks = mutableListOf<List<StoredMessage>>()
    var current = mutableListOf<StoredMessage>()
    var tokens = 0L
    messages.forEach { message ->
        // Split a long turn as well as splitting between turns, preserving its role.
        val parts = if (estimateMessageTokens(message, true) > limit && message.content.isNotEmpty()) {
            splitSummaryText(message.content, limit - 8).mapIndexed { index, content ->
                message.copy(id = "${message.id}_part_$index", activeVersionIndex = 0,
                    versions = listOf(message.activeVersion.copy(content = content,
                        files = if (index == 0) message.files else emptyList(),
                        toolActivity = if (index == 0) message.activeVersion.toolActivity else emptyList(),
                        reasoningContent = if (index == 0) message.reasoningContent else null)))
            }
        } else listOf(message)
        parts.forEach { part ->
            val cost = estimateMessageTokens(part, true)
            if (current.isNotEmpty() && tokens + cost > limit) {
                chunks += current
                current = mutableListOf()
                tokens = 0
            }
            current += part
            tokens += cost
        }
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}
internal data class EffectiveContext(
    val messages: List<StoredMessage>,
    val systemPrompt: String
)
internal fun effectiveContext(
    conversation: Conversation,
    source: List<StoredMessage>,
    baseSystemPrompt: String
): EffectiveContext {
    val summary = conversation.summary ?: return EffectiveContext(source, baseSystemPrompt)
    val cutoff = source.indexOfFirst { it.id == summary.throughMessageId }
    if (cutoff < 0) return EffectiveContext(source, baseSystemPrompt)
    val after = source.drop(cutoff + 1)
    return when (summary.role) {
        SummaryRole.SYSTEM -> EffectiveContext(
            after,
            listOf(baseSystemPrompt, "Conversation summary:\n${summary.content}")
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        )
        SummaryRole.ASSISTANT, SummaryRole.USER -> {
            val role = if (summary.role == SummaryRole.ASSISTANT) ChatRole.MODEL else ChatRole.USER
            val synthetic = StoredMessage(
                id = "summary_${summary.id}",
                role = role,
                versions = listOf(
                    MessageVersion(
                        content = summary.content,
                        timestamp = summary.createdAt.toString()
                    )
                )
            )
            EffectiveContext(listOf(synthetic) + after, baseSystemPrompt)
        }
    }
}
private fun splitSummaryText(text: String, tokenLimit: Int): List<String> {
    val result = mutableListOf<String>()
    var offset = 0
    while (offset < text.length) {
        var low = 1
        var high = text.length - offset
        var length = 1
        while (low <= high) {
            val middle = low + (high - low) / 2
            if (estimateTextTokens(text.substring(offset, offset + middle)) <= tokenLimit) {
                length = middle
                low = middle + 1
            } else high = middle - 1
        }
        if (offset + length < text.length && text[offset + length - 1].isHighSurrogate()) length--
        length = length.coerceAtLeast(1)
        result += text.substring(offset, offset + length)
        offset += length
    }
    return result
}

internal fun formatContextTokens(tokens: Long): String =
    String.format(Locale.US, "%,d", tokens.coerceAtLeast(0L))
