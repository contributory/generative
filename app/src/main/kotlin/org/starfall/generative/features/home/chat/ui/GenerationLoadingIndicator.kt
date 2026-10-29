@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.starfall.generative.features.home.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import org.starfall.generative.shared.ui.LoadingIndicator
import org.starfall.generative.shared.ui.icons.EntityIcon
import kotlinx.coroutines.delay

/** Stationary model logo revealed through Material's morphing LoadingIndicator silhouette. */
@Composable
internal fun GenerationLoadingIndicator(
    modelName: String,
    modifier: Modifier = Modifier,
    modelId: String? = null,
    modelIcon: String? = null
) {
    val indicatorSize = LoadingIndicatorDefaults.IndicatorSize
    val indicatorColor = MaterialTheme.colorScheme.primary
    var showLogo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            showLogo = true
            delay(2_000)
            showLogo = false
        }
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier.size(indicatorSize)
                .testTag("generation-logo-mask")
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            contentAlignment = Alignment.Center
        ) {
            // Keep the loading shape visible while the logo is hidden.
            if (!showLogo) {
                Box(
                    Modifier.size(indicatorSize)
                        .background(indicatorColor)
                        .testTag("generation-logo-fallback")
                )
            }
            // Keep the logo composed so its image remains loaded between visibility cycles.
            EntityIcon(
                image = modelIcon,
                modifier = Modifier.size(indicatorSize)
                    .testTag("generation-model-logo")
                    .graphicsLayer { alpha = if (showLogo) 1f else 0f },
                text = modelName,
                matchName = modelName,
                fallbackMatchName = modelId,
                model = true
            )
            // Material draws its changing polygon into a DstIn layer. Only the polygon's
            // silhouette remains visible from the full-sized, unanimated logo underneath.
            LoadingIndicator(
                modifier = Modifier.size(indicatorSize)
                    .testTag("generation-loading-indicator")
                    .drawWithCache {
                        val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
                        onDrawWithContent {
                            drawContext.canvas.saveLayer(
                                androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), maskPaint
                            )
                            drawContent()
                            drawContext.canvas.restore()
                        }
                    },
                color = Color.White
            )
        }
    }
}
