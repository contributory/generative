package org.starfall.generative.features.storage.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.starfall.generative.R
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.files.openAppStorageInDocumentsUi
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.FluentUi
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.nestedContainerBorder
import org.starfall.generative.shared.ui.layout.nestedContainerColor
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.media.MediaViewer
import org.starfall.generative.shared.ui.media.mediaMimeType
import org.starfall.generative.shared.ui.media.mediaThumbnail

private fun File.mime() = mediaMimeType(name)

private data class ToolFilesSnapshot(val files: List<File>, val totalBytes: Long)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StorageScreen(store: ToolFiles, onBack: () -> Unit) {
    val revision by ToolFiles.revision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(ToolFilesSnapshot(emptyList(), 0L)) }
    val files = snapshot.files
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var inSelectMode by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<File?>(null) }

    val allSelected = files.isNotEmpty() && selected.size == files.size

    LaunchedEffect(revision) {
        snapshot = withContext(Dispatchers.IO) {
            val current = store.list()
            ToolFilesSnapshot(current, current.sumOf { it.length() })
        }
        selected = selected.intersect(snapshot.files.mapTo(mutableSetOf()) { it.name })
        if (snapshot.files.isEmpty()) {
            inSelectMode = false
            selected = emptySet()
        }
    }

    // Back exits selection first, then the screen. Viewer cover handles its own back.
    BackHandler(enabled = viewingFile != null) { viewingFile = null }
    BackHandler(enabled = viewingFile == null && inSelectMode) {
        inSelectMode = false
        selected = emptySet()
    }
    BackHandler(enabled = viewingFile == null && !inSelectMode, onBack = onBack)

    Scaffold(
        topBar = {
            if (inSelectMode) {
                TopAppBar(
                    title = {
                        val title = if (selected.isEmpty()) {
                            stringResource(R.string.sidebar_select)
                        } else {
                            "${selected.size} ${stringResource(R.string.common_selected)}"
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            inSelectMode = false
                            selected = emptySet()
                        }) {
                            FluentIcon(
                                FluentUi.Regular.Dismiss,
                                contentDescription = stringResource(R.string.common_close)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selected = if (allSelected) emptySet()
                                else files.mapTo(mutableSetOf()) { it.name }
                            },
                            enabled = files.isNotEmpty()
                        ) {
                            FluentIcon(
                                imageVector = if (allSelected) FluentUi.Regular.DismissCircle else FluentUi.Regular.CheckmarkCircle,
                                contentDescription = stringResource(
                                    if (allSelected) R.string.storage_clear_selection else R.string.storage_select_all
                                )
                            )
                        }
                        IconButton(
                            onClick = { if (selected.isNotEmpty()) confirmDelete = true },
                            enabled = selected.isNotEmpty()
                        ) {
                            FluentIcon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = if (selected.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings_storage),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            FluentIcon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (!openAppStorageInDocumentsUi(context)) {
                                Toast.makeText(context, R.string.storage_open_in_files_failed, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            FluentIcon(
                                Icons.Outlined.OpenInBrowser,
                                contentDescription = stringResource(R.string.storage_open_in_files)
                            )
                        }
                        if (files.isNotEmpty()) {
                            IconButton(onClick = { inSelectMode = true }) {
                                FluentIcon(
                                    FluentUi.Regular.CheckmarkCircle,
                                    contentDescription = stringResource(R.string.sidebar_select)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (files.isEmpty()) {
                item(key = "storage-empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FluentIcon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            stringResource(R.string.storage_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(files, key = { it.name }) { file ->
                    val isSelected = file.name in selected
                    StorageFileRow(
                        store = store,
                        file = file,
                        inSelectMode = inSelectMode,
                        selected = isSelected,
                        onToggleSelect = { checked ->
                            selected = if (checked) selected + file.name else selected - file.name
                        },
                        onEnterSelectMode = {
                            if (!inSelectMode) {
                                inSelectMode = true
                                selected = setOf(file.name)
                            }
                        },
                        onOpenViewer = {
                            viewingFile = file
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.storage_delete_message, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val names = selected
                    scope.launch { withContext(Dispatchers.IO) { store.delete(names) } }
                    selected = emptySet()
                    confirmDelete = false
                    if (files.size == names.size) {
                        inSelectMode = false
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    viewingFile?.let { file ->
        MediaViewer(store, file) { viewingFile = null }
    }
}

@Composable
private fun StorageFileRow(
    store: ToolFiles,
    file: File,
    inSelectMode: Boolean,
    selected: Boolean,
    onToggleSelect: (Boolean) -> Unit,
    onEnterSelectMode: () -> Unit,
    onOpenViewer: () -> Unit
) {
    val context = LocalContext.current
    val mime = remember(file.name) { file.mime() }
    var thumbnail by remember(file.path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(file.path, file.lastModified()) {
        thumbnail = mediaThumbnail(context, file.path, mime, 112)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = RoundedCornerShape(16.dp),
        color = prominentContainerColor(),
        border = prominentContainerBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        if (inSelectMode) onToggleSelect(!selected) else onOpenViewer()
                    },
                    onLongClick = {
                        if (!inSelectMode) onEnterSelectMode()
                    }
                )
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = nestedContainerColor(),
                border = nestedContainerBorder()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    thumbnail?.let {
                        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } ?: FluentIcon(
                        when {
                            mime.startsWith("audio/") -> Icons.Outlined.Audiotrack
                            mime.startsWith("video/") -> Icons.Outlined.Videocam
                            mime.startsWith("image/") -> Icons.Outlined.BrokenImage
                            else -> Icons.Outlined.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    android.text.format.Formatter.formatFileSize(context, file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (inSelectMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onToggleSelect,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
