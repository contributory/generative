package org.starfall.generative.features.storage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.ui.GenerativeLoadingIndicator
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.FluentUi
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.media.MediaViewer
import org.starfall.generative.shared.ui.media.isPreviewableMedia
import org.starfall.generative.shared.ui.media.mediaMimeType
import org.starfall.generative.shared.ui.media.mediaThumbnail
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Quick media browsing from the drawer, with the same three-column layout as profile photos. */
@Composable
fun MediaStorageSheet(store: ToolFiles, onDismiss: () -> Unit) {
    val revision by ToolFiles.revision.collectAsStateWithLifecycle()
    var media by remember(store) { mutableStateOf<List<StoredMedia>?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var viewingFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(store, revision) {
        media = withContext(Dispatchers.IO) {
            store.list().mapNotNull { file ->
                val mime = mediaMimeType(file.name)
                if (isPreviewableMedia(mime)) StoredMedia(file, mime) else null
            }
        }
        if (viewingFile != null && media.orEmpty().none { it.file == viewingFile }) viewingFile = null
    }
    val filtered = remember(media, search) {
        media.orEmpty().filter { it.file.name.contains(search.trim(), ignoreCase = true) }
    }
    AppSheet(
        onDismissRequest = onDismiss,
        fixedHeightFraction = 0.82f,
        contentBehindNavigationBar = true
    ) {
        Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp).testTag("media-storage-sheet")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.storage_short_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    FluentIcon(FluentUi.Regular.Dismiss, stringResource(R.string.common_close))
                }
            }
            SelectableOutlinedTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.sidebar_search)) },
                leadingIcon = { FluentIcon(Icons.Outlined.Search, null) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().testTag("media-storage-search")
            )
            Spacer(Modifier.height(12.dp))
            if (media == null || filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (media == null) GenerativeLoadingIndicator()
                    else Text(stringResource(if (media.orEmpty().isEmpty()) R.string.storage_media_empty
                        else R.string.storage_media_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("media-storage-grid"),
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.file.absolutePath }) { item ->
                        MediaStorageTile(item) { viewingFile = item.file }
                    }
                }
            }
        }
        // Attach the viewer to the sheet's window so it opens above the modal surface.
        viewingFile?.let { file -> MediaViewer(store, file) { viewingFile = null } }
    }
}

private data class StoredMedia(val file: File, val mime: String)

@Composable
private fun MediaStorageTile(item: StoredMedia, onOpen: () -> Unit) {
    val context = LocalContext.current
    val thumbnailSize = with(LocalDensity.current) { 128.dp.roundToPx() }
    val thumbnail by produceState<ImageBitmap?>(null, item, thumbnailSize) {
        value = mediaThumbnail(context, item.file.path, item.mime, thumbnailSize)
    }
    val icon = when {
        item.mime.startsWith("audio/") -> Icons.Outlined.Audiotrack
        item.mime.startsWith("video/") -> Icons.Outlined.Videocam
        else -> Icons.Outlined.BrokenImage
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .semantics { contentDescription = item.file.name }
            .testTag("media-storage-file_${item.file.name}")
    ) {
        Box(contentAlignment = Alignment.Center) {
            thumbnail?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: FluentIcon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            // Keep photos unobstructed like the UserProfile picker. Other media need a label.
            if (!item.mime.startsWith("image/")) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 5.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FluentIcon(icon, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(item.file.name, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
