package org.starfall.generative

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.providers.list.ui.FolderAnimationContent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProviderFolderTouchTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun closingFolderReleasesTouchesWhileItsVisualLayerRemainsComposed() {
        val open = mutableStateOf(true)
        var rootClicks = 0
        var folderClicks = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("host")) {
                Box(Modifier.fillMaxSize().clickable { rootClicks++ })
                FolderAnimationContent(interactive = open.value, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { folderClicks++ })
                }
            }
        }
        compose.onNodeWithTag("host").performTouchInput { click(center) }
        assertEquals(1, folderClicks)
        assertEquals(0, rootClicks)
        compose.runOnIdle { open.value = false }
        compose.onNodeWithTag("host").performTouchInput { click(center) }
        assertEquals(1, folderClicks)
        assertEquals(1, rootClicks)
    }
}
