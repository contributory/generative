package org.starfall.generative.features.storage.ui

import org.starfall.generative.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.content.Intent
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.ContentScale
import java.io.File
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.files.openAppStorageInDocumentsUi
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.dialogs.AppDialog as Dialog
import org.starfall.generative.shared.ui.media.ChatFilePreview
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.media.LocalChatAttachmentSelection
import org.starfall.generative.shared.ui.media.MediaContent
import org.starfall.generative.shared.ui.media.MediaViewerToolBar
import org.starfall.generative.shared.ui.media.MediaViewer
import org.starfall.generative.shared.ui.media.isPreviewableMedia
import org.starfall.generative.shared.ui.media.mediaMimeType
import org.starfall.generative.shared.ui.media.mediaThumbnail
import org.starfall.generative.shared.ui.layout.windowHeight
import org.starfall.generative.shared.ui.layout.windowHeightIn
import org.starfall.generative.shared.code.codeLanguage
import org.starfall.generative.shared.ui.media.MediaViewer

private fun File.mime() = mediaMimeType(name)

private const val TEXT_PREVIEW_LIMIT = 200_000
private val textFileExtensions = setOf(
    "txt", "log", "md", "markdown", "csv", "tsv", "json", "jsonl", "xml", "svg", "html", "htm", "css", "scss",
    "js", "mjs", "cjs", "jsx", "ts", "tsx", "py", "kt", "kts", "java", "c", "h", "cpp", "cc", "hpp", "cs",
    "rs", "go", "swift", "rb", "php", "sh", "bash", "zsh", "sql", "lua", "r", "yaml", "yml", "toml", "ini",
    "conf", "cfg", "properties", "gradle", "env", "tex", "rst", "diff", "patch"
)

/** Text and source files open as highlighted code instead of "preview unavailable". */
private fun File.isTextFile(): Boolean {
    val mime = mime()
    return extension.lowercase() in textFileExtensions || name.equals("Dockerfile", ignoreCase = true) ||
        mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" ||
        mime == "application/javascript" || mime == "application/x-yaml"
}

private fun File.codeLanguageName(): String = when {
    name.equals("Dockerfile", ignoreCase = true) -> "dockerfile"
    else -> when (val ext = extension.lowercase()) {
        "h" -> "c"
        "jsonl" -> "json"
        "conf", "cfg", "properties", "env" -> "ini"
        "gradle" -> "kotlin"
        else -> codeLanguage(ext)
    }
}
private data class ToolFilesSnapshot(val files: List<File>, val totalBytes: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(store: ToolFiles, onBack: () -> Unit) {
    val revision by ToolFiles.revision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(ToolFilesSnapshot(emptyList(), 0L)) }
    val files = snapshot.files
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val allSelected = files.isNotEmpty() && selected.size == files.size

    LaunchedEffect(revision) {
        snapshot = withContext(Dispatchers.IO) {
            val current = store.list()
            ToolFilesSnapshot(current, current.sumOf { it.length() })
        }
        selected = selected.intersect(snapshot.files.mapTo(mutableSetOf()) { it.name })
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "storage-summary") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tool files", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${files.size} files · ${android.text.format.Formatter.formatFileSize(context, snapshot.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (files.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    selected = if (allSelected) emptySet() else files.mapTo(mutableSetOf()) { it.name }
                                }) {
                                    Text(if (allSelected) "Clear selection" else "Select all")
                                }
                                Spacer(Modifier.weight(1f))
                                FilledTonalButton(
                                    enabled = selected.isNotEmpty(),
                                    onClick = { confirmDelete = true }
                                ) {
                                    Text("Delete (${selected.size})")
                                }
                            }
                        }
                    }
                }
            }
            if (files.isEmpty()) {
                item(key = "storage-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FluentIcon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text("No tool files stored", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(files, key = { it.name }) { file ->
                    StorageFileRow(store, file, file.name in selected) { checked ->
                        selected = if (checked) selected + file.name else selected - file.name
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} files?") },
            text = { Text("Messages will remain, but these files will no longer be available in chat.") },
            confirmButton = {
                TextButton(onClick = {
                    val names = selected
                    scope.launch { withContext(Dispatchers.IO) { store.delete(names) } }
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StorageFileRow(store: ToolFiles, file: File, selected: Boolean, onSelect: (Boolean) -> Unit) {
    val context = LocalContext.current
    val mime = remember(file.name) { file.mime() }
    var thumbnail by remember(file.path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var viewing by remember(file.path) { mutableStateOf(false) }
    LaunchedEffect(file.path, file.lastModified()) {
        thumbnail = mediaThumbnail(context, file.path, mime, 112)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().height(88.dp).clickable { viewing = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
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
            Checkbox(checked = selected, onCheckedChange = onSelect)
        }
    }
    if (viewing) MediaViewer(store, file) { viewing = false }
}
