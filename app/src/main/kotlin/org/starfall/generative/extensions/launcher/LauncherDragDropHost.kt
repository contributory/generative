package org.starfall.generative.extensions.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates

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
    activation: LauncherDragActivation = LauncherDragActivation.LongPress(),
    style: LauncherDragStyle = LauncherDragStyle(),
    content: @Composable BoxScope.() -> Unit
) {
    DisposableEffect(state) {
        onDispose {
            try { state.cancel() } finally { state.clearSettlement() }
        }
    }
    Box(
        modifier.onGloballyPositioned { state.hostOriginInWindow = it.localToWindow(Offset.Zero) }
            .pointerInput(state, activation) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startInWindow = state.hostOriginInWindow + down.position
                    // Freeze the touched item before the long-press; later relayouts
                    // must not silently select a neighboring tile.
                    val sourceHit = state.sourceHitAt(startInWindow) ?: return@awaitEachGesture
                    // Complete this same down before waiting for the NEXT event at Main.
                    // Otherwise a clickable folder consumes it and cancels its own drag.
                    awaitPointerEvent(PointerEventPass.Final)
                    val press = awaitLauncherActivation(down, activation) ?: return@awaitEachGesture
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
                    try {
                        while (state.session != null) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.id != press.id && it.pressed }) break
                            val change = event.changes.firstOrNull { it.id == press.id } ?: break
                            val position = state.hostOriginInWindow + change.position
                            if ((position - startInWindow).getDistance() >= viewConfiguration.touchSlop) {
                                movedEnough = true
                            }
                            state.move(position)
                            if (!change.pressed) {
                                // Compose represents pointer cancellation as an already-consumed
                                // synthetic release. Only an unconsumed physical release may commit.
                                released = change.previousPressed && !change.isConsumed
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                        if (released && movedEnough) state.drop() else state.cancel()
                    } finally {
                        // Cancellation/disposal of pointerInput must roll back a live transaction.
                        if (state.session != null) state.cancel()
                    }
                }
            }
    ) {
        content()
        LauncherDragShadow(state, style, preview)
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
    onDragEnd: (LauncherDragOutcome) -> Unit = {},
    /** Artwork region in source-local coordinates; null uses the entire item. */
    dragRegion: Rect? = null,
    hideWhileDragging: Boolean = true,
    /** Fallback return destination if the source container has closed or its tile is offscreen. */
    returnBounds: (() -> Rect?)? = null,
    /** Optional source-local handle rectangle; the shadow still uses the whole source. */
    handleBounds: Rect? = null
): Modifier = composed {
    val token = remember(state, key) { Any() }
    DisposableEffect(state, token) { onDispose { state.removeSource(token) } }
    SideEffect {
        state.putSource(
            LauncherSource(token, LauncherDragItem(key, data), priority, enabled,
                onStart = onDragStart, onEnd = onDragEnd, dragRegion = dragRegion, returnBounds = returnBounds, handleBounds = handleBounds)
        )
    }
    this.graphicsLayer { alpha = if (hideWhileDragging && state.isDragging(key)) 0f else 1f }
        .then(if (hideWhileDragging && state.isDragging(key)) Modifier.clearAndSetSemantics {} else Modifier)
        .onGloballyPositioned { coordinates ->
        state.putSource(
            LauncherSource(token, LauncherDragItem(key, data), priority, enabled,
                onStart = onDragStart, onEnd = onDragEnd, dragRegion = dragRegion, returnBounds = returnBounds, handleBounds = handleBounds)
        )
        state.sourceBounds(token,
            coordinates.unclippedBoundsInWindow(),
            coordinates.boundsInWindow(), coordinates.size.toSize())
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
    hitTest: LauncherHitTest = LauncherHitTest.Pointer,
    /** Optional explicit landing area in window coordinates, e.g. the folder preview icon. */
    landingBounds: (() -> Rect?)? = null,
    /** Optional logical slot in window coordinates, independent of an animated tile's drawing. */
    hitBounds: Rect? = null,
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
                onExit = onDragExit, onDrop = onDrop, hitTest = hitTest, landingBounds = landingBounds, hitBounds = hitBounds)
        )
    }
    this.onGloballyPositioned { coordinates ->
        state.putTarget(
            LauncherTarget(token, key, priority, enabled, accepts = accepts,
                onEnter = onDragEnter, onMove = onDragMove,
                onExit = onDragExit, onDrop = onDrop, hitTest = hitTest, landingBounds = landingBounds, hitBounds = hitBounds)
        )
        state.targetBounds(token, coordinates.boundsInWindow())
    }
}

/** Full transformed bounds for the preview; visible clipped bounds are used only for hit testing. */
private fun LayoutCoordinates.unclippedBoundsInWindow(): Rect {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val points = listOf(Offset.Zero, Offset(w, 0f), Offset(0f, h), Offset(w, h)).map(::localToWindow)
    return Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}
