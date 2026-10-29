package org.starfall.generative

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.starfall.generative.features.menu.ui.AppMenuScreen
import org.starfall.generative.features.menu.ui.GroupBottomShape
import org.starfall.generative.features.menu.ui.GroupInnerCorner
import org.starfall.generative.features.menu.ui.GroupMiddleShape
import org.starfall.generative.features.menu.ui.GroupOuterCorner
import org.starfall.generative.features.menu.ui.GroupTopShape
import org.starfall.generative.features.menu.ui.featureButtonBorder
import org.starfall.generative.features.menu.ui.featureButtonContainerColor
import org.starfall.generative.shared.theme.GenerativeTheme
import org.starfall.generative.shared.theme.ThemePresets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h1600dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppMenuScreenGroupUiTest {
    @get:Rule val compose = createComposeRule()

    private fun maxChannelDelta(a: Int, b: Int): Int = max(
        max(abs(AndroidColor.red(a) - AndroidColor.red(b)), abs(AndroidColor.green(a) - AndroidColor.green(b))),
        abs(AndroidColor.blue(a) - AndroidColor.blue(b))
    )

    private fun tapAt(offset: Offset) {
        compose.onRoot().performTouchInput { click(offset) }
        compose.waitForIdle()
    }

    @Test
    fun groupShapesUseLargeOuterCornersAndSlightInnerAdjacentCorners() {
        val density = Density(2f)
        val size = Size(600f, 160f)
        val outerPx = with(density) { GroupOuterCorner.toPx() }
        val innerPx = with(density) { GroupInnerCorner.toPx() }
        assertTrue(innerPx > 0f && innerPx < outerPx)

        val top = (GroupTopShape.createOutline(size, LayoutDirection.Ltr, density) as Outline.Rounded).roundRect
        assertEquals(outerPx, top.topLeftCornerRadius.x, 0.01f)
        assertEquals(outerPx, top.topRightCornerRadius.x, 0.01f)
        assertEquals(innerPx, top.bottomLeftCornerRadius.x, 0.01f)
        assertEquals(innerPx, top.bottomRightCornerRadius.x, 0.01f)

        val middle = (GroupMiddleShape.createOutline(size, LayoutDirection.Ltr, density) as Outline.Rounded).roundRect
        assertEquals(innerPx, middle.topLeftCornerRadius.x, 0.01f)
        assertEquals(innerPx, middle.topRightCornerRadius.x, 0.01f)
        assertEquals(innerPx, middle.bottomLeftCornerRadius.x, 0.01f)
        assertEquals(innerPx, middle.bottomRightCornerRadius.x, 0.01f)

        val bottom = (GroupBottomShape.createOutline(size, LayoutDirection.Ltr, density) as Outline.Rounded).roundRect
        assertEquals(innerPx, bottom.topLeftCornerRadius.x, 0.01f)
        assertEquals(innerPx, bottom.topRightCornerRadius.x, 0.01f)
        assertEquals(outerPx, bottom.bottomLeftCornerRadius.x, 0.01f)
        assertEquals(outerPx, bottom.bottomRightCornerRadius.x, 0.01f)
    }

    @Test
    fun interactiveAreaFollowsOuterAndInnerRoundedCornersInsteadOfSquareBounds() {
        val clicked = mutableListOf<String>()
        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                AppMenuScreen(
                    onNavigateToProviders = { clicked += "Providers" },
                    onNavigateToMcp = { clicked += "MCP" },
                    onNavigateToRemoteMachines = { clicked += "Machines" }
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Remote Machines"))
        compose.waitForIdle()

        val providersBounds = compose.onNodeWithText("Model Providers").fetchSemanticsNode().boundsInRoot
        val mcpBounds = compose.onNodeWithText("MCP Connectors").fetchSemanticsNode().boundsInRoot
        val machinesBounds = compose.onNodeWithText("Remote Machines").fetchSemanticsNode().boundsInRoot

        // Outside the 24dp (48px at xhdpi) top-left and top-right outer corners of the group's first item.
        tapAt(Offset(providersBounds.left + 4f, providersBounds.top + 4f))
        tapAt(Offset(providersBounds.right - 4f, providersBounds.top + 4f))

        // Outside the 6dp (12px at xhdpi) inner adjacent corners where items meet in the same group.
        tapAt(Offset(providersBounds.left + 1f, providersBounds.bottom - 1f))
        tapAt(Offset(mcpBounds.left + 1f, mcpBounds.top + 1f))
        tapAt(Offset(mcpBounds.left + 1f, mcpBounds.bottom - 1f))
        tapAt(Offset(machinesBounds.left + 1f, machinesBounds.top + 1f))

        // Outside the 24dp (48px at xhdpi) bottom-left and bottom-right outer corners of the group's last item.
        tapAt(Offset(machinesBounds.left + 4f, machinesBounds.bottom - 4f))
        tapAt(Offset(machinesBounds.right - 4f, machinesBounds.bottom - 4f))

        assertEquals(emptyList<String>(), clicked)

        // Inside the rounded curves of each item triggers navigation as expected.
        tapAt(Offset(providersBounds.left + 24f, providersBounds.top + 24f))
        tapAt(Offset(mcpBounds.left + 4f, mcpBounds.top + 4f))
        tapAt(Offset(machinesBounds.left + 24f, machinesBounds.bottom - 24f))

        assertEquals(listOf("Providers", "MCP", "Machines"), clicked)
    }

    @Test
    fun screenStaysOpaqueWhileItemsAreTranslucentOrOutlinedInAmoled() {
        var themeMode by mutableStateOf("LIGHT")
        var amoledMode by mutableStateOf(false)
        var presetName by mutableStateOf("DEFAULT")

        var screenBackground = Color.Unspecified
        var buttonColor = Color.Unspecified
        var buttonBorder: BorderStroke? = null
        var expectedOutlineVariant = Color.Unspecified

        compose.setContent {
            GenerativeTheme(
                themeMode = themeMode,
                amoledMode = amoledMode,
                dynamicColor = false,
                colorSchemeName = presetName
            ) {
                screenBackground = MaterialTheme.colorScheme.background
                buttonColor = featureButtonContainerColor()
                buttonBorder = featureButtonBorder()
                expectedOutlineVariant = MaterialTheme.colorScheme.outlineVariant
                AppMenuScreen()
            }
        }
        compose.waitForIdle()

        for (preset in ThemePresets) {
            presetName = preset.name

            // Light mode (even if amoled preference is toggled on, Light mode is not AMOLED)
            for (amoledPref in listOf(false, true)) {
                themeMode = "LIGHT"
                amoledMode = amoledPref
                compose.waitForIdle()
                assertNull("Light mode (${preset.name}) should not use border", buttonBorder)
                assertEquals("Screen must stay opaque", 1f, screenBackground.alpha, 0.001f)
                assertEquals("Light mode item fill must be 50% opaque", 0.5f, buttonColor.alpha, 1f / 255f)
            }

            // Dark mode (non-AMOLED)
            themeMode = "DARK"
            amoledMode = false
            compose.waitForIdle()
            assertNull("Dark mode (${preset.name}) should not use border", buttonBorder)
            assertEquals("Screen must stay opaque", 1f, screenBackground.alpha, 0.001f)
            assertEquals("Dark mode item fill must be 50% opaque", 0.5f, buttonColor.alpha, 1f / 255f)
            assertTrue(
                "Dark mode (${preset.name}) background should not be AMOLED black",
                maxChannelDelta(AndroidColor.BLACK, screenBackground.toArgb()) >= 12
            )

            // AMOLED mode
            themeMode = "DARK"
            amoledMode = true
            compose.waitForIdle()
            assertEquals(Color.Black, screenBackground)
            assertEquals("AMOLED item is transparent; only its outline remains", Color.Transparent, buttonColor)
            val border = buttonBorder
            assertNotNull("AMOLED mode (${preset.name}) must use a clear border", border)
            assertEquals(1.5.dp, border!!.width)
            assertEquals(SolidColor(expectedOutlineVariant), border.brush)
            assertTrue(
                "AMOLED border (${preset.name}) must be clearly visible on black",
                maxChannelDelta(AndroidColor.BLACK, expectedOutlineVariant.toArgb()) >= 50
            )
        }
    }
}
