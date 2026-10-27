package org.starfall.generative.shared.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Dashed Compose-style icon used for an unsaved, empty chat draft. */
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
        val strokeWidth = 1.5f * scale
        val dashEffect = PathEffect.dashPathEffect(
            floatArrayOf(2.7f * scale, 1.8f * scale)
        )

        drawRoundRect(
            color = tint,
            topLeft = Offset(3.5f * scale, 4f * scale),
            size = Size(15f * scale, 17f * scale),
            cornerRadius = CornerRadius(2.5f * scale),
            style = Stroke(
                width = strokeWidth,
                pathEffect = dashEffect,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Keep the pencil solid so the icon remains recognizable at small sizes.
        drawLine(
            color = tint,
            start = Offset(9.5f * scale, 14.5f * scale),
            end = Offset(20f * scale, 4f * scale),
            strokeWidth = 2f * scale,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(9.5f * scale, 14.5f * scale),
            end = Offset(10.4f * scale, 13.6f * scale),
            strokeWidth = 1.5f * scale,
            cap = StrokeCap.Round
        )
    }
}
