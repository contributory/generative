package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.chatoptions.ui.ChatOptionsScreen
import org.starfall.generative.features.tools.ui.ToolsEditor
import org.starfall.generative.home.shared.ui.PromptInstructionsSheetContent
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.PromptLibrary
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.ui.LocalPromptControls
import org.starfall.generative.shared.ui.PromptControls

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

        compose.onNodeWithText("Conversation helpers").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Add prompt").assertIsDisplayed()
        compose.onNodeWithText("System Prompt & Instructions").assertDoesNotExist()
        compose.onNodeWithText("Conversation helpers").assertIsNotDisplayed()

        compose.onNodeWithText("Helpers").performClick()
        compose.onNodeWithText("Conversation helpers").assertIsDisplayed()
        compose.onNodeWithText("Instructions").performClick()
        compose.onNodeWithText("Add prompt").assertIsDisplayed()
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
            assertFalse(saved.id in savedLibrary.selectedIds)
        }
    }

    @Test
    fun promptSheetOnlyTogglesPromptsAndRoutesManagementAndEditing() {
        val prompt = SavedPrompt("prompt-id", "System prompt", "Answer clearly", ChatRole.SYSTEM)
        var library = PromptLibrary(listOf(prompt), setOf(prompt.id))
        var openedInstructions = false
        var editedPromptId: String? = null
        compose.setContent {
            TestTheme {
                CompositionLocalProvider(
                    LocalPromptControls provides PromptControls(
                        library = library,
                        setPromptEnabled = { id, enabled ->
                            library = library.copy(
                                selectedIds = if (enabled) library.selectedIds + id else library.selectedIds - id
                            )
                        },
                        openInstructions = { openedInstructions = true },
                        editPrompt = { editedPromptId = it }
                    )
                ) {
                    PromptInstructionsSheetContent()
                }
            }
        }

        compose.onNodeWithText("Add prompt").assertDoesNotExist()
        compose.onNodeWithText("Answer clearly").assertDoesNotExist()
        compose.onNodeWithText("System prompt").performClick()
        compose.runOnIdle { assertTrue(library.selectedIds.isEmpty()) }
        compose.onNodeWithText("System prompt").performTouchInput { longClick() }
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
