package org.starfall.generative.shared.ui.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.starfall.generative.shared.theme.modalScrimColor

/**
 * Full-screen cover that dims the content behind it while keeping it visible.
 *
 * Unlike a [androidx.compose.ui.window.Dialog], the background behind the cover
 * remains composed and is only darkened by the scrim. The cover itself fills
 * the window via a [Popup] so it can be invoked from nested layouts (chat
 * bubbles, storage rows) and still cover the entire screen.
 */
@Composable
fun ScreenCover(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = modalScrimColor(Color.Black.copy(alpha = 0.60f)),
    content: @Composable BoxScope.() -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            usePlatformDefaultWidth = false,
            clippingEnabled = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Content layer consumes clicks so the scrim underneath is not dismissed
            // when the viewer itself is tapped.
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                content = content
            )
        }
    }
}
