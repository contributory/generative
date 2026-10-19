package org.starfall.multigateway.home.chat.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.ui.icons.EntityIcon

/** What the model is doing right now; picks the label next to the rolling icon. */
internal enum class GenerationPhase(@StringRes val labelRes: Int) {
    Requesting(R.string.generation_requesting),
    Thinking(R.string.generation_thinking),
    Generating(R.string.generating),
    Executing(R.string.generation_executing);

    companion object {
        /**
         * Derived from the streaming version. Requesting = an API call is in flight and nothing
         * has come back yet: at the very start, and again after a tool finishes until the model's
         * next response begins (judged by content/reasoning added since the last tool call).
         */
        fun of(version: MessageVersion): GenerationPhase {
            if (version.toolActivity.any { it.status == "running" }) return Executing
            val lastTool = version.toolActivity.lastOrNull { !it.name.endsWith(": connect") }
            val newContent = version.content.drop(lastTool?.contentOffset ?: 0)
            val newReasoning = version.reasoningContent.orEmpty()
                .drop(if (lastTool == null) 0 else lastTool.reasoningOffset ?: Int.MAX_VALUE)
            return when {
                newContent.isNotBlank() -> Generating
                newReasoning.isNotBlank() -> Thinking
                else -> Requesting
            }
        }
    }
}

/**
 * The model icon rolls (like a real wheel) toward the screen center, and the status label is
 * uncovered exactly as fast as the icon's trailing edge moves: text appears wherever the icon has
 * already passed, and is erased again by the icon as it rolls back.
 *
 * Everything is driven by one value, [travel]; icon position, rolling angle and text reveal are
 * all derived from it, so they can never drift out of sync.
 */
@Composable
internal fun GenerationLoadingIndicator(
    modelName: String,
    modifier: Modifier = Modifier,
    phase: GenerationPhase = GenerationPhase.Generating
) {
    val density = LocalDensity.current
    val iconSize = 26.dp
    val gap = 8.dp
    val iconSizePx = with(density) { iconSize.toPx() }
    val gapPx = with(density) { gap.toPx() }
    val textStartPx = iconSizePx + gapPx // where the label sits in the row

    val style = MaterialTheme.typography.bodySmall
    val labels = GenerationPhase.entries.map { stringResource(it.labelRes) }
    val textMeasurer = rememberTextMeasurer()
    // Widest label, so the icon's far end stays put when the label changes.
    val maxLabelWidthPx = remember(labels, style) {
        labels.maxOf { textMeasurer.measure(it, style, maxLines = 1).size.width }.toFloat()
    }

    val travel = remember { Animatable(0f) } // 0 = icon at start, 1 = icon at far end
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    // Far end is the screen center, but never before the end of the longest label.
    fun travelDistancePx(): Float {
        val maxTravel = (containerWidthPx - iconSizePx).coerceAtLeast(0f)
        return (maxTravel / 2f)
            .coerceAtLeast(textStartPx + maxLabelWidthPx + gapPx)
            .coerceAtMost(maxTravel)
    }

    LaunchedEffect(Unit) {
        while (true) {
            travel.animateTo(1f, tween(durationMillis = 2_000))
            delay(1_000)
            travel.animateTo(0f, tween(durationMillis = 2_000))
            delay(500)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { containerWidthPx = it.width.toFloat() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        EntityIcon(
            image = null,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    val dx = travel.value * travelDistancePx()
                    translationX = dx
                    // True rolling: arc length = radius * angle.
                    rotationZ = Math.toDegrees((dx / (iconSizePx / 2f)).toDouble()).toFloat()
                }
                .clip(CircleShape) // after graphicsLayer so the clip follows the icon
                .zIndex(1f),
            text = modelName,
            matchName = modelName,
            model = true
        )
        Spacer(modifier = Modifier.width(gap))
        Text(
            text = stringResource(phase.labelRes),
            modifier = Modifier.drawWithContent {
                val iconLeft = travel.value * travelDistancePx()
                // Only the part the icon's trailing edge has already passed is visible.
                val visible = (iconLeft - textStartPx).coerceIn(0f, size.width)
                clipRect(right = visible) { this@drawWithContent.drawContent() }
            },
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
