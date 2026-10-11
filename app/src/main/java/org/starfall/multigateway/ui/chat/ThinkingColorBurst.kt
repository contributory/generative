package org.starfall.multigateway.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI

/** A short, interruptible bloom; idle controls never keep an animation running. */
@Stable
internal class ThinkingColorBurst(private val scope: CoroutineScope) {
    val progress = Animatable(1f)
    var origin by mutableStateOf(Offset.Zero)
        private set
    var color by mutableStateOf(Color.Transparent)
        private set
    private var job: Job? = null

    fun start(origin: Offset, color: Color, durationMillis: Int = 1100) {
        job?.cancel()
        this.origin = origin
        this.color = color
        job = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis.coerceIn(700, 7000), easing = FastOutSlowInEasing))
        }
    }
}

@Composable
internal fun rememberThinkingColorBurst(): ThinkingColorBurst {
    val scope = rememberCoroutineScope()
    return remember(scope) { ThinkingColorBurst(scope) }
}

/** Reveal the new gradient outward from the thumb before leaving its settled fill. */
internal fun DrawScope.revealThinkingColor(burst: ThinkingColorBurst, draw: DrawScope.() -> Unit) {
    val progress = burst.progress.value
    if (progress >= 1f) {
        draw()
        return
    }
    val radius = 14.dp.toPx() + hypot(size.width, size.height) * progress
    clipPath(Path().apply { addOval(Rect(burst.origin, radius)) }) { draw() }
}

internal fun DrawScope.drawThinkingColorBurst(burst: ThinkingColorBurst) {
    val progress = burst.progress.value
    if (progress >= 1f) return
    val radius = 14.dp.toPx() + hypot(size.width, size.height) * progress
    val opacity = (1f - progress) * (1f - progress)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.42f * opacity),
                burst.color.copy(alpha = 0.55f * opacity), Color.Transparent),
            center = burst.origin,
            radius = radius
        ),
        radius = radius,
        center = burst.origin
    )
    // Several diminishing wave fronts rather than a single expanding disc.
    repeat(3) { index ->
        val wave = (progress - index * 0.12f).coerceAtLeast(0f)
        if (wave > 0f) drawCircle(
            burst.color.copy(alpha = 0.2f * opacity),
            radius = 14.dp.toPx() + hypot(size.width, size.height) * wave,
            center = burst.origin,
            style = androidx.compose.ui.graphics.drawscope.Stroke((3 - index).dp.toPx())
        )
    }
}

/** Owned by the chat screen, never by a sheet's composition. */
internal val LocalThinkingScreenBurst = staticCompositionLocalOf<ThinkingColorBurst?> { null }

/**
 * A drawable overlay cannot receive touch/focus events. Both the Activity and the sheet's window
 * display the same screen-owned animation; dismissing the sheet only removes its own drawable.
 */
@Composable
internal fun ThinkingScreenBurstLayer(burst: ThinkingColorBurst) {
    val root = LocalView.current.rootView
    val overlay = remember(root, burst) {
        object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: android.graphics.Canvas) {
                val progress = burst.progress.value
                if (progress >= 1f) return
                val width = root.width.toFloat()
                val height = root.height.toFloat()
                if (width <= 0f || height <= 0f) return
                val location = IntArray(2)
                root.getLocationOnScreen(location)
                val x = burst.origin.x - location[0]
                val y = burst.origin.y - location[1]
                val fade = sin(PI * progress).toFloat().coerceAtLeast(0f)
                val radius = (hypot(width, height) * (0.1f + progress * 1.4f)).coerceAtLeast(1f)
                paint.shader = android.graphics.RadialGradient(x, y, radius,
                    intArrayOf(android.graphics.Color.argb((55 * fade).toInt(), 167, 139, 250),
                        android.graphics.Color.argb((24 * fade).toInt(), 124, 58, 237), android.graphics.Color.TRANSPARENT),
                    null, android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, width, height, paint)
                paint.shader = null
                repeat(32) { index ->
                    val sx = width * ((index * 0.618034f + 0.13f) % 1f)
                    val sy = height * ((index * 0.381966f + 0.27f) % 1f) - progress * 20f
                    val sparkle = (sin(progress * PI * 6 + index).toFloat() * 0.5f + 0.5f) * fade
                    paint.color = android.graphics.Color.argb((175 * sparkle).toInt(), 233, 213, 255)
                    val ray = (2f + index % 3) * root.resources.displayMetrics.density
                    paint.strokeWidth = root.resources.displayMetrics.density
                    canvas.drawLine(sx - ray, sy, sx + ray, sy, paint)
                    canvas.drawLine(sx, sy - ray, sx, sy + ray, paint)
                    canvas.drawCircle(sx, sy, root.resources.displayMetrics.density, paint)
                }
            }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
            @Suppress("DEPRECATION")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
    DisposableEffect(root, overlay) {
        root.overlay.add(overlay)
        onDispose { root.overlay.remove(overlay) }
    }
    LaunchedEffect(root, overlay, burst) {
        snapshotFlow { burst.progress.value }.collect {
            overlay.setBounds(0, 0, root.width, root.height)
            overlay.invalidateSelf()
        }
    }
}
