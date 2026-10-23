package org.starfall.generative.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf
import org.starfall.generative.shared.model.PromptLibrary

data class PromptControls(
    /** The prompt library with the selection of the chat that is open. */
    val library: PromptLibrary = PromptLibrary(),
    val setPromptEnabled: (promptId: String, enabled: Boolean) -> Unit = { _, _ -> },
    val openInstructions: () -> Unit = {},
    val editPrompt: (promptId: String) -> Unit = {}
)

val LocalPromptControls = staticCompositionLocalOf { PromptControls() }
