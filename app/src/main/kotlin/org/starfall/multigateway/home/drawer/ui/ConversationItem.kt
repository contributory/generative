@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.starfall.multigateway.home.drawer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.starfall.multigateway.R
import java.util.UUID
import org.starfall.multigateway.shared.model.ConversationListItem
import org.starfall.multigateway.shared.theme.LocalAmoledMode
import org.starfall.multigateway.shared.ui.layout.AmoledBorderEdge
import org.starfall.multigateway.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.multigateway.shared.ui.icons.EntityIcon
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField
import org.starfall.multigateway.shared.ui.layout.amoledEdgeBorder
import org.starfall.multigateway.shared.ui.layout.windowHeightIn
import org.starfall.multigateway.shared.ui.layout.windowWidth
import org.starfall.multigateway.home.drawer.logic.ConversationModelIcon
import org.starfall.multigateway.shared.ui.model.modelInitial

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
    // AMOLED: the open chat is outlined with a faint border instead of a tinted fill.
    val outlineActive = LocalAmoledMode.current && active && !checked
    val itemShape = RoundedCornerShape(12.dp)
    Surface(shape = itemShape,
        color = if (!outlineActive && (checked || active)) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        border = if (outlineActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
        modifier = modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked, onCheckedChange = { onClick() }, modifier = Modifier.size(36.dp))
            else if (modelIcon != null) EntityIcon(
                modelIcon.image, Modifier.size(36.dp).clip(CircleShape).combinedClickable(onClick = onLongClick, onLongClick = onLongClick),
                text = modelInitial(modelIcon.name), matchName = modelIcon.name, fallbackMatchName = modelIcon.id, model = true,
                backgroundColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            ) else Box(Modifier.size(36.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest).combinedClickable(onClick = onLongClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) {
                Text(chat.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#", color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(chat.title.ifBlank { "New Chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(formatTimestamp(chat.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (pinned) FluentIcon(Icons.Default.PushPin, "Pinned chat", Modifier.size(14.dp))
            if (chat.id in generatingIds) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
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
private fun formatTimestamp(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60000).coerceAtLeast(0)
    return when { minutes < 1 -> "Just now"; minutes < 60 -> "${minutes}m ago"; minutes < 1440 -> "${minutes / 60}h ago"; minutes < 10080 -> "${minutes / 1440}d ago"; else -> "${minutes / 10080}w ago" }
}
