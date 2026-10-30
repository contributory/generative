package org.starfall.generative.extensions.launcher

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/** A join region is opt-in. Rejected/disabled joins leave the entire reorder region available. */
data class LauncherItemDropPolicy(
    val reorderHitTest: LauncherHitTest = LauncherHitTest.VisualCenter,
    val joinHitTest: LauncherHitTest? = null
)

/**
 * Register reorder and optional join actions independently, without assuming an item is a folder.
 * Normal items need only onReorderDrop. A folder (or custom grouping target) explicitly supplies
 * both a join policy and callback. Hover timers can use the two separate keys as usual.
 */
fun <T : Any> Modifier.launcherItemDropTarget(
    state: LauncherDragDropState<T>,
    key: String,
    policy: LauncherItemDropPolicy = LauncherItemDropPolicy(),
    enabled: Boolean = true,
    priority: Int = 0,
    hitBounds: Rect? = null,
    acceptsReorder: (LauncherDragItem<T>) -> Boolean = { true },
    joinKey: String = "$key:join",
    joinPriority: Int = priority + 1,
    acceptsJoin: (LauncherDragItem<T>) -> Boolean = { true },
    joinLandingBounds: (() -> Rect?)? = null,
    onJoinDrop: ((LauncherDragSession<T>) -> Boolean)? = null,
    onReorderEnter: (LauncherDragSession<T>) -> Unit = {},
    onReorderDrop: (LauncherDragSession<T>) -> Boolean
): Modifier {
    require(policy.joinHitTest == null || onJoinDrop == null || key != joinKey) {
        "Reorder and join actions need distinct target keys"
    }
    val reorder = launcherDropTarget(
        state, key, enabled = enabled, priority = priority,
        hitTest = policy.reorderHitTest, hitBounds = hitBounds,
        accepts = acceptsReorder, onDragEnter = onReorderEnter, onDrop = onReorderDrop
    )
    val joinHitTest = policy.joinHitTest ?: return reorder
    val join = onJoinDrop ?: return reorder
    return reorder.launcherDropTarget(
        state, joinKey, enabled = enabled, priority = joinPriority,
        hitTest = joinHitTest, hitBounds = hitBounds, accepts = acceptsJoin,
        landingBounds = joinLandingBounds, onDrop = join
    )
}
