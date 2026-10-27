package org.starfall.generative.features.home.chat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.ToolActivity
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.windowHeightIn

/** Reserved square preview for an image/video tool that has not returned its media yet. */
@Composable
internal fun GeneratedMediaPlaceholder(activity: ToolActivity, modifier: Modifier = Modifier) {
    val video = activity.name == "generate_video"
    val label = stringResource(if (video) R.string.tool_generating_video else R.string.tool_generating_image)
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "media-preview-shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "media-preview-glow"
    )
    val shape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().windowHeightIn(maxFraction = 0.5f),
        contentAlignment = Alignment.CenterStart
    ) {
        // Restrict both dimensions, including on tablets and in landscape mode.
        val side = minOf(maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .size(side)
                .clip(shape)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape)
                .semantics { contentDescription = label }
                .testTag("media-generation-placeholder_${activity.id}"),
            contentAlignment = Alignment.Center
        ) {
            val base = scheme.surfaceVariant
            val glow = scheme.primary
            val secondaryGlow = scheme.tertiary
            Canvas(Modifier.fillMaxSize()) {
                drawRect(base.copy(alpha = 0.6f))
                val radius = size.minDimension * 0.82f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.3f), Color.Transparent),
                        center = Offset(size.width * (0.15f + phase * 0.7f), size.height * 0.3f),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(size.width * (0.15f + phase * 0.7f), size.height * 0.3f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow.copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(size.width * (0.85f - phase * 0.7f), size.height * 0.8f),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(size.width * (0.85f - phase * 0.7f), size.height * 0.8f)
                )
                val sweep = size.width * (phase * 2.5f - 0.75f)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                        start = Offset(sweep - size.width * 0.7f, 0f),
                        end = Offset(sweep + size.width * 0.25f, size.height)
                    )
                )
            }
            FluentIcon(
                imageVector = if (video) Icons.Outlined.Videocam else Icons.Outlined.Image,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
