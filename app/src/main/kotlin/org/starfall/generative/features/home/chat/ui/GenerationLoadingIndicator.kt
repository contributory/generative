@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.starfall.generative.features.home.chat.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
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
    val logoOpacity = animateFloatAsState(
        targetValue = if (showLogo) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "generation-model-logo-opacity"
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            showLogo = true
            delay(2_000)
            showLogo = false
        }
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        // Match the Material 3 contained variant's shape and dimensions. Keep the
        // container outside the offscreen mask so only the logo is clipped to the
        // morphing indicator; the surrounding container stays visible.
        Box(
            Modifier.size(LoadingIndicatorDefaults.ContainerWidth, LoadingIndicatorDefaults.ContainerHeight)
                .background(
                    LoadingIndicatorDefaults.containedContainerColor,
                    LoadingIndicatorDefaults.containerShape
                )
                .testTag("generation-indicator-container"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.matchParentSize()
                    .testTag("generation-logo-mask")
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                contentAlignment = Alignment.Center
            ) {
                // This solid base remains visible while the logo fades in and out.
                Box(
                    Modifier.size(indicatorSize)
                        .background(indicatorColor)
                        .testTag("generation-logo-fallback")
                )
                // The image remains stationary; only the Material polygon morphs.
                EntityIcon(
                    image = modelIcon,
                    modifier = Modifier.size(indicatorSize)
                        .testTag("generation-model-logo")
                        .graphicsLayer { alpha = logoOpacity.value },
                    text = modelName,
                    matchName = modelName,
                    fallbackMatchName = modelId,
                    model = true
                )
                // Transparent container keeps DstIn limited to the changing polygon.
                // The opaque outer container above is deliberately outside this layer.
                ContainedLoadingIndicator(
                    modifier = Modifier.matchParentSize()
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
                    containerColor = Color.Transparent,
                    indicatorColor = Color.White
                )
            }
        }
    }
}
