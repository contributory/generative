package org.starfall.generative.features.home.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** State shared by chat message UI and the tool-details bottom sheet. */
internal class ToolSheetState {
    var selectedId by mutableStateOf<String?>(null)
}

internal val LocalToolSheetState = staticCompositionLocalOf<ToolSheetState?> { null }
