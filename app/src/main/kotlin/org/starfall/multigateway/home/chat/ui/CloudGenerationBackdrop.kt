package org.starfall.multigateway.home.chat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material3.MaterialTheme
import kotlin.math.cos
import kotlin.math.sin

/** A slowly rotating ring of soft, translucent circles behind the assistant's live response. */
@Composable
internal fun CloudGenerationBackdrop(
    modifier: Modifier = Modifier,
    revealProgress: Float = 0f
) {
    val transition = rememberInfiniteTransition(label = "generation-clouds")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing)
        ),
        label = "cloud-ring-rotation"
    )
    val circleColor = MaterialTheme.colorScheme.primary
    val opacity = 0.72f * (1f - revealProgress.coerceIn(0f, 1f))

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f || opacity <= 0f) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = minOf(size.width * 0.30f, size.height * 0.34f).coerceAtLeast(22f)
        val dotRadius = minOf(size.width, size.height) * 0.052f

        // Each element is a single translucent circle; rotate the formation as one ring.
        rotate(rotation, pivot = center) {
            repeat(8) { index ->
                val angle = Math.toRadians(index * 45.0)
                val dotCenter = Offset(
                    x = center.x + cos(angle).toFloat() * ringRadius,
                    y = center.y + sin(angle).toFloat() * ringRadius
                )
                drawCircle(
                    color = circleColor.copy(alpha = opacity * 0.62f),
                    radius = dotRadius,
                    center = dotCenter
                )
            }
        }
    }
}
