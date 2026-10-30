package org.starfall.generative.features.settings.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.extensions.launcher.Launcher

/** Validates that the single-import facade uses the original transaction/policy objects. */
class LauncherFacadeTest {
    @Test
    fun aliasesShareTheSameControllerAndTransaction() {
        val state: Launcher.State<String> = Launcher.State()
        assertNull(state.session)

        val layout: Launcher.Layout<String> = Launcher.Layout(
            { it }, mapOf("workspace" to listOf("a", "b"))
        )
        assertTrue(layout.begin("a"))
        assertTrue(layout.hover("workspace", "b", after = true))
        assertEquals(listOf("b", "a"), layout.commit()?.get("workspace"))
        assertFalse(layout.begin("missing"))
    }

    @Test
    fun policiesAndGeometryAreExposedWithoutAdditionalImports() {
        val session: Launcher.Session<String> = Launcher.Session(
            Launcher.Item("a", "a"),
            Rect(0f, 0f, 100f, 100f),
            Offset(50f, 50f),
            Offset(150f, 50f)
        )
        val rule: Launcher.ReorderRule = Launcher.Reorder.CrossCenter
        val context: Launcher.ReorderContext = Launcher.ReorderContext(
            session, Rect(100f, 0f, 200f, 100f),
            sourceIndex = 0, targetIndex = 1, horizontal = true, rtl = false
        )
        assertTrue(rule.placeAfter(context))
        assertTrue(Launcher.Collision.Center(0.5f, 0.5f).contains(session, context.targetBounds))
        val policy: Launcher.DropPolicy = Launcher.DropPolicy(joinHitTest = Launcher.Collision.VisualCenter)
        assertEquals(Launcher.Collision.VisualCenter, policy.joinHitTest)
        val cell = Launcher.Cell("b", context.targetBounds)
        assertEquals("b", Launcher.geometry.closestSlot(Offset(150f, 50f), listOf(cell), true)?.key)
    }
}
