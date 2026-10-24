package org.starfall.generative.shared.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.ScreenCover
import org.starfall.generative.shared.ui.layout.windowHeightIn

/** Shared shell for attachment and stored-file viewers, now backed by a ScreenCover instead of a Dialog. */
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

    ScreenCover(onDismiss = onDismiss) {
        if (fullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Box(Modifier.fillMaxSize()) {
                    fullscreenContent { zoomed ->
                        if (!zoomed) fullscreen = false
                    }
                }
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .statusBarsPadding()
                ) {
                    FluentIcon(
                        Icons.Outlined.Close,
                        stringResource(R.string.media_exit_fullscreen),
                        tint = Color.White
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowHeightIn(maxFraction = 0.85f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            content = header
                        )
                        content { zoomed -> fullscreen = zoomed }
                        AnimatedVisibility(
                            visible = !fullscreen,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            toolbar(onDismiss)
                        }
                    }
                }
            }
        }
    }
}
