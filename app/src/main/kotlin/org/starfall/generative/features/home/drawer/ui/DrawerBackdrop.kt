package org.starfall.generative.features.home.drawer.ui

import android.os.Build
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Blurred devices can be more translucent; older devices need more opacity over sharp content. */
internal val DrawerContainerAlpha: Float
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.55f else 0.8f

private val DrawerBackdropBlurRadius = 20.dp

/**
 * Captures the chat behind the drawer and prepares a blurred copy for translucent drawer items.
 * The actual content behind the drawer remains sharp; blur is composited only inside the drawer's
 * history-item backgrounds by [drawerBackdropBlur].
 */
@Stable
class DrawerBackdrop internal constructor(
    internal val content: GraphicsLayer,
    internal val blurred: GraphicsLayer,
    private val drawerState: DrawerState,
    private val scrim: State<Color>
) {
    /** Window position of the recorded content; items subtract it to find their slice. */
    internal var contentOrigin by mutableStateOf(Offset.Zero)

    /** Measured by the drawer sheet; needed to turn the drawer offset into Material's scrim fraction. */
    internal var sheetWidthPx by mutableFloatStateOf(0f)

    internal val scrimColor: Color get() = scrim.value

    /** Same 0 (closed) to 1 (open) fraction Material 3 uses to fade its scrim. Read in draw, so it animates. */
    internal fun openFraction(): Float {
        val width = sheetWidthPx
        val offset = drawerState.currentOffset
        if (width <= 0f || offset.isNaN()) return if (drawerState.currentValue == DrawerValue.Open) 1f else 0f
        return ((width + offset) / width).coerceIn(0f, 1f)
    }
}

@Composable
fun rememberDrawerBackdrop(drawerState: DrawerState, scrimColor: Color): DrawerBackdrop {
    val content = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val scrim = rememberUpdatedState(scrimColor)
    return remember(drawerState, content, blurred) { DrawerBackdrop(content, blurred, drawerState, scrim) }
}

/**
 * Put on whatever the drawer slides over. While the drawer is visible the content is drawn through a
 * layer so it can be reused; with the drawer closed it draws directly and costs nothing extra.
 */
fun Modifier.recordDrawerBackdrop(backdrop: DrawerBackdrop, blurRadius: Dp = DrawerBackdropBlurRadius): Modifier = this
    .onGloballyPositioned { backdrop.contentOrigin = it.positionInWindow() }
    .drawWithContent {
        if (backdrop.openFraction() <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            drawContent()
            return@drawWithContent
        }
        backdrop.content.record { this@drawWithContent.drawContent() }
        val radius = blurRadius.toPx()
        backdrop.blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        backdrop.blurred.record { drawLayer(backdrop.content) }

        // Keep the original content sharp everywhere, then replace only the portion currently
        // covered by the sliding drawer with its blurred copy. Clipping to the drawer's live
        // right edge is essential: the uncovered strip on the right must remain completely sharp.
        drawLayer(backdrop.content)
        val visibleDrawerWidth = backdrop.sheetWidthPx * backdrop.openFraction()
        if (visibleDrawerWidth > 0f) {
            clipRect(left = 0f, top = 0f, right = visibleDrawerWidth, bottom = size.height) {
                drawLayer(backdrop.blurred)
            }
        }
    }

/**
 * Blurs what is behind this item. The slice is composited like the sheet itself: the blurred chat,
 * then Material's scrim at the current drawer progress, then the sheet tint, so the item matches the
 * empty area around it apart from the blur. Needs Android 12 for the blur; older versions keep the
 * plain transparent item.
 */
@Composable
internal fun Modifier.drawerBackdropBlur(backdrop: DrawerBackdrop?, shape: Shape, sheetTint: Color): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .clip(shape)
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            val fraction = backdrop.openFraction()
            if (fraction <= 0f) return@drawBehind
            val offset = origin - backdrop.contentOrigin
            translate(-offset.x, -offset.y) { drawLayer(backdrop.blurred) }
            drawRect(backdrop.scrimColor, alpha = fraction)
            drawRect(sheetTint)
        }
}
