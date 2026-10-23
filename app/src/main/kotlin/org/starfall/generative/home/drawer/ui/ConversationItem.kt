@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.starfall.generative.home.drawer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.home.drawer.logic.ConversationModelIcon
import org.starfall.generative.shared.model.ConversationListItem
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.generative.shared.ui.model.modelInitial

@Composable
internal fun ConversationItem(
    chat: ConversationListItem, currentId: String?, generatingIds: Set<String>, selecting: Boolean, checked: Boolean, pinned: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onSelect: () -> Unit,
    onPin: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onMove: () -> Unit,
    showContextMenu: Boolean, onContextMenuDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    modelIcon: ConversationModelIcon? = null,
    backdrop: DrawerBackdrop? = null
) {
    val menu = showContextMenu
    val active = chat.id == currentId
    val isGenerating = chat.id in generatingIds
    // AMOLED: the open chat is outlined with a faint border instead of a tinted fill.
    val outlineActive = LocalAmoledMode.current && active && !checked
    val itemShape = RoundedCornerShape(12.dp)
    Surface(shape = itemShape,
        color = if (!outlineActive && (checked || active)) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        border = if (outlineActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Checkbox(checked, onCheckedChange = { onClick() }, modifier = Modifier.size(36.dp))
            } else {
                ConversationLeadingIcon(
                    chat = chat,
                    active = active,
                    isGenerating = isGenerating,
                    modelIcon = modelIcon,
                    onLongClick = onLongClick
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(chat.title.ifBlank { "New Chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(formatTimestamp(chat.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (pinned) FluentIcon(Icons.Default.PushPin, "Pinned chat", Modifier.padding(end = 10.dp).size(14.dp))
            if (!selecting) {
                DropdownMenu(expanded = menu, onDismissRequest = onContextMenuDismiss) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.sidebar_select)) }, leadingIcon = { FluentIcon(Icons.Outlined.CheckCircle, null) }, onClick = onSelect)
                    DropdownMenuItem(text = { Text(if (pinned) "Unpin" else "Pin") }, leadingIcon = { FluentIcon(Icons.Outlined.PushPin, null) }, onClick = { onContextMenuDismiss(); onPin() })
                    DropdownMenuItem(text = { Text("Move to folder") }, leadingIcon = { FluentIcon(Icons.Outlined.Folder, null) }, onClick = { onContextMenuDismiss(); onMove() })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { FluentIcon(Icons.Outlined.Edit, null) }, onClick = { onContextMenuDismiss(); onRename() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { FluentIcon(Icons.Outlined.Delete, null) }, onClick = { onContextMenuDismiss(); onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ConversationLeadingIcon(
    chat: ConversationListItem,
    active: Boolean,
    isGenerating: Boolean,
    modelIcon: ConversationModelIcon?,
    onLongClick: () -> Unit
) {
    val iconInset by animateDpAsState(
        targetValue = if (isGenerating) 2.75.dp else 0.dp,
        label = "conversationGeneratingIconInset"
    )
    val ringRotation = if (isGenerating) {
        val transition = rememberInfiniteTransition(label = "conversationGeneratingRing")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 950, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "conversationGeneratingAngle"
        )
        angle
    } else {
        0f
    }
    val ringColor = MaterialTheme.colorScheme.primary
    val ringTrackColor = ringColor.copy(alpha = 0.22f)

    Box(
        modifier = Modifier
            .size(36.dp)
            .testTag(
                if (isGenerating) "conversation_generating_icon_${chat.id}"
                else "conversation_icon_${chat.id}"
            )
            .clip(CircleShape)
            .combinedClickable(onClick = onLongClick, onLongClick = onLongClick)
            .drawWithContent {
                drawContent()
                if (isGenerating) {
                    val strokePx = 2.dp.toPx()
                    val halfStroke = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    val topLeft = Offset(halfStroke, halfStroke)
                    drawArc(
                        color = ringTrackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = ringRotation - 90f,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
            .padding(iconInset),
        contentAlignment = Alignment.Center
    ) {
        if (modelIcon != null) {
            EntityIcon(
                image = modelIcon.image,
                modifier = Modifier.fillMaxSize(),
                text = modelInitial(modelIcon.name),
                matchName = modelIcon.name,
                fallbackMatchName = modelIcon.id,
                model = true,
                backgroundColor = when {
                    active -> MaterialTheme.colorScheme.primaryContainer
                    isGenerating -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> null
                },
                shape = CircleShape
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .then(
                        if (!active && !isGenerating && LocalAmoledMode.current) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#",
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
private fun formatTimestamp(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60000).coerceAtLeast(0)
    return when { minutes < 1 -> "Just now"; minutes < 60 -> "${minutes}m ago"; minutes < 1440 -> "${minutes / 60}h ago"; minutes < 10080 -> "${minutes / 1440}d ago"; else -> "${minutes / 10080}w ago" }
}
