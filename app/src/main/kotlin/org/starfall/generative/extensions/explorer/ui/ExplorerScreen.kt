package org.starfall.generative.extensions.explorer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.starfall.generative.extensions.explorer.logic.ExplorerItem
import org.starfall.generative.extensions.explorer.logic.ExplorerLabels
import org.starfall.generative.extensions.explorer.logic.ExplorerLocation
import org.starfall.generative.extensions.explorer.logic.ExplorerRepository

private enum class ExplorerAction { FOLDER, RENAME, DELETE }

/** Standalone UI framework: every filesystem operation goes through the injected interface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    repository: ExplorerRepository,
    initialLocation: ExplorerLocation,
    onClose: () -> Unit,
    onOpenFile: suspend (ExplorerItem) -> Unit,
    refreshKey: Int = 0,
    labels: ExplorerLabels = ExplorerLabels()
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var location by remember(initialLocation) { mutableStateOf(initialLocation) }
    var entries by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchWasFocused by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var grid by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<ExplorerAction?>(null) }
    var target by remember { mutableStateOf<ExplorerItem?>(null) }
    var name by remember { mutableStateOf("") }

    fun closeSearch() {
        focusManager.clearFocus()
        searchExpanded = false
        searchWasFocused = false
    }

    fun back() {
        closeSearch()
        if (location == initialLocation) onClose()
        else { location = location.parent(); query = "" }
    }
    BackHandler { if (searchExpanded) closeSearch() else back() }

    LaunchedEffect(repository, location, refresh, refreshKey) {
        loading = true
        try {
            entries = repository.list(location)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            entries = emptyList()
            snackbar.showSnackbar(error.message ?: labels.operationFailed)
        } finally { loading = false }
    }

    fun runOperation(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            try {
                block()
                refresh++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                loading = false
                snackbar.showSnackbar(error.message ?: labels.operationFailed)
            }
        }
    }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries else entries.filter { it.name.contains(query, ignoreCase = true) }
    }
    val title = when {
        location.isHome -> labels.title
        location.path.isNotEmpty() -> location.path.substringAfterLast('/')
        else -> location.label.ifBlank { labels.title }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(targetState = searchExpanded, label = "Explorer search") { searching ->
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(labels.search) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(onSearch = { closeSearch() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                                    .focusRequester(searchFocus)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) searchWasFocused = true
                                        else if (searchWasFocused) {
                                            searchExpanded = false
                                            searchWasFocused = false
                                        }
                                    }
                                    .testTag("explorer-search")
                            )
                            LaunchedEffect(Unit) { searchFocus.requestFocus() }
                        } else {
                            Column {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge)
                                if (location.path.isNotEmpty()) {
                                    Text(location.path, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                navigationIcon = { TextButton(onClick = ::back,
                    modifier = Modifier.testTag("explorer-back").semantics { contentDescription = labels.back }) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                } },
                actions = {
                    if (searchExpanded) {
                        IconButton(onClick = ::closeSearch,
                            modifier = Modifier.testTag("explorer-close-search")
                                .semantics { contentDescription = labels.closeSearch }) {
                            ExplorerSearchGlyph(close = true)
                        }
                    } else {
                        IconButton(onClick = { searchExpanded = true },
                            modifier = Modifier.testTag("explorer-open-search")
                                .semantics { contentDescription = labels.search }) {
                            ExplorerSearchGlyph(close = false, active = query.isNotBlank())
                        }
                        if (!location.isHome) {
                            IconButton(onClick = { target = null; name = ""; action = ExplorerAction.FOLDER },
                                modifier = Modifier.testTag("explorer-new-folder").semantics { contentDescription = labels.newFolder }) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        IconButton(onClick = { grid = !grid },
                            modifier = Modifier.testTag("explorer-toggle-layout")
                                .semantics { contentDescription = labels.changeLayout }) { Text(if (grid) "☷" else "▦") }
                        IconButton(onClick = { refresh++ },
                            modifier = Modifier.testTag("explorer-refresh")
                                .semantics { contentDescription = labels.refresh }) { Text("⟳") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize().testTag("explorer-list"),
                    columns = GridCells.Fixed(if (grid) 3 else 1),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.location.toString() }) { item ->
                        ExplorerFileTile(
                            item = item,
                            grid = grid,
                            labels = labels,
                            onOpen = {
                                if (item.directory) { closeSearch(); location = item.location; query = "" }
                                else {
                                    closeSearch()
                                    scope.launch {
                                        try { onOpenFile(item) }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (error: Exception) {
                                            snackbar.showSnackbar(error.message ?: labels.operationFailed)
                                        }
                                    }
                                }
                            },
                            onRename = { target = item; name = item.name; action = ExplorerAction.RENAME },
                            onDelete = { target = item; action = ExplorerAction.DELETE }
                        )
                    }
                }
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("explorer-loading"))
                else if (filtered.isEmpty()) Text(labels.empty, modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    action?.let { currentAction ->
        AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(when (currentAction) {
                ExplorerAction.FOLDER -> labels.newFolder
                ExplorerAction.RENAME -> labels.rename
                ExplorerAction.DELETE -> labels.delete
            }) },
            text = {
                if (currentAction == ExplorerAction.DELETE) Text(labels.deleteConfirm(target?.name.orEmpty()))
                else OutlinedTextField(name, { name = it }, singleLine = true,
                    modifier = Modifier.testTag("explorer-name"),
                    label = { Text(labels.name) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false))
            },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("explorer-confirm"), onClick = {
                    val item = target
                    val enteredName = name
                    val parent = location
                    action = null
                    runOperation {
                        when (currentAction) {
                            ExplorerAction.FOLDER -> repository.createFolder(parent, enteredName)
                            ExplorerAction.RENAME -> repository.rename(requireNotNull(item), enteredName)
                            ExplorerAction.DELETE -> repository.delete(requireNotNull(item))
                        }
                    }
                }) { Text(if (currentAction == ExplorerAction.DELETE) labels.delete else labels.save) }
            },
            dismissButton = { TextButton(onClick = { action = null },
                modifier = Modifier.testTag("explorer-cancel")) { Text(labels.cancel) } }
        )
    }
}

@Composable
private fun ExplorerFileTile(
    item: ExplorerItem,
    grid: Boolean,
    labels: ExplorerLabels,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val icon = if (item.directory) "📁" else "📄"
    val itemKey = "${item.location.sourceId}:${item.location.path}"
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("explorer-item:$itemKey").clickable(onClick = onOpen)
    ) {
        if (grid) Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
            if (item.location.path.isNotEmpty()) {
                TileMenu({ menu = true }, menu, { menu = false }, onRename, onDelete, labels, item.name, itemKey)
            }
        } else Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!item.directory) Text(formatSize(item.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.location.path.isNotEmpty()) {
                TileMenu({ menu = true }, menu, { menu = false }, onRename, onDelete, labels, item.name, itemKey)
            }
        }
    }
}

@Composable
private fun TileMenu(open: () -> Unit, shown: Boolean, close: () -> Unit,
    rename: () -> Unit, delete: () -> Unit, labels: ExplorerLabels, itemName: String, itemKey: String) {
    Box {
        TextButton(onClick = open, modifier = Modifier.testTag("explorer-menu:$itemKey")
            .semantics { contentDescription = labels.moreActions(itemName) }) { Text("⋮") }
        DropdownMenu(expanded = shown, onDismissRequest = close) {
            DropdownMenuItem(text = { Text(labels.rename) }, onClick = { close(); rename() })
            DropdownMenuItem(text = { Text(labels.delete) }, onClick = { close(); delete() })
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun ExplorerSearchGlyph(close: Boolean, active: Boolean = false) {
    val color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        if (close) {
            drawLine(color, Offset(size.width * .25f, size.height * .25f),
                Offset(size.width * .75f, size.height * .75f), strokeWidth = stroke)
            drawLine(color, Offset(size.width * .75f, size.height * .25f),
                Offset(size.width * .25f, size.height * .75f), strokeWidth = stroke)
        } else {
            drawCircle(color, radius = size.minDimension * .28f,
                center = Offset(size.width * .41f, size.height * .41f), style = Stroke(stroke))
            drawLine(color, Offset(size.width * .62f, size.height * .62f),
                Offset(size.width * .88f, size.height * .88f), strokeWidth = stroke)
        }
    }
}
