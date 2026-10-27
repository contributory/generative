package org.starfall.generative.shared.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** A soft, irregular horizontal dashed oval for opening an ephemeral chat. */
@Composable
fun TemporaryChatIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription?.let { this.contentDescription = it }
        }
    ) {
        val scale = size.minDimension / 24f
        val outline = Path().apply {
            moveTo(11.8f * scale, 5.5f * scale)
            cubicTo(16.8f * scale, 5f * scale, 21.1f * scale, 8f * scale, 21f * scale, 11.8f * scale)
            cubicTo(21.2f * scale, 15.9f * scale, 17.4f * scale, 18.4f * scale, 12.2f * scale, 18.5f * scale)
            cubicTo(7.1f * scale, 18.8f * scale, 3f * scale, 16.1f * scale, 3f * scale, 12.2f * scale)
            cubicTo(2.8f * scale, 8.3f * scale, 6.3f * scale, 5.9f * scale, 11.8f * scale, 5.5f * scale)
            close()
        }
        drawPath(
            path = outline,
            color = tint.copy(alpha = tint.alpha * 0.6f),
            style = Stroke(
                width = 1.7f * scale,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f * scale, 2.3f * scale)),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
