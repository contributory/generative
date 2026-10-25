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
import org.starfall.generative.features.home.shared.ui.InstructionsSheetContent
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.InstructionLibrary
import org.starfall.generative.shared.model.SavedInstruction
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.ui.InstructionControls
import org.starfall.generative.shared.ui.LocalInstructionControls

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
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
        compose.onNodeWithText("System Prompt & Instructions").assertDoesNotExist()
        compose.onNodeWithText("Conversation helpers").assertIsNotDisplayed()

        compose.onNodeWithText("Helpers").performClick()
        compose.onNodeWithText("Conversation helpers").assertIsDisplayed()
        compose.onNodeWithText("Instructions").performClick()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
    }

    @Test
    fun instructionsTabOpensDedicatedInstructionEditorAndSavesInstruction() {
        var savedLibrary = InstructionLibrary()
        compose.setContent {
            TestTheme {
                ChatOptionsScreen(
                    providers = emptyList(),
                    settings = ToolSettings(),
                    instructionLibrary = savedLibrary,
                    initialTab = "instructions",
                    onInstructionLibraryChange = { savedLibrary = it },
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("instruction_name_input").assertIsDisplayed()
        compose.onNodeWithText("System").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Concise")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Keep answers concise")
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText("Concise").assertIsDisplayed()
        compose.runOnIdle {
            val saved = savedLibrary.instructions.single()
            assertEquals("Concise", saved.name)
            assertEquals("Keep answers concise", saved.content)
            assertFalse(saved.id in savedLibrary.selectedIds)
        }
    }

    @Test
    fun instructionSheetOnlyTogglesInstructionsAndRoutesManagementAndEditing() {
        val instruction = SavedInstruction("instruction-id", "System instruction", "Answer clearly", ChatRole.SYSTEM)
        var library = InstructionLibrary(listOf(instruction), setOf(instruction.id))
        var openedInstructions = false
        var editedInstructionId: String? = null
        compose.setContent {
            TestTheme {
                CompositionLocalProvider(
                    LocalInstructionControls provides InstructionControls(
                        library = library,
                        setInstructionEnabled = { id, enabled ->
                            library = library.copy(
                                selectedIds = if (enabled) library.selectedIds + id else library.selectedIds - id
                            )
                        },
                        openInstructions = { openedInstructions = true },
                        editInstruction = { editedInstructionId = it }
                    )
                ) {
                    InstructionsSheetContent()
                }
            }
        }

        compose.onNodeWithContentDescription("Add").assertDoesNotExist()
        compose.onNodeWithText("Answer clearly").assertDoesNotExist()
        compose.onNodeWithText("System instruction").performClick()
        compose.runOnIdle { assertTrue(library.selectedIds.isEmpty()) }
        compose.onNodeWithText("System instruction").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Open Chat Options > Instructions").performClick()
        compose.runOnIdle {
            assertEquals("instruction-id", editedInstructionId)
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
