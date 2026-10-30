package org.starfall.generative.extensions.launcher

import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange

/** The down event has already passed Final, so a clickable child's consumed down is harmless. */
internal suspend fun AwaitPointerEventScope.awaitLauncherActivation(
    down: PointerInputChange,
    activation: LauncherDragActivation
): PointerInputChange? = when (activation) {
    LauncherDragActivation.AfterSlop -> awaitLauncherSlop(down)
    is LauncherDragActivation.LongPress -> {
        val timeout = activation.timeoutMillis
        if (timeout == null) awaitLongPressOrCancellation(down.id)
        else {
            var latest = down
            var cancelled = false
            val completed = withTimeoutOrNull(timeout) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || change.isConsumed ||
                        event.changes.any { it.id != down.id && it.pressed } ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        cancelled = true
                        break
                    }
                    latest = change
                    if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) {
                        cancelled = true
                        break
                    }
                }
                true
            }
            if (completed == null && !cancelled) latest else null
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitLauncherSlop(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if (!change.pressed || change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) return null
        if ((change.position - down.position).getDistance() >= viewConfiguration.touchSlop) {
            change.consume()
            return change
        }
        if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) return null
    }
}
