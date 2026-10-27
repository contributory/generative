package org.starfall.generative.shared.ui.layout

import org.starfall.generative.shared.ui.menus.AppPopup as Popup
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.starfall.generative.shared.theme.modalScrimColor

/**
 * A full-window cover with a tappable scrim. Content is deliberately not click-consuming:
 * interactive media and controls consume their own gestures, while uncovered areas dismiss.
 */
@Composable
fun ScreenCover(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    scrimColor: Color = modalScrimColor(Color.Black.copy(alpha = 0.60f)),
    content: @Composable BoxScope.() -> Unit
) {
    Popup(
        popupPositionProvider = FullWindowPopupPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            clippingEnabled = true
        )
    ) {
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
            Box(modifier = modifier, content = content)
        }
    }
}

/** Pins the cover to the window rather than to the message or file tile that opened it. */
internal object FullWindowPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}
