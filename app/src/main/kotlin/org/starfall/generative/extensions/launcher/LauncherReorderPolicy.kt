package org.starfall.generative.extensions.launcher

import androidx.compose.ui.geometry.Rect

/** Current transaction indices, not stale pickup indices or animated drawing positions. */
data class LauncherReorderContext(
    val drag: LauncherDragSession<*>,
    val targetBounds: Rect,
    val sourceIndex: Int?,
    val targetIndex: Int,
    val horizontal: Boolean,
    val rtl: Boolean
)

/** Choosing an insertion side is separate from reserving a region for joining/grouping. */
sealed interface LauncherReorderPolicy {
    /** Move to the hovered item's index on entry; no center crossing is needed in the same container. */
    data object OnEntry : LauncherReorderPolicy
    /** Use the pointer/artwork half of the target, useful for explicit insertion boundaries. */
    data object CrossCenter : LauncherReorderPolicy
    data object Before : LauncherReorderPolicy
    data object After : LauncherReorderPolicy
    class Custom(val placeAfter: (LauncherReorderContext) -> Boolean) : LauncherReorderPolicy

    fun placeAfter(context: LauncherReorderContext): Boolean = when (this) {
        OnEntry -> context.sourceIndex?.let { it < context.targetIndex }
            ?: CrossCenter.placeAfter(context)
        CrossCenter -> if (context.horizontal) {
            (context.drag.visualCenter.x >= context.targetBounds.center.x) != context.rtl
        } else context.drag.visualCenter.y >= context.targetBounds.center.y
        Before -> false
        After -> true
        is Custom -> placeAfter.invoke(context)
    }
}
