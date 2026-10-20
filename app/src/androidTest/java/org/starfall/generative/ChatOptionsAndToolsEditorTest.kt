package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.chatoptions.ui.ChatOptionsScreen
import org.starfall.generative.features.tools.ui.ToolsEditorScreen
import org.starfall.generative.shared.model.ToolSettings

class ChatOptionsAndToolsEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatOptionsOnlyShowsConversationHelpers() {
        compose.setContent {
            TestTheme {
                ChatOptionsScreen(
                    providers = emptyList(),
                    settings = ToolSettings(),
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Chat Options").assertIsDisplayed()
        compose.onNodeWithText("Conversation helpers").assertIsDisplayed()
        compose.onNodeWithText("Image Generation").assertDoesNotExist()
        compose.onNodeWithText("Send File").assertDoesNotExist()
    }

    @Test
    fun toolsEditorContainsMediaToolModelSelectorsAndFileTool() {
        compose.setContent {
            TestTheme {
                ToolsEditorScreen(
                    providers = emptyList(),
                    settings = ToolSettings(),
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Tools Editor").assertIsDisplayed()
        compose.onNodeWithText("Image Generation").assertIsDisplayed()
        compose.onNodeWithText("Video Generation").assertIsDisplayed()
        compose.onNodeWithText("Send File").assertIsDisplayed()
        compose.onNodeWithText("Conversation helpers").assertDoesNotExist()
    }
}

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
