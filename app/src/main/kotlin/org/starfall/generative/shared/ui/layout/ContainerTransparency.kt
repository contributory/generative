package org.starfall.generative.shared.ui.layout

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Item transparency from General Settings > Appearance, independent of profile photos. */
val LocalItemTransparency = compositionLocalOf { 0.5f }

fun Color.itemFill(transparency: Float): Color = copy(alpha = alpha * (1f - transparency.coerceIn(0f, 1f)))
