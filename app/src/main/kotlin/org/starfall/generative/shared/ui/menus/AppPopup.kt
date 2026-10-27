package org.starfall.generative.shared.ui.menus

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** A shared entry point for custom-positioned popups such as covers and autocomplete. */
@Composable
fun AppPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Popup(popupPositionProvider, onDismissRequest, properties, content)
}
