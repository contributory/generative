@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)


package org.starfall.multigateway.ui.drawer

import org.starfall.multigateway.ui.icons.FluentIcon

import org.starfall.multigateway.ui.components.AppAlertDialog as AlertDialog
import org.starfall.multigateway.ui.components.SelectableOutlinedTextField
import org.starfall.multigateway.ui.components.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.multigateway.ui.components.windowHeightIn
import org.starfall.multigateway.ui.components.windowWidth

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
import org.starfall.multigateway.data.model.*
import org.starfall.multigateway.ui.components.EntityIcon
import org.starfall.multigateway.ui.chat.modelInitial
import org.starfall.multigateway.ui.components.AmoledBorderEdge
import androidx.compose.ui.res.stringResource
import org.starfall.multigateway.R
import org.starfall.multigateway.ui.components.amoledEdgeBorder
import org.starfall.multigateway.ui.theme.LocalAmoledMode
import java.util.UUID
import org.starfall.multigateway.ui.icons.Icons

@Composable
fun ConversationsDrawer(
    conversations: List<Conversation>,
    currentConversationId: String?,
    isOpen: Boolean,
    generatingConversationIds: Set<String> = emptySet(),
    organization: SidebarOrganization,
    onUpdateOrganization: ((SidebarOrganization) -> SidebarOrganization) -> Unit,
    onDeleteConversations: (Set<String>) -> Unit,
    defaultSystemPrompt: String,
    onSelectConversation: (Conversation) -> Unit,
    onNewChat: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onUpdateDefaultSystemPrompt: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onCloseDrawer: () -> Unit,
    promptLibrary: PromptLibrary? = null,
    onPromptLibraryChange: (PromptLibrary) -> Unit = {},
    modifier: Modifier = Modifier,
    providers: List<LlmProviderInfo> = emptyList()
) {
    val providersById = remember(providers) { providers.associateBy { it.id } }
    var search by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    // A long-pressed chat is temporarily highlighted while its context menu is open.
    // Multi-select mode starts only when the user taps another chat.
    var contextChatId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf(emptySet<String>()) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var title by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var editingFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var promptDialog by remember { mutableStateOf(false) }
    val folderIds = organization.folders.map { it.id }.toSet()
    val matches = remember(conversations, search, organization.folders, organization.chatFolders) {
        val matchingFolders = organization.folders.filter { search.isNotBlank() && it.name.contains(search, true) }.map { it.id }.toSet()
        conversations.filter { search.isBlank() || it.title.contains(search, true) || organization.chatFolders[it.id] in matchingFolders }
    }
    val matchingIds = matches.map { it.id }.toSet()
    fun stopSelecting() { selecting = false; selected = emptySet(); contextChatId = null }
    fun dismissContextMenu() { contextChatId = null; selected = emptySet() }
    fun onChatClick(id: String, chat: Conversation) {
        when {
            contextChatId != null && contextChatId == id -> stopSelecting()
            contextChatId != null -> {
                val first = contextChatId!!
                contextChatId = null
                selecting = true
                selected = setOf(first, id)
            }
            selecting -> {
                selected = if (id in selected) selected - id else selected + id
                if (selected.isEmpty()) stopSelecting()
            }
            else -> { onSelectConversation(chat); onCloseDrawer() }
        }
    }
    fun onChatLongClick(id: String) {
        selecting = false
        selected = setOf(id)
        contextChatId = id
    }
    fun moveToFolder(folderId: String?) {
        val ids = selected
        onUpdateOrganization { it.moveChats(ids, folderId) }
        grouping = false
        stopSelecting()
    }
    LaunchedEffect(conversations) {
        selected = selected.intersect(conversations.map { it.id }.toSet())
        if (contextChatId != null && conversations.none { it.id == contextChatId }) stopSelecting()
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }
    fun unfocusSearch() { focusManager.clearFocus(); keyboard?.hide() }
    // Closing the keyboard (back gesture, IME close button) also leaves the search field.
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) imeWasVisible = true
        else if (imeWasVisible) { imeWasVisible = false; if (searchFocused) unfocusSearch() }
    }
    LaunchedEffect(isOpen) { if (!isOpen) { stopSelecting(); unfocusSearch() } }
    BackHandler(enabled = selecting && isOpen) { stopSelecting() }
    val newChatButtonSize by animateDpAsState(
        targetValue = if (searchFocused) 40.dp else 52.dp,
        label = "new chat button size"
    )

    val drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
    val drawerModifier = modifier.windowWidth(0.85f).let {
        if (LocalAmoledMode.current) {
            // Only the edge facing the chat is outlined; top/bottom/start sit against the screen edge.
            it.amoledEdgeBorder(drawerShape, MaterialTheme.colorScheme.outlineVariant, 1.dp, AmoledBorderEdge.End)
        } else it
    }
    ModalDrawerSheet(
        modifier = drawerModifier,
        drawerShape = drawerShape,
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                // A tap on any non-interactive part of the drawer leaves the search field.
                .pointerInput(Unit) { detectTapGestures(onTap = { unfocusSearch() }) }
        ) {
            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::stopSelecting) { FluentIcon(Icons.Default.Close, "Exit selection") }
                    Text("${selected.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    val allSelected = matchingIds.isNotEmpty() && selected.containsAll(matchingIds)
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + matchingIds else selected - matchingIds
                        },
                        enabled = matchingIds.isNotEmpty()
                    )
                    IconButton(enabled = selected.isNotEmpty(), onClick = { folderName = ""; grouping = true }) { FluentIcon(Icons.Outlined.CreateNewFolder, "Move selected chats to folder") }
                    IconButton(enabled = selected.isNotEmpty(), onClick = { deleting = selected }) { FluentIcon(Icons.Outlined.Delete, "Delete selected chats", tint = MaterialTheme.colorScheme.error) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val folders = organization.folders.sortedByDescending { it.pinned }
                if (folders.isNotEmpty()) item(key = "folders-label") { SidebarLabel("Folders") }
                folders.forEach { folder ->
                    val chats = matches.filter { organization.chatFolders[it.id] == folder.id }
                        .sortedWith(compareByDescending<Conversation> { it.id in organization.pinnedChatIds }.thenByDescending { it.updatedAt })
                    if (search.isBlank() || chats.isNotEmpty() || folder.name.contains(search, true)) {
                        item(key = "folder:${folder.id}") {
                            var menu by remember { mutableStateOf(false) }
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(Modifier.fillMaxWidth().combinedClickable(onClick = {
                                    onUpdateOrganization { state -> state.copy(folders = state.folders.map { if (it.id == folder.id) it.copy(expanded = !it.expanded) else it }) }
                                }).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FluentIcon(if (folder.expanded || search.isNotBlank()) Icons.Default.ExpandMore else Icons.Default.ChevronRight, "Toggle folder")
                                    FluentIcon(Icons.Outlined.Folder, null, modifier = Modifier.size(20.dp))
                                    Text(folder.name, Modifier.weight(1f).padding(8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${chats.size}", style = MaterialTheme.typography.labelSmall)
                                    if (folder.pinned) FluentIcon(Icons.Default.PushPin, "Pinned folder", Modifier.size(14.dp))
                                    Box {
                                        IconButton(onClick = { menu = true }) { FluentIcon(Icons.Default.MoreVert, "Folder options") }
                                        DropdownMenu(menu, { menu = false }) {
                                            DropdownMenuItem(text = { Text(if (folder.pinned) "Unpin" else "Pin") }, leadingIcon = { FluentIcon(Icons.Outlined.PushPin, null) }, onClick = {
                                                menu = false
                                                onUpdateOrganization { state -> state.copy(folders = state.folders.map { if (it.id == folder.id) it.copy(pinned = !it.pinned) else it }) }
                                            })
                                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; editingFolder = folder; folderName = folder.name })
                                            DropdownMenuItem(text = { Text("Remove folder") }, onClick = { menu = false; deletingFolder = folder })
                                        }
                                    }
                                }
                            }
                        }
                        if (folder.expanded || search.isNotBlank()) {
                            items(chats, key = { "chat:${it.id}" }) { chat ->
                                DrawerChatRow(chat, currentConversationId, generatingConversationIds, selecting, chat.id in selected, chat.id in organization.pinnedChatIds,
                                    modelIcon = chat.lastModelIcon(providersById),
                                    onClick = { onChatClick(chat.id, chat) },
                                    onLongClick = { onChatLongClick(chat.id) },
                                    showContextMenu = contextChatId == chat.id, onContextMenuDismiss = ::dismissContextMenu,
                                    onPin = { onUpdateOrganization { it.copy(pinnedChatIds = if (chat.id in it.pinnedChatIds) it.pinnedChatIds - chat.id else it.pinnedChatIds + chat.id) } },
                                    onRename = { renaming = chat; title = chat.title }, onDelete = { deleting = setOf(chat.id) },
                                    onMove = { selecting = true; selected = setOf(chat.id); folderName = ""; grouping = true }, modifier = Modifier.padding(start = 14.dp))
                            }
                        }
                    }
                }
                val history = matches.filter { organization.chatFolders[it.id] !in folderIds }
                    .sortedWith(compareByDescending<Conversation> { it.id in organization.pinnedChatIds }.thenByDescending { it.updatedAt })
                items(history, key = { "chat:${it.id}" }) { chat ->
                    DrawerChatRow(chat, currentConversationId, generatingConversationIds, selecting, chat.id in selected, chat.id in organization.pinnedChatIds,
                        modelIcon = chat.lastModelIcon(providersById),
                        onClick = { onChatClick(chat.id, chat) },
                        onLongClick = { onChatLongClick(chat.id) },
                        showContextMenu = contextChatId == chat.id, onContextMenuDismiss = ::dismissContextMenu,
                        onPin = { onUpdateOrganization { it.copy(pinnedChatIds = if (chat.id in it.pinnedChatIds) it.pinnedChatIds - chat.id else it.pinnedChatIds + chat.id) } },
                        onRename = { renaming = chat; title = chat.title }, onDelete = { deleting = setOf(chat.id) },
                        onMove = { selecting = true; selected = setOf(chat.id); folderName = ""; grouping = true })
                }
                if (matches.isEmpty()) item { Text(if (search.isBlank()) stringResource(R.string.no_conversations_yet) else "No matching conversations", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.outline) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectableOutlinedTextField(
                    value = search, onValueChange = { search = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_history), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { FluentIcon(Icons.Default.Search, "Search") },
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { FluentIcon(Icons.Default.Close, "Clear search") } },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        // Soft outline: muted when idle, only a gentle accent when focused.
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 220.dp)
                        .onFocusChanged { searchFocused = it.isFocused }
                )
                // Secondary actions step aside while the search field is expanded.
                AnimatedVisibility(
                    visible = !searchFocused,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { promptDialog = true }, modifier = Modifier.size(40.dp)) { FluentIcon(Icons.Outlined.DocumentSparkle, "Prompts") }
                        IconButton(onClick = { onNavigateToSettings(); onCloseDrawer() }, modifier = Modifier.size(40.dp)) { FluentIcon(Icons.Outlined.Settings, "General Settings") }
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (LocalAmoledMode.current) {
                    OutlinedIconButton(
                        onClick = { stopSelecting(); onNewChat(); onCloseDrawer() },
                        modifier = Modifier.size(newChatButtonSize),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) { FluentIcon(Icons.Outlined.Compose, "New chat") }
                } else {
                    FilledIconButton(
                        onClick = { stopSelecting(); onNewChat(); onCloseDrawer() },
                        modifier = Modifier.size(newChatButtonSize),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { FluentIcon(Icons.Outlined.Compose, "New chat") }
                }
            }
        }
    }
    if (grouping) AlertDialog(
        onDismissRequest = { grouping = false }, title = { Text("Move to folder") },
        text = {
            LazyColumn(Modifier.windowHeightIn(maxFraction = 0.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(organization.folders, key = { it.id }) { folder -> TextButton(onClick = { moveToFolder(folder.id) }, modifier = Modifier.fillMaxWidth()) { FluentIcon(Icons.Outlined.Folder, null); Spacer(Modifier.width(8.dp)); Text(folder.name) } }
                item { TextButton(onClick = { moveToFolder(null) }) { Text("Move to History") } }
                item { SelectableOutlinedTextField(folderName, { folderName = it }, label = { Text("New folder name") }, singleLine = true) }
            }
        },
        confirmButton = { TextButton(enabled = folderName.isNotBlank(), onClick = {
            val name = folderName.trim(); val ids = selected; val id = UUID.randomUUID().toString()
            onUpdateOrganization { it.copy(folders = it.folders + ChatFolder(id, name)).moveChats(ids, id) }
            grouping = false; stopSelecting()
        }) { Text("Create folder") } }, dismissButton = { TextButton(onClick = { grouping = false }) { Text("Cancel") } }
    )
    if (deleting.isNotEmpty()) AlertDialog(onDismissRequest = { deleting = emptySet() }, title = { Text("Delete chats?") },
        text = { Text("Delete ${deleting.size} conversation(s) and all their messages?") },
        confirmButton = { TextButton(onClick = { val ids = deleting; deleting = emptySet(); if (ids.size == 1) onDeleteConversation(ids.first()) else onDeleteConversations(ids); stopSelecting() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = emptySet() }) { Text("Cancel") } })
    if (renaming != null) AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename chat") },
        text = { SelectableOutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Chat title") }) },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { renaming?.let { onRenameConversation(it.id, title.trim()) }; renaming = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    if (editingFolder != null) AlertDialog(onDismissRequest = { editingFolder = null }, title = { Text("Rename folder") },
        text = { SelectableOutlinedTextField(folderName, { folderName = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = folderName.isNotBlank(), onClick = { val id = editingFolder!!.id; val name = folderName.trim(); onUpdateOrganization { state -> state.copy(folders = state.folders.map { if (it.id == id) it.copy(name = name) else it }) }; editingFolder = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editingFolder = null }) { Text("Cancel") } })
    if (deletingFolder != null) AlertDialog(onDismissRequest = { deletingFolder = null }, title = { Text("Remove folder?") }, text = { Text("Its chats will return to History.") },
        confirmButton = { TextButton(onClick = { val id = deletingFolder!!.id; onUpdateOrganization { it.removeFolder(id) }; deletingFolder = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } })
    if (promptDialog) {
        PromptLibraryDialog(promptLibrary ?: PromptLibrary.fromLegacy(defaultSystemPrompt),
            onChange = onPromptLibraryChange, onDismiss = { promptDialog = false })
    }
}

@Composable
private fun SidebarLabel(text: String) { Text(text, Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun DrawerChatRow(
    chat: Conversation, currentId: String?, generatingIds: Set<String>, selecting: Boolean, checked: Boolean, pinned: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onPin: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onMove: () -> Unit,
    showContextMenu: Boolean, onContextMenuDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    modelIcon: ConversationModelIcon? = null
) {
    val menu = showContextMenu
    val active = chat.id == currentId
    // AMOLED: the open chat is outlined with a faint border instead of a tinted fill.
    val outlineActive = LocalAmoledMode.current && active && !checked
    Surface(shape = RoundedCornerShape(12.dp),
        color = if (!outlineActive && (checked || active)) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        border = if (outlineActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked, onCheckedChange = { onClick() }, modifier = Modifier.size(36.dp))
            else if (modelIcon != null) EntityIcon(
                modelIcon.image, Modifier.size(36.dp).clip(CircleShape).combinedClickable(onClick = onLongClick, onLongClick = onLongClick),
                text = modelInitial(modelIcon.name), matchName = modelIcon.name, model = true,
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
