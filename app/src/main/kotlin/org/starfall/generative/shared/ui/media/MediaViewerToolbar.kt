package org.starfall.generative.shared.ui.media
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

@Composable
fun MediaViewerToolbar(
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            onSave?.let { IconButton(onClick = it) { FluentIcon(Icons.Outlined.Download, "Save media") } }
            onShare?.let { IconButton(onClick = it) { FluentIcon(Icons.Outlined.Share, "Share media") } }
            onDelete?.let { IconButton(onClick = it) { FluentIcon(Icons.Outlined.Delete, "Delete media", tint = MaterialTheme.colorScheme.error) } }
            IconButton(onClick = onClose) { FluentIcon(Icons.Outlined.Close, "Close viewer") }
        }
    }
}
