package org.starfall.generative.shared.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Dialog as PlatformDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.starfall.generative.R
import org.starfall.generative.shared.ui.dialogs.AppDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.windowHeightIn

/** Shared shell for attachment and stored-file viewers, including their edge-to-edge zoom layer. */
@Composable
internal fun MediaViewerDialog(
    mediaKey: Any,
    onDismiss: () -> Unit,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.(onZoomChanged: (Boolean) -> Unit) -> Unit,
    fullscreenContent: @Composable (onZoomChanged: (Boolean) -> Unit) -> Unit,
    toolbar: @Composable (onClose: () -> Unit) -> Unit
) {
    var fullscreen by remember(mediaKey) { mutableStateOf(false) }

    if (fullscreen) {
        PlatformDialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(window) {
                window?.let {
                    WindowCompat.getInsetsController(it, it.decorView)
                        .hide(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                fullscreenContent { zoomed ->
                    if (!zoomed) fullscreen = false
                }
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    FluentIcon(
                        Icons.Outlined.Close,
                        stringResource(R.string.media_exit_fullscreen),
                        tint = Color.White
                    )
                }
            }
        }
    } else {
        AppDialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    Modifier.fillMaxWidth().windowHeightIn(maxFraction = 0.85f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = header)
                    content { zoomed -> fullscreen = zoomed }
                    toolbar(onDismiss)
                }
            }
        }
    }
}
