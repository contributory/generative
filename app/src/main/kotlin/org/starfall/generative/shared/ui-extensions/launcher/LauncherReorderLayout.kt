package org.starfall.generative.shared.uiextensions.launcher

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** A named region: workspace, dock or the contents of a particular folder. */
typealias LauncherContainerKey = String

/** A drop slot measured from real layout coordinates (never guessed row heights). */
data class LauncherCell(val key: String, val boundsInWindow: Rect)

data class LauncherReorderHit(val key: String, val after: Boolean)

/**
 * Geometric hit testing in list or grid layouts. Finds the closest visible tile
 * rather than calculating an index from guessed margins, rows or column widths.
 */
object LauncherGridGeometry {
    fun closestSlot(
        visualCenter: Offset,
        cells: List<LauncherCell>,
        horizontal: Boolean
    ): LauncherReorderHit? {
        val cell = cells.asSequence()
            .filter { it.boundsInWindow.width > 0f && it.boundsInWindow.height > 0f }
            .minByOrNull {
                val center = it.boundsInWindow.center
                val dx = visualCenter.x - center.x
                val dy = visualCenter.y - center.y
                dx * dx + dy * dy
            } ?: return null
        val after = if (horizontal) visualCenter.x >= cell.boundsInWindow.center.x
            else visualCenter.y >= cell.boundsInWindow.center.y
        return LauncherReorderHit(cell.key, after)
    }
}

/**
 * An in-memory reorder transaction shared by workspace and folders.
 *
 * Each item has one stable key across every container; a folder itself may
 * be an item in the workspace and also have a separate child container.
 * Hover only changes [preview]. Persist [commit]'s result once on successful
 * drop; call [cancel] on Back, rejection or gesture cancellation.
 */
@Stable
class LauncherReorderLayout<T : Any>(
    private val keyOf: (T) -> String,
    initial: Map<LauncherContainerKey, List<T>> = emptyMap()
) {
    var preview: Map<LauncherContainerKey, List<T>> by mutableStateOf(copyContainers(initial))
        private set
    private var initialSnapshot: Map<LauncherContainerKey, List<T>>? = null
    var draggedKey: String? by mutableStateOf(null)
        private set

    fun update(items: Map<LauncherContainerKey, List<T>>) {
        if (draggedKey == null) preview = copyContainers(items)
    }

    fun begin(key: String): Boolean {
        if (draggedKey != null) return false
        if (preview.values.flatten().count { keyOf(it) == key } != 1) return false
        initialSnapshot = copyContainers(preview)
        draggedKey = key
        return true
    }

    /**
     * Insert before/after the hovered tile; null target inserts at the end
     * (or start when after=false). Works when the source folder is unmounted.
     */
    fun hover(
        destination: LauncherContainerKey,
        targetKey: String? = null,
        after: Boolean = true
    ): Boolean {
        val moving = draggedKey ?: return false
        val old = preview
        val item = old.values.flatten().singleOrNull { keyOf(it) == moving } ?: return false
        val destinationItems = old[destination] ?: return false
        if (targetKey == moving) return false
        if (targetKey != null && destinationItems.none { keyOf(it) == targetKey }) return false

        val next = old.mapValues { (_, values) -> values.filterNot { keyOf(it) == moving } }.toMutableMap()
        val into = next.getValue(destination).toMutableList()
        val targetIndex = if (targetKey == null) {
            if (after) into.size else 0
        } else into.indexOfFirst { keyOf(it) == targetKey } + if (after) 1 else 0
        into.add(targetIndex, item)
        next[destination] = into
        if (next == old) return false
        preview = next
        return true
    }

    /** Drop accepted: return a detached result for storage and end the transaction. */
    fun commit(): Map<LauncherContainerKey, List<T>>? {
        if (draggedKey == null) return null
        val result = copyContainers(preview)
        initialSnapshot = null
        draggedKey = null
        return result
    }

    /** Restore the exact pre-drag layout, including original folder membership. */
    fun cancel() {
        initialSnapshot?.let { preview = it }
        initialSnapshot = null
        draggedKey = null
    }

    private fun copyContainers(items: Map<LauncherContainerKey, List<T>>): Map<LauncherContainerKey, List<T>> =
        items.mapValues { (_, value) -> value.toList() }
}
