package org.starfall.multigateway.home.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.icons.EntityIcon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The model icon rolls left to reveal the generating label, pauses, then rolls back as the label hides. */
@Composable
internal fun GenerationLoadingIndicator(
    modelName: String,
    modifier: Modifier = Modifier
) {
    val textReveal = remember { Animatable(0f) }
    val iconTravel = remember { Animatable(0f) }
    val iconRotation = remember { Animatable(0f) }
    val travelPx = with(LocalDensity.current) { 24.dp.toPx() }

    LaunchedEffect(Unit) {
        while (true) {
            // Roll left while revealing the label in sync with the icon's movement.
            coroutineScope {
                val travel = launch { iconTravel.animateTo(1f, tween(durationMillis = 700)) }
                val reveal = launch { textReveal.animateTo(1f, tween(durationMillis = 700)) }
                val rotation = launch { iconRotation.animateTo(-360f, tween(durationMillis = 700)) }
                travel.join()
                reveal.join()
                rotation.join()
            }
            kotlinx.coroutines.delay(1_000)
            // Roll back to the starting point while hiding the label from right to left.
            coroutineScope {
                val travel = launch { iconTravel.animateTo(0f, tween(durationMillis = 700)) }
                val reveal = launch { textReveal.animateTo(0f, tween(durationMillis = 700)) }
                val rotation = launch { iconRotation.animateTo(0f, tween(durationMillis = 700)) }
                travel.join()
                reveal.join()
                rotation.join()
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EntityIcon(
            image = null,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    translationX = -travelPx * iconTravel.value
                    rotationZ = iconRotation.value
                },
            text = modelName,
            matchName = modelName,
            model = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.generating),
            modifier = Modifier
                .clipToBounds()
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
