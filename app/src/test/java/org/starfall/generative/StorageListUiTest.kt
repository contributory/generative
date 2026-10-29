package org.starfall.generative

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.storage.ui.StorageScreen
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.theme.GenerativeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class StorageListUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun gridKeepsLongPressAndMultipleSelection() {
        temporary.newFile("first.txt").writeText("first")
        temporary.newFile("second.txt").writeText("second")
        val store = ToolFiles(temporary.root)
        compose.setContent {
            GenerativeTheme(darkTheme = false, amoledMode = false) {
                StorageScreen(store, onBack = {})
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("first.txt").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Switch to Grid View").performClick()
        compose.onNodeWithText("first.txt").performTouchInput { longClick() }
        compose.onNodeWithText("1 Selected").assertIsDisplayed()
        compose.onNodeWithText("second.txt").performClick()
        compose.onNodeWithText("2 Selected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear selection").performClick()
        compose.onNodeWithText("Select").assertIsDisplayed()
    }
}
