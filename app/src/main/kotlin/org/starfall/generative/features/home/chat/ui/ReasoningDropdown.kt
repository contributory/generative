package org.starfall.generative.features.home.chat.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.GenerativeLoadingIndicator
import org.starfall.generative.features.home.chat.ui.tool.ToolActivityCards
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.text.IndependentSelectionContainer

@Composable
fun ReasoningDropdown(
    reasoning: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = prominentContainerColor(),
        border = prominentContainerBorder(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluentIcon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = stringResource(R.string.thinking),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStreaming) stringResource(R.string.thinking_streaming) else stringResource(R.string.thought_process),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isStreaming) {
                    GenerativeLoadingIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                FluentIcon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            IndependentSelectionContainer {
                CollapsibleThinkingText(
                    reasoning = reasoning,
                    expanded = isExpanded,
                    isStreaming = isStreaming,
                    onToggle = { isExpanded = !isExpanded },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                )
            }
        }
    }
}

internal sealed interface ProcessingDropdownItem {
    data class Thinking(val reasoning: String) : ProcessingDropdownItem
    data class Tool(val activity: org.starfall.generative.shared.model.ToolActivity) : ProcessingDropdownItem
}

@Composable
internal fun ProcessingDropdown(
    items: List<ProcessingDropdownItem>,
    durationMillis: Long?,
    startNumber: Int,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    // Keep running tool calls in the timeline too. Filtering them out here could leave the
    // dropdown with no header at all, making the action steps impossible to inspect.
    val visibleItems = items.filterNot {
        it is ProcessingDropdownItem.Tool &&
            (it.activity.name.endsWith(": connect") || it.activity.name == "send_file")
    }
    if (visibleItems.isEmpty()) return

    val onlyThinking = visibleItems.all { it is ProcessingDropdownItem.Thinking }
    val onlyTools = visibleItems.all { it is ProcessingDropdownItem.Tool }
    val processingLabel = when {
        isStreaming && onlyThinking -> stringResource(R.string.thinking_streaming)
        isStreaming -> stringResource(R.string.generating)
        onlyThinking && durationMillis != null ->
            stringResource(R.string.reasoning_completed_in, formatProcessingDuration(durationMillis))
        onlyThinking -> stringResource(R.string.reasoning_completed)
        onlyTools && durationMillis != null ->
            stringResource(R.string.tool_executed_in, formatProcessingDuration(durationMillis))
        onlyTools -> stringResource(R.string.tool_executed)
        durationMillis != null ->
            stringResource(R.string.processed_in, formatProcessingDuration(durationMillis))
        else -> stringResource(R.string.processed)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
    ) {
        DisableSelection {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = processingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(4.dp))
                FluentIcon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // A collapsed timeline shows only running tools; expanding it shows every item.
        Box(Modifier.fillMaxWidth()) {
            // Draw one continuous connector behind every numbered action. Individual
            // short segments were laid out between rows, so row padding made them look
            // like small marks protruding from the start of each block.
            val connectorColor = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.matchParentSize()) {
                val x = 14.dp.toPx()
                val inset = 12.dp.toPx()
                if (size.height > inset * 2) {
                    drawLine(
                        color = connectorColor,
                        start = androidx.compose.ui.geometry.Offset(x, inset),
                        end = androidx.compose.ui.geometry.Offset(x, size.height - inset),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            Column {
                visibleItems.forEachIndexed { index, item ->
                    val itemKey = when (item) {
                        is ProcessingDropdownItem.Thinking -> "thinking_$index"
                        is ProcessingDropdownItem.Tool -> item.activity.id
                    }
                    key(itemKey) {
                        val runningTool = item is ProcessingDropdownItem.Tool && item.activity.status == "running"
                        val shown = expanded || runningTool
                        // Running items animate in during a live stream.
                        val visibility = remember { MutableTransitionState(!isStreaming && shown) }
                        visibility.targetState = shown
                        AnimatedVisibility(
                            visibleState = visibility,
                            enter = expandVertically() + slideInVertically { it / 2 } + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = (startNumber + index).toString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when (item) {
                                        is ProcessingDropdownItem.Thinking -> {
                                             if (isStreaming && index == visibleItems.lastIndex) {
                                                 IndependentSelectionContainer {
                                                     LiveThinkingBlock(item.reasoning, active = true, showHeader = false)
                                                 }
                                             } else {
                                                 var thinkingExpanded by remember { mutableStateOf(false) }
                                                 IndependentSelectionContainer {
                                                     CollapsibleThinkingText(
                                                         reasoning = item.reasoning,
                                                         expanded = thinkingExpanded,
                                                         onToggle = { thinkingExpanded = !thinkingExpanded },
                                                         modifier = Modifier.padding(end = 8.dp, bottom = 2.dp)
                                                     )
                                                 }
                                             }
                                         }
                                         is ProcessingDropdownItem.Tool -> {
                                             DisableSelection {
                                                 ToolActivityCards(listOf(item.activity))
                                             }
                                         }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleThinkingText(
    reasoning: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = preferences.effectiveMessageFontSize.sp
    val bodyLineHeight = bodySize * 1.375f
    val previewHeight = with(LocalDensity.current) { (bodyLineHeight * 5f).toDp() }
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(if (expanded) Modifier else Modifier.heightIn(max = previewHeight))
            .clip(RoundedCornerShape(8.dp))
            .then(if (expanded) Modifier else Modifier.verticalScroll(scrollState))
            .clickable(onClick = onToggle)
    ) {
        ThinkingMarkdown(
            reasoning = reasoning,
            isStreaming = isStreaming,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun formatProcessingDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) + 500L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes_seconds, hours, minutes, seconds)
        minutes > 0 -> stringResource(R.string.duration_minutes_seconds, minutes, seconds)
        else -> stringResource(R.string.duration_seconds, seconds)
    }
}
