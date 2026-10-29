package org.starfall.generative.shared.uiextensions.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** The same state must be hoisted above every folder/workspace that can exchange items. */
@Composable
fun <T : Any> rememberLauncherDragDropState(): LauncherDragDropState<T> = remember {
    LauncherDragDropState()
}

/**
 * Gesture ownership and drag shadow survive removal of the original item or folder.
 * Long-press is detected at the host; no child item needs to retain a pointerInput job.
 * Sources/targets use boundsInWindow so nested scrolling/graphics transforms require
 * no caller-provided position offsets.
 */
@Composable
fun <T : Any> LauncherDragDropHost(
    state: LauncherDragDropState<T>,
    modifier: Modifier = Modifier,
    preview: @Composable (LauncherDragSession<T>) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    DisposableEffect(state) { onDispose { state.cancel() } }
    Box(
        modifier.onGloballyPositioned { state.hostOriginInWindow = it.boundsInWindow().topLeft }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startInWindow = state.hostOriginInWindow + down.position
                    // Freeze the touched item before the long-press; later relayouts
                    // must not silently select a neighboring tile.
                    val sourceHit = state.sourceHitAt(startInWindow) ?: return@awaitEachGesture
                    val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    if (!state.start(sourceHit, startInWindow, state.hostOriginInWindow + press.position)) {
                        return@awaitEachGesture
                    }
                    press.consume()
                    // Keep this pointer owned by the stable host even when a nested
                    // scrollable or its entire folder is removed during the gesture.
                    // The generic drag() detector cancels on another consumer's
                    // consumption; our session must instead survive that transition.
                    var movedEnough = false
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == press.id } ?: break
                        val position = state.hostOriginInWindow + change.position
                        if ((position - startInWindow).getDistance() >= viewConfiguration.touchSlop) {
                            movedEnough = true
                        }
                        state.move(position)
                        if (!change.pressed) {
                            released = change.previousPressed
                            break
                        }
                        change.consume()
                    }
                    if (released && movedEnough) state.drop() else state.cancel()
                }
            }
    ) {
        content()
        LauncherDragShadow(state, preview)
    }
}

@Composable
private fun <T : Any> BoxScope.LauncherDragShadow(
    state: LauncherDragDropState<T>,
    preview: @Composable (LauncherDragSession<T>) -> Unit
) {
    val drag = state.session ?: return
    val rect = drag.shadowBounds
    val origin = state.hostOriginInWindow
    val density = LocalDensity.current
    Box(
        Modifier
            .offset {
                IntOffset((rect.left - origin.x).roundToInt(), (rect.top - origin.y).roundToInt())
            }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .zIndex(1000f)
    ) {
        preview(drag)
    }
}

/** A drag source registers geometry only; the host recognizes its long-press. */
fun <T : Any> Modifier.launcherDragSource(
    state: LauncherDragDropState<T>,
    key: String,
    data: T,
    enabled: Boolean = true,
    priority: Int = 0,
    onDragStart: (LauncherDragSession<T>) -> Unit = {},
    onDragEnd: (LauncherDragOutcome) -> Unit = {}
): Modifier = composed {
    val token = remember(state, key) { Any() }
    DisposableEffect(state, token) { onDispose { state.removeSource(token) } }
    SideEffect {
        state.putSource(
            LauncherSource(token, LauncherDragItem(key, data), priority, enabled,
                onStart = onDragStart, onEnd = onDragEnd)
        )
    }
    this.onGloballyPositioned { coordinates ->
        state.putSource(
            LauncherSource(token, LauncherDragItem(key, data), priority, enabled,
                onStart = onDragStart, onEnd = onDragEnd)
        )
        state.sourceBounds(token, coordinates.boundsInWindow())
    }
}

/**
 * Register a target. Higher priority takes precedence; for ties the smallest area
 * takes precedence (a tile over its folder, a folder over its workspace).
 * Return true from onDrop only after the destination accepts the item.
 */
fun <T : Any> Modifier.launcherDropTarget(
    state: LauncherDragDropState<T>,
    key: String,
    enabled: Boolean = true,
    priority: Int = 0,
    accepts: (LauncherDragItem<T>) -> Boolean = { true },
    onDragEnter: (LauncherDragSession<T>) -> Unit = {},
    onDragMove: (LauncherDragSession<T>) -> Unit = {},
    onDragExit: (LauncherDragSession<T>) -> Unit = {},
    onDrop: (LauncherDragSession<T>) -> Boolean
): Modifier = composed {
    val token = remember(state, key) { Any() }
    DisposableEffect(state, token) { onDispose { state.removeTarget(token) } }
    SideEffect {
        state.putTarget(
            LauncherTarget(token, key, priority, enabled, accepts = accepts,
                onEnter = onDragEnter, onMove = onDragMove,
                onExit = onDragExit, onDrop = onDrop)
        )
    }
    this.onGloballyPositioned { coordinates ->
        state.putTarget(
            LauncherTarget(token, key, priority, enabled, accepts = accepts,
                onEnter = onDragEnter, onMove = onDragMove,
                onExit = onDragExit, onDrop = onDrop)
        )
        state.targetBounds(token, coordinates.boundsInWindow())
    }
}
