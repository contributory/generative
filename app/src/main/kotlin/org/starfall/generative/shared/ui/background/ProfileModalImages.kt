package org.starfall.generative.shared.ui.background

import androidx.compose.runtime.compositionLocalOf

/** Background photos and their transparency for modal surfaces. */
data class ProfileModalImages(
    val sheet: String? = null,
    val dialog: String? = null,
    val sheetTransparency: Float = 0.5f,
    val dialogTransparency: Float = 0.5f
)

val LocalProfileModalImages = compositionLocalOf { ProfileModalImages() }
