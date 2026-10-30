package org.starfall.generative.extensions.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LauncherItemDropPolicyDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = LauncherDragDropState<Int>()
    private var joinRegion by mutableStateOf<LauncherHitTest?>(null)
    private var callbackProvided = true
    private var joinAllowed = true
    private var joinAccepted = true
    private var reorders = 0
    private var joins = 0
    private var outcome: LauncherDragOutcome? = null

    private fun show() {
        compose.setContent {
            LauncherDragDropHost(state, Modifier.fillMaxSize(), preview = {}) {
                Box(Modifier.size(80.dp).launcherDragSource(state, "source", 7,
                    onDragEnd = { outcome = it }).testTag("source"))
                Box(Modifier.offset(120.dp, 40.dp).size(160.dp)
                    .launcherItemDropTarget(state, "target", policy = LauncherItemDropPolicy(joinHitTest = joinRegion),
                        acceptsJoin = { it.data == 7 && joinAllowed },
                        onJoinDrop = if (callbackProvided) ({ joins++; joinAccepted }) else null,
                        onReorderDrop = { reorders++; true })
                    .testTag("target"))
            }
        }
        compose.waitForIdle()
        val start = compose.onNodeWithTag("source").fetchSemanticsNode().boundsInRoot.center - origin()
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
    }

    private fun origin() = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
    private fun move(fractionX: Float = 0.5f, fractionY: Float = 0.5f) {
        val target = compose.onNodeWithTag("target").fetchSemanticsNode().boundsInRoot
        val point = Offset(target.left + target.width * fractionX, target.top + target.height * fractionY) - origin()
        compose.onRoot().performTouchInput { moveTo(point) }
        compose.waitForIdle()
    }
    private fun release() { compose.onRoot().performTouchInput { up() }; compose.waitForIdle() }

    @Test fun ordinaryItemCenterHasOnlyAReorderTargetByDefault() {
        show(); move()
        compose.runOnIdle {
            assertEquals("target", state.hoveredTargetKey)
            assertEquals(listOf("target"), state.targetCells().map { it.key })
        }
        release()
        assertEquals(1, reorders)
        assertEquals(0, joins)
    }

    @Test fun regionWithoutJoinCallbackCannotReserveAnyPartOfTheItem() {
        joinRegion = LauncherHitTest.Center()
        callbackProvided = false
        show(); move()
        compose.runOnIdle { assertEquals("target", state.hoveredTargetKey); assertEquals(1, state.targetCells().size) }
        release()
        assertEquals(1, reorders)
    }

    @Test fun enabledJoinUsesItsCenterWhileEdgesContinueToReorder() {
        joinRegion = LauncherHitTest.Center()
        show(); move()
        compose.runOnIdle { assertEquals("target:join", state.hoveredTargetKey) }
        move(0.1f)
        compose.runOnIdle { assertEquals("target", state.hoveredTargetKey) }
        compose.onRoot().performTouchInput { cancel() }
    }

    @Test fun deniedJoinLeavesTheWholeCenterAvailableForReordering() {
        joinRegion = LauncherHitTest.Center()
        joinAllowed = false
        show(); move(); release()
        assertEquals(1, reorders)
        assertEquals(0, joins)
        assertEquals(LauncherDragOutcome(true, "target"), outcome)
    }

    @Test fun turningJoinOffRetargetsAStationaryDragWithoutLeavingADeadCenter() {
        joinRegion = LauncherHitTest.Center()
        show(); move()
        compose.runOnIdle { assertEquals("target:join", state.hoveredTargetKey); joinRegion = null }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("target", state.hoveredTargetKey) }
        release()
        assertEquals(1, reorders)
        assertEquals(0, joins)
    }

    @Test fun customGroupingCanBeEnabledForOrdinaryDataWithoutAFolderType() {
        joinRegion = LauncherHitTest.Custom { drag, target ->
            (drag.visualCenter - target.center).getDistance() < target.width / 3
        }
        show(); move(); release()
        assertEquals(1, joins)
        assertEquals(0, reorders)
        assertEquals(LauncherDragOutcome(true, "target:join"), outcome)
    }

    @Test fun applicationJoinFailureRollsBackInsteadOfPerformingAnUnrequestedReorder() {
        joinRegion = LauncherHitTest.Center()
        joinAccepted = false
        show(); move(); release()
        assertEquals(1, joins)
        assertEquals(0, reorders)
        assertEquals(LauncherDragOutcome(false, null), outcome)
    }
}
