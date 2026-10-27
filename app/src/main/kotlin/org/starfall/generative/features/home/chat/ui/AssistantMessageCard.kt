package org.starfall.generative.features.home.chat.ui


import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.shared.ui.text.GatedSelectionContainer
import org.starfall.generative.shared.ui.text.SelectionOnlyPlaceholder
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.shared.ui.media.MediaFileCard
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.ToolActivity
import org.starfall.generative.features.home.chat.model.ActiveChatStreamState
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import kotlinx.coroutines.flow.StateFlow
import org.starfall.generative.shared.model.countedVersionIndices
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.generative.shared.ui.media.mediaMimeType
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.features.home.chat.ui.markdown.MarkdownRenderer
import org.starfall.generative.features.home.chat.ui.markdown.StreamingMarkdownRenderer
import org.starfall.generative.features.home.shared.ui.LocalToolSheetState
import org.starfall.generative.features.home.shared.ui.ToolSheetState
import org.starfall.generative.features.home.shared.ui.ToolDetailsSheetHost

@Composable
fun AssistantMessageCard(
    message: StoredMessage,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onRetryError: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRead: () -> Unit,
    onSwitchVersion: (Int) -> Unit,
    selectedImageAttachments: List<String> = emptyList(),
    onToggleChatImage: ((String) -> Unit)? = null,
    isReading: Boolean = false,
    fallbackModelName: String = "",
    modelIcon: String? = null,
    modifier: Modifier = Modifier,
    conversationId: String? = null,
    streamStateFlow: StateFlow<ActiveChatStreamState?>? = null
) {
    val context = LocalContext.current
    SideEffect { ChatRenderMetrics.recordMessageCardRecomposition() }
    var showMoreMenu by remember { mutableStateOf(false) }
    val storedVersion = message.activeVersion
    // The frozen stream state of a stopped response stays published after Stop. A card that mounts
    // later (the chat was left and reopened from history) never saw the live stream, so a fresh
    // incremental renderer would start already frozen and parse nothing. Only a card that streamed
    // in this composition keeps the incremental renderer; every other card renders the saved text.
    val streamedInThisComposition = remember(message.id) { booleanArrayOf(false) }
    if (isStreaming) streamedInThisComposition[0] = true
    val stoppedStreamForMessage = streamStateFlow?.value?.takeIf {
        streamedInThisComposition[0] &&
            it.conversationId == conversationId && it.messageId == message.id && it.stopSignal.isFrozen
    }
    // Keep the exact incremental Markdown tree mounted after Stop instead of switching to the
    // persisted String and parsing the whole answer again.
    val keepStreamRendererMounted = isStreaming || stoppedStreamForMessage != null
    val renderCacheIdentity = remember(conversationId, message.id) {
        conversationId?.let { MarkdownRenderCacheIdentity(it, message.id) }
    }
    // Stream state is consumed by the content subtree below. The card shell keeps its stable
    // version so the toolbar, media actions and tool sheet do not invalidate on every chunk.
    val activeVersion = storedVersion
    val generatedModelName = activeVersion.modelDisplayName.ifBlank { activeVersion.modelId.ifBlank { fallbackModelName } }
    val generationError = activeVersion.generationError.orEmpty()
    val toolbarText = message.content.ifBlank { generationError }
    val versionIndices = remember(message.versions, message.activeVersionIndex) {
        val counted = message.countedVersionIndices()
        if (message.activeVersionIndex in counted) counted
        else listOf(message.activeVersionIndex) + counted
    }
    val displayedVersionIndex = versionIndices.indexOf(message.activeVersionIndex).coerceAtLeast(0)
    val processingDurationMillis = activeVersion.processingFinishedAt?.let { finishedAt ->
        activeVersion.timestamp.toLongOrNull()?.let { startedAt -> (finishedAt - startedAt).coerceAtLeast(0L) }
    }

    val toolSheetState = remember(message.id) { ToolSheetState() }
    ToolDetailsSheetHost(toolSheetState, activeVersion.toolActivity)

    CompositionLocalProvider(LocalToolSheetState provides toolSheetState) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // One call site for streaming and finished states. Switching between two composables here
        // (e.g. when a tool's image arrives before any text) recreated the processing dropdown,
        // resetting its expanded/visibility state and making it snap closed and animate open again.
        CompletedAssistantContent(
            version = activeVersion,
            processingDurationMillis = processingDurationMillis.takeUnless { isStreaming },
            animateStreamingContent = keepStreamRendererMounted,
            selectedImageAttachments = selectedImageAttachments,
            onToggleChatImage = onToggleChatImage,
            renderCacheIdentity = renderCacheIdentity,
            streamStateFlow = streamStateFlow?.takeIf { keepStreamRendererMounted },
            streamConversationId = conversationId,
            streamMessageId = message.id
        )
        if (isStreaming) {
            GenerationLoadingIndicator(
                modelName = generatedModelName,
                modelId = activeVersion.modelId,
                modelIcon = modelIcon,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        activeVersion.generationError?.let { error ->
            GenerationErrorBlock(
                error = error,
                onRetry = onRetryError,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_error_clip_label), error))
                    Toast.makeText(context, context.getString(R.string.message_error_copied), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (activeVersion.files.isNotEmpty()) {
            AttachmentStrip(activeVersion.files, removable = false, modifier = Modifier.padding(top = 8.dp),
                selectedImageAttachments = selectedImageAttachments, onToggleChatImage = onToggleChatImage,
                inlinePreview = true)
        }

        if (!isStreaming && versionIndices.size > 1) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (displayedVersionIndex > 0) {
                            onSwitchVersion(versionIndices[displayedVersionIndex - 1])
                        }
                    },
                    enabled = displayedVersionIndex > 0,
                    modifier = Modifier.size(30.dp)
                ) {
                    FluentIcon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.message_previous_version),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "${displayedVersionIndex + 1}/${versionIndices.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        if (displayedVersionIndex < versionIndices.lastIndex) {
                            onSwitchVersion(versionIndices[displayedVersionIndex + 1])
                        }
                    },
                    enabled = displayedVersionIndex < versionIndices.lastIndex,
                    modifier = Modifier.size(30.dp)
                ) {
                    FluentIcon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.message_next_version),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // A failed response can have no normal assistant content and therefore is not treated as
        // a response version by the message actions. It still needs the same toolbar so users can
        // copy/share/delete/edit the error instead of being left with only the error card.
        if (!isStreaming && toolbarText.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MessageActionButton(onClick = onRegenerate, contentDescription = stringResource(R.string.message_regenerate)) {
                    FluentIcon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                MessageActionButton(
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                         putExtra(Intent.EXTRA_TEXT, toolbarText)
                        }
                        context.startActivity(Intent.createChooser(share, context.getString(R.string.message_share_response)))
                    },
                    contentDescription = stringResource(R.string.common_share)
                ) {
                    FluentIcon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                MessageActionButton(onClick = {
                    if (message.content.isNotBlank()) {
                        onCopy()
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_error_clip_label), toolbarText))
                        Toast.makeText(context, context.getString(R.string.message_error_copied), Toast.LENGTH_SHORT).show()
                    }
                }, contentDescription = stringResource(R.string.common_copy)) {
                    FluentIcon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                Box {
                    MessageActionButton(
                        onClick = { showMoreMenu = true },
                        contentDescription = stringResource(R.string.common_more)
                    ) {
                        FluentIcon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_edit)) },
                            leadingIcon = { FluentIcon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete)) },
                            leadingIcon = { FluentIcon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onDelete()
                            }
                        )
                    }
                }

                if (generatedModelName.isNotBlank()) {
                    Text(generatedModelName,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Spacer(modifier = Modifier.weight(1f))

                MessageActionButton(onClick = onRead, contentDescription = stringResource(if (isReading) R.string.message_stop_reading else R.string.message_read_aloud)) {
                    FluentIcon(if (isReading) Icons.Outlined.StopCircle else Icons.Outlined.VolumeUp,
                        contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = if (isReading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    }
}

@Composable
private fun GenerationErrorBlock(
    error: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    var expanded by remember(error) { mutableStateOf(false) }
    val errorFontSize = (LocalCodeRenderingPreferences.current.effectiveMessageFontSize * 0.78f).sp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluentIcon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.message_generation_error),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.weight(1f))
                if (onRetry != null) {
                    IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                        FluentIcon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.message_retry_from_error),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    FluentIcon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.message_copy_error),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                FluentIcon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.message_collapse_error else R.string.message_expand_error),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            GatedSelectionContainer {
                Text(
                    text = error,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = errorFontSize,
                    lineHeight = errorFontSize * 1.44f
                ),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private sealed interface ProcessingTimelineItem {
    val contentOffset: Int

    data class Thinking(
        override val contentOffset: Int,
        val reasoning: String
    ) : ProcessingTimelineItem

    data class Tool(val activity: ToolActivity) : ProcessingTimelineItem {
        override val contentOffset: Int get() = activity.contentOffset
    }
}

private fun buildProcessingTimeline(version: MessageVersion): List<ProcessingTimelineItem> {
    val activities = version.toolActivity.filterNot { it.name.endsWith(": connect") }
    val reasoning = version.reasoningContent.orEmpty()

    if (activities.isNotEmpty() && activities.all { it.reasoningOffset == null }) {
        return buildList {
            if (reasoning.isNotBlank()) add(ProcessingTimelineItem.Thinking(0, reasoning))
            activities.forEach { add(ProcessingTimelineItem.Tool(it)) }
        }
    }

    val timeline = mutableListOf<ProcessingTimelineItem>()
    var reasoningCursor = 0
    activities.forEach { activity ->
        val reasoningEnd = (activity.reasoningOffset ?: reasoningCursor)
            .coerceIn(reasoningCursor, reasoning.length)
        if (reasoningEnd > reasoningCursor) {
            timeline += ProcessingTimelineItem.Thinking(
                activity.contentOffset.coerceIn(0, version.content.length),
                reasoning.substring(reasoningCursor, reasoningEnd)
            )
        }
        timeline += ProcessingTimelineItem.Tool(activity)
        reasoningCursor = reasoningEnd
    }
    if (reasoningCursor < reasoning.length) {
        timeline += ProcessingTimelineItem.Thinking(
            activities.lastOrNull()?.contentOffset?.coerceIn(0, version.content.length) ?: 0,
            reasoning.substring(reasoningCursor)
        )
    }
    return timeline
}

@Composable
private fun SmoothStreamingMarkdownMessage(
    content: String,
    isStreaming: Boolean,
    cacheIdentity: MarkdownRenderCacheIdentity? = null
) {
    StreamingMarkdownRenderer(content = content, isStreaming = isStreaming, cacheIdentity = cacheIdentity)
}

/** Collects live text below the assistant-card shell so static actions/media do not recompose per commit. */
@Composable
private fun StreamingAssistantText(
    streamStateFlow: StateFlow<ActiveChatStreamState?>,
    conversationId: String?,
    messageId: String,
    startOffset: Int,
    fallbackContent: String,
    cacheIdentity: MarkdownRenderCacheIdentity?
) {
    val stream = streamStateFlow.collectAsState().value?.takeIf {
        it.conversationId == conversationId && it.messageId == messageId
    }
    if (stream == null) {
        val fallback = fallbackContent.substring(startOffset.coerceIn(0, fallbackContent.length)).trimEnd()
        if (fallback.isNotBlank()) SmoothStreamingMarkdownMessage(fallback, false, cacheIdentity)
        return
    }
    val streamTail = remember(stream.content, startOffset) {
        stream.content.suffixFrom(startOffset)
    }
    if (streamTail.length > 0L) {
        StreamingMarkdownRenderer(
            // The snapshot is the source of truth on this path; avoid copying the complete tail to a String
            // on every stream publication just to satisfy the compatibility renderer's parameter.
            content = "",
            isStreaming = true,
            // The conflated collector can drain every queued segment in order without dropping chunks.
            streamSnapshot = streamTail,
            streamGenerationId = stream.generationId,
            streamMessageId = stream.messageId,
            streamStartOffset = startOffset,
            cacheIdentity = cacheIdentity,
            streamFreezeSignal = stream.stopSignal
        )
    }
}

private data class ProcessingBlock(
    val offset: Int,
    val items: List<ProcessingDropdownItem>
)

@Composable
private fun CompletedAssistantContent(
    version: MessageVersion,
    processingDurationMillis: Long?,
    animateStreamingContent: Boolean,
    selectedImageAttachments: List<String>,
    onToggleChatImage: ((String) -> Unit)?,
    renderCacheIdentity: MarkdownRenderCacheIdentity? = null,
    streamStateFlow: StateFlow<ActiveChatStreamState?>? = null,
    streamConversationId: String? = null,
    streamMessageId: String? = null
) {
    val context = LocalContext.current
    val store = remember(context) { ToolFiles(context) }
    val blocks = remember(version.content, version.reasoningContent, version.toolActivity) { buildProcessingBlocks(version) }
    // Tool details have their own sheet. Keep the answer text canonical here; redacting matching tool
    // responses made the rendered message look truncated while copy/edit still showed the full value.
    val content = version.content
    val shownFiles = mutableSetOf<String>()

    GatedSelectionContainer {
        Column {
            if (animateStreamingContent && streamStateFlow != null) {
                StreamingAssistantText(
                    streamStateFlow = streamStateFlow,
                    conversationId = streamConversationId,
                    messageId = streamMessageId.orEmpty(),
                    startOffset = 0,
                    fallbackContent = content,
                    cacheIdentity = renderCacheIdentity
                )
            } else if (blocks.isEmpty() && content.isNotBlank()) {
                SmoothStreamingMarkdownMessage(content.trimEnd(), false, renderCacheIdentity)
            } else {
                var cursor = 0
                var nextItemNumber = 1
                blocks.forEach { block ->
                    val offset = block.offset.coerceIn(cursor, content.length)
                    // Answer text after this block means its actions are finished, so they all collapse.
                    val answerStarted = content.substring(offset).isNotBlank()
                    if (offset > cursor) {
                        val textBefore = content.substring(cursor, offset).trimEnd()
                        if (textBefore.isNotBlank()) {
                            FormattedMarkdownMessage(
                                content = textBefore,
                                cacheIdentity = renderCacheIdentity?.forBlock("before-${block.offset}")
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    val visibleProcessingItems = block.items.filterNot { item ->
                        item is ProcessingDropdownItem.Tool && item.activity.responsePreviewLimit == 0 &&
                            item.activity.name in listOf("generate_image", "generate_video") &&
                            item.activity.status == "success" && item.activity.files.any {
                                mediaMimeType(it.removePrefix("tool-file:")).startsWith(
                                    if (item.activity.name == "generate_video") "video/" else "image/"
                                )
                            }
                    }
                    val hasActionTurn = visibleProcessingItems.any { it is ProcessingDropdownItem.Tool }
                    // Identity is the block's position plus its first item. Keying on every item (or on
                    // the reasoning text) recreated the dropdown on each streamed token or new tool call.
                    val live = animateStreamingContent && block === blocks.last() && !answerStarted
                    if (hasActionTurn) SelectionOnlyPlaceholder("[ActionTurnsBlock]")
                    key(block.offset, block.items.firstOrNull()?.let { first ->
                        when (first) {
                            is ProcessingDropdownItem.Thinking -> "thinking"
                            is ProcessingDropdownItem.Tool -> "tool:${first.activity.id}"
                        }
                    }) {
                        // Thinking owns an independent selection island inside ProcessingDropdown.
                        // Tool/action visuals are excluded from the message content selection; the
                        // placeholder above is their sole copy/select representation.
                        DisableSelection {
                            ProcessingDropdown(
                                items = visibleProcessingItems,
                                durationMillis = processingDurationMillis.takeIf { blocks.size == 1 },
                                startNumber = nextItemNumber,
                                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                                isStreaming = live,
                                previewCount = if (live) 2 else 0
                            )
                        }
                    }
                    val files = block.items.filterIsInstance<ProcessingDropdownItem.Tool>()
                        .flatMap { item ->
                            val activity = item.activity
                            if (activity.name == "send_file" && activity.inlineMedia) {
                                activity.files
                            } else if (activity.inlineMedia || activity.responsePreviewLimit == 0 &&
                                activity.name in listOf("generate_image", "generate_video")) {
                                activity.files.filter { file ->
                                    val mime = mediaMimeType(file.removePrefix("tool-file:"))
                                    mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")
                                }
                            } else if (activity.responsePreviewLimit > 0) emptyList()
                            else activity.files.filterNot { it.endsWith(".json", ignoreCase = true) }
                        }
                        .map { it.removePrefix("tool-file:") }
                        .filter { shownFiles.add(it) }
                    if (files.isNotEmpty()) {
                        SelectionOnlyPlaceholder("[MediaBlock]")
                        DisableSelection {
                            val columns = when {
                                files.size == 1 -> 1
                                files.size <= 6 -> 2
                                else -> 3
                            }
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                files.chunked(columns).forEach { rowFiles ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowFiles.forEach { name ->
                                            Box(Modifier.weight(1f)) {
                                                key(name) {
                                                    MediaFileCard(store, name, imageOnly = true,
                                                        selectedForChat = store.resolve(name)?.path in selectedImageAttachments,
                                                        onToggleChatImage = onToggleChatImage,
                                                        playVideoInline = columns == 1)
                                                }
                                            }
                                        }
                                        repeat(columns - rowFiles.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                    nextItemNumber += block.items.count { it !is ProcessingDropdownItem.Tool || it.activity.name != "send_file" }
                    cursor = offset
                }

                if (animateStreamingContent && streamStateFlow != null) {
                    StreamingAssistantText(
                        streamStateFlow = streamStateFlow,
                        conversationId = streamConversationId,
                        messageId = streamMessageId.orEmpty(),
                        startOffset = cursor,
                        fallbackContent = content,
                        cacheIdentity = renderCacheIdentity?.forBlock("tail-$cursor")
                    )
                } else if (cursor < content.length) {
                    val remaining = content.substring(cursor)
                    if (remaining.isNotBlank()) SmoothStreamingMarkdownMessage(
                        remaining.trimEnd(), false, renderCacheIdentity?.forBlock("tail-$cursor")
                    )
                }
            }
        }
    }
}

private fun ProcessingTimelineItem.toDropdownItem(): ProcessingDropdownItem = when (this) {
    is ProcessingTimelineItem.Thinking -> ProcessingDropdownItem.Thinking(reasoning)
    is ProcessingTimelineItem.Tool -> ProcessingDropdownItem.Tool(activity)
}

private fun buildProcessingBlocks(version: MessageVersion): List<ProcessingBlock> {
    val timeline = buildProcessingTimeline(version)
    if (timeline.isEmpty()) return emptyList()

    val blocks = mutableListOf<ProcessingBlock>()
    var groupOffset = timeline.first().contentOffset.coerceIn(0, version.content.length)
    var previousOffset = groupOffset
    var groupItems = mutableListOf(timeline.first().toDropdownItem())

    timeline.drop(1).forEach { item ->
        val itemOffset = item.contentOffset.coerceIn(previousOffset, version.content.length)
        val hasVisibleContentBetween = itemOffset > previousOffset && version.content.substring(
            previousOffset.coerceAtMost(version.content.length),
            itemOffset.coerceAtMost(version.content.length)
        ).isNotBlank()

        if (hasVisibleContentBetween) {
            blocks += ProcessingBlock(
                offset = groupOffset,
                items = groupItems.toList()
            )
            groupOffset = itemOffset
            groupItems = mutableListOf()
        }

        groupItems += item.toDropdownItem()
        previousOffset = itemOffset
    }

    blocks += ProcessingBlock(
        offset = groupOffset,
        items = groupItems.toList()
    )
    return blocks
}

@Composable
private fun MessageActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp).semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun FormattedMarkdownMessage(
    content: String,
    cacheIdentity: MarkdownRenderCacheIdentity? = null
) {
    MarkdownRenderer(content = content, cacheIdentity = cacheIdentity)
}

/** Copy representation of one assistant message; visual action/media blocks have stable text tokens. */
internal fun assistantCopyText(message: StoredMessage): String {
    val version = message.activeVersion
    val activities = version.toolActivity
        .filterNot { it.name.endsWith(": connect") }
        .sortedBy { it.contentOffset }
    if (activities.isEmpty()) return version.content

    val content = version.content
    val output = StringBuilder(content.length)
    var cursor = 0
    activities.groupBy { it.contentOffset.coerceIn(0, content.length) }
        .toSortedMap()
        .forEach { (offset, group) ->
            if (offset > cursor) output.append(content, cursor, offset)
            val action = group.any { it.name != "send_file" }
            val media = group.any { activity ->
                activity.files.any { file ->
                    val mime = mediaMimeType(file.removePrefix("tool-file:"))
                    mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")
                }
            }
            if (action) output.append("[ActionTurnsBlock]")
            if (media) output.append("[MediaBlock]")
            cursor = offset
        }
    if (cursor < content.length) output.append(content, cursor, content.length)
    return output.toString()
}
