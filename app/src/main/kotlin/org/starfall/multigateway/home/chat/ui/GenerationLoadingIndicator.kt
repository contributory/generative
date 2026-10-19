package org.starfall.multigateway.home.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.icons.EntityIcon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The model icon rolls to the screen center, pauses, then rolls back with the generating label. */
@Composable
internal fun GenerationLoadingIndicator(
    modelName: String,
    modifier: Modifier = Modifier
) {
    val iconTravel = remember { Animatable(0f) }
    val iconRotation = remember { Animatable(0f) }
    val textReveal = remember { Animatable(0f) }
    val density = LocalDensity.current
    val iconSize = 26.dp
    val gap = 8.dp
    val iconSizePx = with(density) { iconSize.toPx() }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            coroutineScope {
                val travel = launch {
                    iconTravel.animateTo(1f, tween(durationMillis = 2_000))
                }
                val rotation = launch {
                    iconRotation.animateTo(-360f, tween(durationMillis = 2_000))
                }
                val reveal = launch {
                    textReveal.animateTo(1f, tween(durationMillis = 2_000))
                }
                travel.join()
                rotation.join()
                reveal.join()
            }
            delay(1_000)
            coroutineScope {
                val travel = launch {
                    iconTravel.animateTo(0f, tween(durationMillis = 2_000))
                }
                val rotation = launch {
                    iconRotation.animateTo(0f, tween(durationMillis = 2_000))
                }
                val reveal = launch {
                    textReveal.animateTo(0f, tween(durationMillis = 2_000))
                }
                travel.join()
                rotation.join()
                reveal.join()
            }
            delay(1_000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerWidthPx = it.size.width.toFloat() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityIcon(
                    image = null,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape)
                        .graphicsLayer {
                            val maxTravel = (containerWidthPx - iconSizePx).coerceAtLeast(0f)
                            translationX = (maxTravel / 2f) * iconTravel.value
                            rotationZ = iconRotation.value
                        },
                    text = modelName,
                    matchName = modelName,
                    model = true
                )
                Spacer(modifier = Modifier.width(gap))
                Text(
                    text = stringResource(R.string.generating),
                    modifier = Modifier
                        .drawWithContent {
                            clipRect(right = size.width * textReveal.value) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
