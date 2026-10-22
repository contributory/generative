package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.chatoptions.ui.ChatOptionsScreen
import org.starfall.generative.features.tools.ui.ToolsEditor
import org.starfall.generative.home.drawer.ui.PromptLibraryDialog
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.PromptLibrary
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.model.ToolSettings

class ChatOptionsAndToolsEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatOptionsSeparatesHelpersAndInstructionsIntoTabs() {
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
        compose.onNodeWithText("Helpers").assertIsDisplayed()
        compose.onNodeWithText("Instructions").assertIsDisplayed()
        compose.onNodeWithText("Conversation helpers").assertIsDisplayed()
        compose.onNodeWithText("Image Generation").assertDoesNotExist()
        compose.onNodeWithText("Send File").assertDoesNotExist()

        compose.onNodeWithText("Instructions").performClick()
        compose.onNodeWithText("System Prompt & Instructions").assertIsDisplayed()
        compose.onNodeWithText("Add prompt").assertIsDisplayed()
        compose.onNodeWithText("Conversation helpers").assertDoesNotExist()
    }

    @Test
    fun instructionsTabOpensDedicatedPromptEditorAndSavesPrompt() {
        var savedLibrary = PromptLibrary()
        compose.setContent {
            TestTheme {
                ChatOptionsScreen(
                    providers = emptyList(),
                    settings = ToolSettings(),
                    promptLibrary = savedLibrary,
                    initialTab = "instructions",
                    onPromptLibraryChange = { savedLibrary = it },
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Add prompt").performClick()
        compose.onNodeWithText("New Prompt").assertIsDisplayed()
        compose.onNodeWithText("System").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Concise")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Keep answers concise")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Concise").assertIsDisplayed()
        compose.runOnIdle {
            val saved = savedLibrary.prompts.single()
            assertEquals("Concise", saved.name)
            assertEquals("Keep answers concise", saved.content)
            assertTrue(saved.id in savedLibrary.selectedIds)
        }
    }

    @Test
    fun drawerPromptDialogOnlyTogglesPromptsAndRoutesManagementAndEditing() {
        val prompt = SavedPrompt("prompt-id", "System prompt", "Answer clearly", ChatRole.SYSTEM)
        var changedLibrary = PromptLibrary(listOf(prompt), setOf(prompt.id))
        var openedInstructions = false
        var editedPromptId: String? = null
        compose.setContent {
            TestTheme {
                PromptLibraryDialog(
                    initialLibrary = changedLibrary,
                    onChange = { changedLibrary = it },
                    onOpenInstructions = { openedInstructions = true },
                    onEditPrompt = { editedPromptId = it },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Add prompt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete System prompt").assertDoesNotExist()
        compose.onNodeWithText("System prompt").performTouchInput { click() }
        compose.runOnIdle { assertTrue(changedLibrary.selectedIds.isEmpty()) }
        compose.onNodeWithContentDescription("Edit System prompt").performClick()
        compose.onNodeWithContentDescription("Open Chat Options > Instructions").performClick()
        compose.runOnIdle {
            assertEquals("prompt-id", editedPromptId)
            assertTrue(openedInstructions)
        }
    }

    @Test
    fun toolsEditorContainsMediaToolModelSelectorsAndFileTool() {
        compose.setContent {
            TestTheme {
                ToolsEditor(
                    providers = emptyList(),
                    settings = ToolSettings(),
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Feature Tools").assertIsDisplayed()
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
