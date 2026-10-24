package org.starfall.generative.shared.ui.media
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

@Composable
fun MediaViewerToolBar(
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            onSave?.let { IconButton(onClick = it) { FluentIcon(Icons.Outlined.Download, stringResource(R.string.media_save)) } }
            onShare?.let { IconButton(onClick = it) { FluentIcon(Icons.Outlined.Share, stringResource(R.string.media_share)) } }
            onDelete?.let { IconButton(onClick = it) { FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error) } }
            IconButton(onClick = onClose) { FluentIcon(Icons.Outlined.Close, stringResource(R.string.media_close_viewer)) }
        }
    }
}
