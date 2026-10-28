package org.starfall.generative.shared.ui.layout

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Tabs follow the same item transparency preference and AMOLED outline as other controls. */
@Composable
fun appTabRowColor(): Color = prominentContainerColor()

@Composable
fun appTabRowBorder(): Modifier =
    prominentContainerBorder()?.let { Modifier.border(it) } ?: Modifier
