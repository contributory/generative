package org.starfall.multigateway.home.chat.screen

import androidx.compose.runtime.Composable
import org.starfall.multigateway.home.quickactions.sheet.QuickActionsSheet

/** Opens the quick-actions sheet from the chat input area. */
@Composable
fun QuickActionsLink(showQuickActions: Boolean, onDismiss: () -> Unit) {
    if (showQuickActions) {
        QuickActionsSheet(onDismiss = onDismiss)
    }
}
