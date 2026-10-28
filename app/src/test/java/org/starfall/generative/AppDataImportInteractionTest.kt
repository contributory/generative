package org.starfall.generative

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.appdata.ui.AppDataScreen
import org.starfall.generative.shared.theme.GenerativeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class AppDataImportInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importRunsWithoutBlockingNavigationOrTheDataControlsScreen() {
        var backCalls = 0
        compose.setContent {
            GenerativeTheme(darkTheme = false, amoledMode = false) {
                AppDataScreen(
                    conversationCount = 5,
                    providerCount = 2,
                    onClearAllConversations = {},
                    onResetAllData = {},
                    importInProgress = true,
                    onBack = { backCalls++ }
                )
            }
        }

        compose.onNodeWithText("Importing in the background. You can leave this screen.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Import Complete Backup (ZIP)").assertIsNotEnabled()
        // Changes that could corrupt a full restore stay unavailable, but navigation does not.
        compose.onNodeWithText("Clear All Chat History").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test fun importActionsBecomeAvailableAgainWhenTheBackgroundJobFinishes() {
        var importing by mutableStateOf(true)
        compose.setContent {
            GenerativeTheme(darkTheme = false, amoledMode = false) {
                AppDataScreen(0, 0, {}, {}, importInProgress = importing, onBack = {})
            }
        }
        compose.runOnIdle { importing = false }
        compose.onNodeWithText("Importing in the background. You can leave this screen.")
            .assertDoesNotExist()
        compose.onNodeWithText("Import Complete Backup (ZIP)").performScrollTo().assertIsEnabled()
    }
}
