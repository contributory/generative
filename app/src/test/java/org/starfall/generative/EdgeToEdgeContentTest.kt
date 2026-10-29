package org.starfall.generative

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w480dp-h960dp-xhdpi")
class EdgeToEdgeContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun scrollablePaddingKeepsBothBarsAndExtraSpacingForSettledItems() {
        lateinit var padding: PaddingValues
        compose.setContent {
            padding = scrollableScaffoldPadding(
                PaddingValues(start = 6.dp, top = 32.dp, end = 10.dp, bottom = 24.dp),
                horizontal = 16.dp, top = 8.dp, bottom = 12.dp
            )
        }
        compose.runOnIdle {
            assertEquals(40.dp, padding.calculateTopPadding())
            assertEquals(36.dp, padding.calculateBottomPadding())
            assertEquals(22.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
            assertEquals(26.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        }
    }

    @Test
    fun scrollablePaddingPreservesStartAndEndInRtl() {
        lateinit var padding: PaddingValues
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                padding = scrollableScaffoldPadding(
                    PaddingValues(start = 20.dp, end = 6.dp), horizontal = 4.dp
                )
            }
        }
        compose.runOnIdle {
            assertEquals(24.dp, padding.calculateRightPadding(LayoutDirection.Rtl))
            assertEquals(10.dp, padding.calculateLeftPadding(LayoutDirection.Rtl))
        }
    }
}
