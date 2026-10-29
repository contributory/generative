package org.starfall.generative.shared.uiextensions.launcher

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Stable identity even while the source is temporarily removed from composition. */
data class LauncherDragItem<T : Any>(val key: String, val data: T)

/** All coordinates are in the window, not relative to a moving folder or item. */
data class LauncherDragSession<T : Any>(
    val item: LauncherDragItem<T>,
    val sourceBounds: Rect,
    val startPointer: Offset,
    val pointer: Offset
) {
    val shadowBounds: Rect get() = sourceBounds.translate(pointer - startPointer)
    val visualCenter: Offset get() = shadowBounds.center
}

data class LauncherDragOutcome(val accepted: Boolean, val targetKey: String?)

internal data class LauncherSourceHit(val token: Any, val bounds: Rect)

internal class LauncherSource<T : Any>(
    val token: Any,
    var item: LauncherDragItem<T>,
    var priority: Int,
    var enabled: Boolean,
    var bounds: Rect = Rect.Zero,
    var onStart: (LauncherDragSession<T>) -> Unit,
    var onEnd: (LauncherDragOutcome) -> Unit
)

internal class LauncherTarget<T : Any>(
    val token: Any,
    var key: String,
    var priority: Int,
    var enabled: Boolean,
    var bounds: Rect = Rect.Zero,
    var accepts: (LauncherDragItem<T>) -> Boolean,
    var onEnter: (LauncherDragSession<T>) -> Unit,
    var onMove: (LauncherDragSession<T>) -> Unit,
    var onExit: (LauncherDragSession<T>) -> Unit,
    var onDrop: (LauncherDragSession<T>) -> Boolean
)

/**
 * Compose adaptation of Launcher3's single-controller / multiple-target approach.
 *
 * A gesture is owned by the stable host, never by an item that may disappear when
 * moving between a folder and the workspace. Source completion is transactional:
 * an unaccepted drop produces a failure outcome; consumers restore their snapshot.
 */
@Stable
class LauncherDragDropState<T : Any> {
    private val sources = linkedMapOf<Any, LauncherSource<T>>()
    private val targets = linkedMapOf<Any, LauncherTarget<T>>()
    private var hoveredToken: Any? = null
    private var sourceCompletion: ((LauncherDragOutcome) -> Unit)? = null

    var session: LauncherDragSession<T>? by mutableStateOf(null)
        private set
    var hoveredTargetKey: String? by mutableStateOf(null)
        private set
    var hostOriginInWindow: Offset by mutableStateOf(Offset.Zero)
        internal set

    internal fun putSource(source: LauncherSource<T>) {
        val current = sources[source.token]
        if (current == null) sources[source.token] = source
        else {
            current.item = source.item
            current.priority = source.priority
            current.enabled = source.enabled
            current.onStart = source.onStart
            current.onEnd = source.onEnd
        }
    }

    internal fun sourceBounds(token: Any, bounds: Rect) {
        sources[token]?.bounds = bounds
    }

    internal fun removeSource(token: Any) {
        sources.remove(token) // Keep the active session and its completion callback.
    }

    internal fun putTarget(target: LauncherTarget<T>) {
        val current = targets[target.token]
        if (current == null) targets[target.token] = target
        else {
            current.key = target.key
            current.priority = target.priority
            current.enabled = target.enabled
            current.accepts = target.accepts
            current.onEnter = target.onEnter
            current.onMove = target.onMove
            current.onExit = target.onExit
            current.onDrop = target.onDrop
        }
        refreshTarget()
    }

    internal fun targetBounds(token: Any, bounds: Rect) {
        targets[token]?.bounds = bounds
        refreshTarget()
    }

    internal fun removeTarget(token: Any) {
        val old = targets.remove(token)
        if (hoveredToken == token) {
            hoveredToken = null
            hoveredTargetKey = null
            session?.let { old?.onExit?.invoke(it) }
        }
        refreshTarget()
    }

    /** Folder exit detection is independent from whichever child tile is hovered. */
    fun isPointerInside(targetKey: String): Boolean {
        val point = session?.pointer ?: return false
        return targets.values.any { it.enabled && it.key == targetKey && it.bounds.contains(point) }
    }

    internal fun sourceHitAt(point: Offset): LauncherSourceHit? =
        sourceAt(point)?.let { LauncherSourceHit(it.token, it.bounds) }

    private fun sourceAt(point: Offset): LauncherSource<T>? = sources.values
        .filter { it.enabled && it.bounds.width > 0f && it.bounds.height > 0f && it.bounds.contains(point) }
        .maxWithOrNull(compareBy<LauncherSource<T>> { it.priority }
            .thenBy { -it.bounds.width * it.bounds.height })

    internal fun start(downPoint: Offset, currentPoint: Offset = downPoint): Boolean =
        sourceHitAt(downPoint)?.let { start(it, downPoint, currentPoint) } == true

    internal fun start(hit: LauncherSourceHit, downPoint: Offset, currentPoint: Offset): Boolean {
        if (session != null) return false
        val source = sources[hit.token]?.takeIf { it.enabled } ?: return false
        val active = LauncherDragSession(source.item, hit.bounds, downPoint, currentPoint)
        sourceCompletion = source.onEnd
        session = active
        source.onStart(active)
        refreshTarget()
        return true
    }

    internal fun move(point: Offset) {
        val current = session ?: return
        session = current.copy(pointer = point)
        refreshTarget(notifyMove = true)
    }

    private fun refreshTarget(notifyMove: Boolean = false) {
        val drag = session ?: return
        // More specific areas win over large containers at equal priority.
        val next = targets.values
            .filter { it.enabled && it.bounds.width > 0f && it.bounds.height > 0f &&
                it.bounds.contains(drag.pointer) && it.accepts(drag.item) }
            .maxWithOrNull(compareBy<LauncherTarget<T>> { it.priority }
                .thenBy { -it.bounds.width * it.bounds.height })
        if (hoveredToken != next?.token) {
            hoveredToken?.let { targets[it]?.onExit?.invoke(drag) }
            hoveredToken = next?.token
            hoveredTargetKey = next?.key
            next?.onEnter?.invoke(drag)
        }
        if (notifyMove) next?.onMove?.invoke(drag)
    }

    fun drop(): LauncherDragOutcome {
        val drag = session ?: return LauncherDragOutcome(false, null)
        refreshTarget()
        val target = hoveredToken?.let { targets[it] }
        val accepted = target?.takeIf { it.enabled && it.accepts(drag.item) }?.onDrop?.invoke(drag) == true
        return finish(LauncherDragOutcome(accepted, target?.key?.takeIf { accepted }))
    }

    fun cancel(): LauncherDragOutcome = finish(LauncherDragOutcome(false, null))

    private fun finish(outcome: LauncherDragOutcome): LauncherDragOutcome {
        val drag = session ?: return outcome
        val completion = sourceCompletion
        val exitTarget = hoveredToken?.let { targets[it] }
        // Clear before invoking callbacks; they may remove a folder or start navigation.
        session = null
        hoveredToken = null
        hoveredTargetKey = null
        sourceCompletion = null
        exitTarget?.onExit?.invoke(drag)
        completion?.invoke(outcome)
        return outcome
    }
}
