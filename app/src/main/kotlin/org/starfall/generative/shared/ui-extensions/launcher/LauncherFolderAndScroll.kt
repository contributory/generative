package org.starfall.generative.shared.uiextensions.launcher

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Close a folder only after the pointer has stayed outside its registered drop
 * region. The host and drag session must remain mounted during that transition.
 * Call from the stable owner of the folder state, not from a disposable tile.
 */
@Composable
fun <T : Any> LauncherFolderExitEffect(
    state: LauncherDragDropState<T>,
    folderTargetKey: String,
    open: Boolean,
    fromFolder: (LauncherDragItem<T>) -> Boolean,
    onExit: () -> Unit,
    delayMillis: Long = 300L
) {
    val drag = state.session
    val latestOnExit = rememberUpdatedState(onExit)
    val outside = open && drag != null && fromFolder(drag.item) &&
        !state.isPointerInside(folderTargetKey)
    LaunchedEffect(drag?.item?.key, outside, folderTargetKey) {
        if (outside) {
            delay(delayMillis.coerceAtLeast(0L))
            // Re-evaluate after delay: moving back inside must cancel the exit.
            if (open && state.session?.item?.key == drag?.item?.key &&
                !state.isPointerInside(folderTargetKey)) latestOnExit.value()
        }
    }
}

/**
 * Auto-scroll a lazy list/grid near the edges while a launcher drag is active.
 * Operates on the actual measured viewport and uses frame time, not polling.
 * Both LazyListState and LazyGridState implement ScrollableState.
 */
fun <T : Any> Modifier.launcherDragAutoScroll(
    state: LauncherDragDropState<T>,
    scrollState: ScrollableState,
    edge: Dp = 48.dp,
    maxSpeed: Dp = 640.dp,
    horizontal: Boolean = false
): Modifier = composed {
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val edgePx = with(density) { edge.toPx() }.coerceAtLeast(1f)
    val speedPx = with(density) { maxSpeed.toPx() }.coerceAtLeast(0f)
    val pointer = state.session?.pointer
    val dragKey = state.session?.item?.key
    val fraction = if (pointer != null && viewport.contains(pointer) &&
        viewport.width > 0f && viewport.height > 0f) {
        val along = if (horizontal) pointer.x else pointer.y
        val min = if (horizontal) viewport.left else viewport.top
        val max = if (horizontal) viewport.right else viewport.bottom
        val threshold = edgePx.coerceAtMost((max - min) / 2f)
        when {
            along < min + threshold -> -(min + threshold - along) / threshold
            along > max - threshold -> (along - (max - threshold)) / threshold
            else -> 0f
        }.coerceIn(-1f, 1f)
    } else 0f
    // Do not keep requesting frames when the pointer is not in a scroll edge.
    // An unconditional frame loop prevents Compose from becoming idle on long-press.
    LaunchedEffect(state, scrollState, dragKey, fraction, speedPx) {
        if (dragKey == null || fraction == 0f) return@LaunchedEffect
        var previousFrame = 0L
        while (state.session?.item?.key == dragKey) {
            val now = withFrameNanos { it }
            val elapsed = if (previousFrame == 0L) 0f else
                ((now - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrame = now
            val delta = fraction * speedPx * elapsed
            if (delta != 0f && abs(scrollState.scrollBy(delta)) < 0.5f) break
        }
    }
    this.onGloballyPositioned { viewport = it.boundsInWindow() }
}
