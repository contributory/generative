package org.starfall.generative.extensions.launcher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Pointer tracking never animates: only pickup and the bounded, rendering-only landing do. */
@Composable
internal fun <T : Any> BoxScope.LauncherDragShadow(
    state: LauncherDragDropState<T>,
    style: LauncherDragStyle,
    preview: @Composable (LauncherDragSession<T>) -> Unit
) {
    val active = state.session
    val settlement = state.settlement
    val drag = active ?: settlement?.drag ?: return
    var landing by remember(settlement) { mutableStateOf(drag.shadowBounds) }
    var landingScale by remember(settlement) { mutableFloatStateOf(style.pickupScale) }
    var landingAlpha by remember(settlement) { mutableFloatStateOf(1f) }
    LaunchedEffect(settlement, style) {
        val tail = settlement ?: return@LaunchedEffect
        // Commit/rollback may remount the source in a different container on the next layout.
        repeat(2) { withFrameNanos { } }
        val start = tail.drag.shadowBounds
        val first = withFrameNanos { it }
        while (state.settlement === tail) {
            val now = withFrameNanos { it }
            val fraction = ((now - first) / (style.landingDurationMillis.coerceAtLeast(1) * 1_000_000f)).coerceIn(0f, 1f)
            val t = FastOutSlowInEasing.transform(fraction)
            val destination = tail.landingBounds?.invoke() ?: state.sourceRect(drag.item.key)
                ?: if (!tail.accepted) tail.returnBounds?.invoke() else null
            // An accepted offscreen insertion must not fly back to the rejected source position.
            val target = destination ?: start
            landingAlpha = if (destination == null) 1f - t else 1f
            fun mix(a: Float, b: Float) = a + (b - a) * t
            landing = Rect(mix(start.left, target.left), mix(start.top, target.top),
                mix(start.right, target.right), mix(start.bottom, target.bottom))
            landingScale = mix(style.pickupScale, 1f)
            if (fraction >= 1f) break
        }
        state.clearSettlement(tail)
    }
    var lifted by remember(drag.identity) { mutableStateOf(false) }
    LaunchedEffect(drag.identity) { lifted = true }
    val pickup by animateFloatAsState(if (active != null && lifted) style.pickupScale else 1f,
        tween(style.pickupDurationMillis), label = "launcher-pickup")
    val rect = if (active != null) drag.shadowBounds else landing
    val origin = state.hostOriginInWindow
    val density = LocalDensity.current
    Box(
        Modifier.align(AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset((rect.left - origin.x).roundToInt(), (rect.top - origin.y).roundToInt()) }
            .requiredSize(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .graphicsLayer {
                val scale = if (active != null) pickup else landingScale
                scaleX = scale; scaleY = scale
                alpha = if (active != null) 1f else landingAlpha
                transformOrigin = TransformOrigin(
                    ((drag.startPointer.x - drag.sourceBounds.left) / drag.sourceBounds.width).coerceIn(0f, 1f),
                    ((drag.startPointer.y - drag.sourceBounds.top) / drag.sourceBounds.height).coerceIn(0f, 1f)
                )
                shadowElevation = style.elevation.toPx()
            }.zIndex(1000f)
    ) { preview(drag) }
}
