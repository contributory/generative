package org.starfall.generative.shared.uiextensions.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

/**
 * Launcher3-style delayed reorder / folder-open trigger. Changing targets cancels
 * the pending hover automatically; a static hover fires once, not every frame.
 */
@Composable
fun <T : Any> LauncherHoverEffect(
    state: LauncherDragDropState<T>,
    delayMillis: Long = 250L,
    onHover: (targetKey: String, drag: LauncherDragSession<T>) -> Unit
) {
    val target = state.hoveredTargetKey
    val dragKey = state.session?.item?.key
    val currentOnHover = rememberUpdatedState(onHover)
    LaunchedEffect(state, dragKey, target, delayMillis) {
        if (dragKey == null || target == null) return@LaunchedEffect
        delay(delayMillis.coerceAtLeast(0L))
        val drag = state.session ?: return@LaunchedEffect
        if (drag.item.key == dragKey && state.hoveredTargetKey == target) {
            currentOnHover.value(target, drag)
        }
    }
}
