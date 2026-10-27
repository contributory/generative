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

/** Fluent Compose icon geometry, rendered as a soft dashed outline for temporary chat. */
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
        // Follow the same open top-right document outline as Icons.Outlined.Compose.
        val document = Path().apply {
            moveTo(13.25f * scale, 4.75f * scale)
            lineTo(6.25f * scale, 4.75f * scale)
            cubicTo(4.87f * scale, 4.75f * scale, 3.75f * scale, 5.87f * scale, 3.75f * scale, 7.25f * scale)
            lineTo(3.75f * scale, 17.75f * scale)
            cubicTo(3.75f * scale, 19.13f * scale, 4.87f * scale, 20.25f * scale, 6.25f * scale, 20.25f * scale)
            lineTo(16.75f * scale, 20.25f * scale)
            cubicTo(18.13f * scale, 20.25f * scale, 19.25f * scale, 19.13f * scale, 19.25f * scale, 17.75f * scale)
            lineTo(19.25f * scale, 10.75f * scale)
        }
        // Match the diagonal pen and tapered tip of the regular Fluent Compose icon.
        val pen = Path().apply {
            moveTo(20.25f * scale, 3.75f * scale)
            lineTo(10.06f * scale, 13.94f * scale)
            lineTo(9f * scale, 15f * scale)
            lineTo(10.59f * scale, 14.47f * scale)
        }
        val dashedStroke = Stroke(
            width = 1.5f * scale,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f * scale, 2f * scale)),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val color = tint.copy(alpha = tint.alpha * 0.6f)
        drawPath(document, color, style = dashedStroke)
        drawPath(pen, color, style = dashedStroke)
    }
}
