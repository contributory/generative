package org.starfall.generative.shared.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.starfall.generative.shared.ui.layout.ScreenCover

/** Edge-to-edge media cover. Only the media bounds consume taps; the uncovered scrim dismisses. */
@Composable
internal fun MediaViewerCover(
    mediaKey: Any,
    onDismiss: () -> Unit,
    actions: List<MediaViewerAction>,
    content: @Composable BoxScope.(onZoomChanged: (Boolean) -> Unit) -> Unit,
    fullscreenContent: @Composable BoxScope.(onZoomChanged: (Boolean) -> Unit) -> Unit
) {
    var fullscreen by remember(mediaKey) { mutableStateOf(false) }
    val contentInteraction = remember(mediaKey) { MutableInteractionSource() }

    ScreenCover(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !fullscreen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = contentInteraction) {},
                        contentAlignment = Alignment.Center
                    ) {
                        content { zoomed -> fullscreen = zoomed }
                    }
                }
            }

            AnimatedVisibility(
                visible = fullscreen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(indication = null, interactionSource = contentInteraction) {}
                ) {
                    fullscreenContent(this) { zoomed -> fullscreen = zoomed }
                }
            }

            MediaViewerMenu(actions = actions, onDismiss = onDismiss)
        }
    }
}
