package org.starfall.generative.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.extensions.launcher.LauncherDragDropState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi")
class LauncherDragCollectionGeometryTest {
    @get:Rule val compose = createComposeRule()

    private fun verify(list: Boolean, rtl: Boolean) {
        val measured = mutableMapOf<String, Rect>()
        val slots = mutableMapOf<String, Rect?>()
        val items = (1..6).map { "$it" }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().padding(start = 17.dp, top = 29.dp)) {
                    LauncherDragTestCollection(items, LauncherDragDropState(), LauncherDragTestOptions(list = list),
                        Modifier.fillMaxSize(), padding = 16.dp, enabled = false) { id, modifier, slot ->
                        SideEffect { slots[id] = slot }
                        Box(modifier.fillMaxWidth().height(88.dp).onGloballyPositioned { measured[id] = it.boundsInWindow() })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(items.toSet(), measured.keys)
            for (id in items) assertEquals("logical slot must match measured tile $id", measured[id], slots[id])
        }
    }

    @Test fun gridSlotsMatchMeasuredTilesIncludingContentPadding() = verify(list = false, rtl = false)
    @Test fun listSlotsMatchMeasuredRowsIncludingContentPadding() = verify(list = true, rtl = false)
    @Test fun rtlListSlotsMatchPhysicalMeasuredRows() = verify(list = true, rtl = true)
    @Test fun rtlGridSlotsMatchPhysicalMeasuredTiles() = verify(list = false, rtl = true)
}
