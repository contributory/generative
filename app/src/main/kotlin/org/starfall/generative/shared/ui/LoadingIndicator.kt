@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.starfall.generative.shared.ui

import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Material 3 expressive loading indicator used consistently across the app. */
@Composable
fun GenerativeLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    progress: (() -> Float)? = null
) {
    if (progress == null) {
        LoadingIndicator(modifier = modifier, color = color)
    } else {
        LoadingIndicator(progress = progress, modifier = modifier, color = color)
    }
}

/** Material 3 expressive replacement for the legacy linear progress indicator. */
@Composable
fun GenerativeLinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    progress: (() -> Float)? = null
) {
    if (progress == null) {
        LinearWavyProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
    } else {
        LinearWavyProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    }
}
