package org.starfall.generative.extensions.launcher

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LauncherReorderResponseDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = LauncherDragDropState<String>()
    private val area = Rect(0f, 0f, 100f, 100f)
    private val first = Any()
    private fun target(token: Any, key: String, bounds: Rect = area) = LauncherTarget(token, key, 0, true,
        bounds, { _: LauncherDragItem<String> -> true }, {}, {}, {}, { true })
    private fun register() {
        state.putSource(LauncherSource(Any(), LauncherDragItem("item", "item"), 0, true, area, {}, {}))
        state.putTarget(target(first, "first"))
    }
    private fun start() { compose.runOnIdle { state.start(Offset(50f, 50f)) }; compose.waitForIdle() }
    private fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    @Test fun noopHoverDoesNotBlockANearbyTargetAfterTinyPointerMovement() {
        register()
        var calls = 0
        compose.setContent { LauncherHoverActionEffect(state, delayMillis = 100) { _, _ -> ++calls > 1 } }
        start(); advance(150)
        compose.runOnIdle {
            assertEquals(1, calls)
            state.targetBounds(first, Rect(200f, 0f, 300f, 100f))
            state.putTarget(target(Any(), "second"))
            state.move(Offset(51f, 50f))
        }
        advance(150)
        compose.runOnIdle { assertEquals(2, calls); state.cancel() }
    }

    @Test fun changedIntentRetriesANoopInsideTheSameTargetWithoutLeavingIt() {
        register()
        var intent by mutableIntStateOf(0)
        var calls = 0
        compose.setContent { LauncherHoverActionEffect(state, delayMillis = 100, intentKey = intent) { _, _ -> calls++; intent > 0 } }
        start(); advance(150)
        compose.runOnIdle { assertEquals(1, calls); intent = 1 }
        compose.waitForIdle(); advance(150)
        compose.runOnIdle { assertEquals(2, calls); state.cancel() }
    }

    @Test fun appliedActionStillPreventsStationaryRelayoutCascades() {
        register()
        var calls = 0
        compose.setContent { LauncherHoverActionEffect(state, delayMillis = 100) { _, _ ->
            calls++
            if (calls == 1) {
                state.targetBounds(first, Rect(200f, 0f, 300f, 100f))
                state.putTarget(target(Any(), "second"))
            }
            true
        } }
        start(); advance(500)
        compose.runOnIdle { assertEquals(1, calls); state.move(Offset(70f, 50f)) }
        compose.waitForIdle(); advance(150)
        compose.runOnIdle { assertEquals(2, calls); state.cancel() }
    }

    @Test fun zeroDelayAppliesWithoutWaitingForADwellTimer() {
        register()
        var calls = 0
        compose.setContent { LauncherHoverActionEffect(state, delayMillis = 0) { _, _ -> calls++; true } }
        start()
        compose.runOnIdle { assertEquals(1, calls); state.cancel() }
    }

    @Test fun configuredDelayDoesNotApplyBeforeItsDeadline() {
        register()
        var calls = 0
        compose.setContent { LauncherHoverActionEffect(state, delayMillis = 300) { _, _ -> calls++; true } }
        start(); advance(100)
        compose.runOnIdle { assertEquals(0, calls) }
        advance(250)
        compose.runOnIdle { assertEquals(1, calls); state.cancel() }
    }

    @Test fun delayCanBeChangedToZeroWhileThePointerIsStationary() {
        register()
        var dwell by mutableLongStateOf(300)
        var calls = 0
        compose.setContent { LauncherHoverActionEffect(state, delayMillis = dwell) { _, _ -> calls++; true } }
        start(); advance(100)
        compose.runOnIdle { assertEquals(0, calls); dwell = 0 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, calls); state.cancel() }
    }
}
