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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI

/** Stable per-star coordinates that do not line up into diagonal sequences. */
private fun starCoordinate(index: Int, salt: Long): Float {
    var hash = index.toLong() * 0x9E3779B9L + salt
    hash = (hash xor (hash ushr 16)) * 0x45D9F3BL
    hash = hash xor (hash ushr 16)
    return (hash and 0x7FFFFFFFL).toFloat() / Int.MAX_VALUE.toFloat()
}

/** A short, interruptible bloom; idle controls never keep an animation running. */
@Stable
internal class ThinkingColorBurst(private val scope: CoroutineScope) {
    val progress = Animatable(1f)
    var origin by mutableStateOf(Offset.Zero)
        private set
    var color by mutableStateOf(Color.Transparent)
        private set
    var starCount by mutableIntStateOf(32)
        private set
    private var job: Job? = null
    var keepAfterCompletion by mutableStateOf(false)
        private set

    /** A pronounced bloom: expand quickly, then contract before settling. */
    val bloomProgress: Float
        get() = if (progress.value < 0.52f) {
            (progress.value / 0.52f).coerceIn(0f, 1f)
        } else {
            ((1f - progress.value) / 0.48f).coerceIn(0f, 1f)
        }

    fun start(
        origin: Offset,
        color: Color,
        durationMillis: Int = 1100,
        keepAfterCompletion: Boolean = false,
        starCount: Int = 32
    ) {
        job?.cancel()
        this.origin = origin
        this.color = color
        this.starCount = starCount.coerceIn(0, 256)
        this.keepAfterCompletion = keepAfterCompletion
        job = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis.coerceIn(700, 7000), easing = FastOutSlowInEasing))
        }
    }

    /** Hide a settled screen effect immediately when its owning sheet is dismissed or leaves max. */
    fun hide() {
        job?.cancel()
        job = null
        keepAfterCompletion = false
        starCount = 0
        scope.launch { progress.snapTo(1f) }
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
    if (progress >= 0.52f) {
        draw()
        return
    }
    val radius = 14.dp.toPx() + hypot(size.width, size.height) * burst.bloomProgress
    clipPath(Path().apply { addOval(Rect(burst.origin, radius)) }) { draw() }
}

internal fun DrawScope.drawThinkingColorBurst(burst: ThinkingColorBurst) {
    val progress = burst.progress.value
    if (progress >= 1f) return
    val bloom = burst.bloomProgress
    val radius = 14.dp.toPx() + hypot(size.width, size.height) * (0.15f + bloom * 1.35f)
    val opacity = bloom * bloom
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
        val wave = (bloom - index * 0.12f).coerceAtLeast(0f)
        if (wave > 0f) drawCircle(
            burst.color.copy(alpha = 0.2f * opacity),
            radius = 14.dp.toPx() + hypot(size.width, size.height) * wave,
            center = burst.origin,
            style = androidx.compose.ui.graphics.drawscope.Stroke((3 - index).dp.toPx())
        )
    }
}

/** Soft slanted light streaks over the max-effort card; white only, so they add no extra hue. */
internal fun DrawScope.drawMaximumEffortStreaks(alpha: Float) {
    if (alpha <= 0f) return
    // (left edge as a fraction of the width, band width as a fraction, peak opacity)
    val streaks = listOf(
        Triple(0.06f, 0.05f, 0.10f),
        Triple(0.22f, 0.018f, 0.24f),
        Triple(0.34f, 0.07f, 0.12f),
        Triple(0.52f, 0.022f, 0.28f),
        Triple(0.66f, 0.05f, 0.14f),
        Triple(0.82f, 0.016f, 0.22f),
        Triple(0.92f, 0.04f, 0.10f)
    )
    rotate(degrees = 20f, pivot = center) {
        streaks.forEach { (at, span, strength) ->
            val left = size.width * at
            val bandWidth = size.width * span
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = strength * alpha), Color.Transparent),
                    startX = left,
                    endX = left + bandWidth
                ),
                topLeft = Offset(left, -size.height),
                size = androidx.compose.ui.geometry.Size(bandWidth, size.height * 3f)
            )
        }
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
                val settled = progress >= 1f && burst.keepAfterCompletion
                if (progress >= 1f && !settled) return
                val width = root.width.toFloat()
                val height = root.height.toFloat()
                if (width <= 0f || height <= 0f) return
                val location = IntArray(2)
                root.getLocationOnScreen(location)
                val x = burst.origin.x - location[0]
                val y = burst.origin.y - location[1]
                val bloom = if (settled) 1f else burst.bloomProgress
                val fade = bloom
                // The background tint only grows in and then stays; it must not follow the bloom's
                // contraction, otherwise it fades out and pops back in as a flash.
                val bgFade = if (settled) 1f else (progress / 0.52f).coerceIn(0f, 1f)
                val radius = (hypot(width, height) * (0.1f + bgFade * 1.55f)).coerceAtLeast(1f)
                val isRed = burst.color.red > burst.color.blue
                val primaryRed = if (isRed) 220 else 167
                val primaryGreen = if (isRed) 38 else 139
                val primaryBlue = if (isRed) 38 else 250
                val secondaryRed = if (isRed) 153 else 124
                val secondaryGreen = if (isRed) 27 else 58
                val secondaryBlue = if (isRed) 27 else 237
                paint.shader = android.graphics.RadialGradient(x, y, radius,
                    intArrayOf(android.graphics.Color.argb((55 * bgFade).toInt(), primaryRed, primaryGreen, primaryBlue),
                        android.graphics.Color.argb((24 * bgFade).toInt(), secondaryRed, secondaryGreen, secondaryBlue), android.graphics.Color.TRANSPARENT),
                    null, android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, width, height, paint)
                paint.shader = null
                repeat(burst.starCount) { index ->
                    val sx = width * starCoordinate(index, 0x13579BDFL)
                    val sy = height * starCoordinate(index, 0x2468ACE1L) - bloom * 20f
                    val twinkle = (sin(progress * PI * 6 + index * 2.41).toFloat() * 0.5f + 0.5f)
                    val secondaryTwinkle = (sin(progress * PI * 3 + index * 0.73).toFloat() * 0.5f + 0.5f)
                    val sparkle = if (isRed) {
                        // Red stars keep twinkling independently instead of sharing the
                        // overlay's fade as a single on/off state.
                        (0.08f + 0.92f * (twinkle * 0.72f + secondaryTwinkle * 0.28f)) *
                            (0.38f + 0.62f * fade)
                    } else {
                        twinkle * fade
                    }
                    paint.color = android.graphics.Color.argb(
                        ((if (isRed) 240 else 175) * sparkle).toInt().coerceIn(0, 255),
                        233, 213, 255
                    )
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
        snapshotFlow { burst.progress.value to burst.keepAfterCompletion }.collect {
            overlay.setBounds(0, 0, root.width, root.height)
            overlay.invalidateSelf()
        }
    }
}
