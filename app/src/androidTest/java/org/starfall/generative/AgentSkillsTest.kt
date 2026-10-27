package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.chatoptions.ui.ChatOptionsScreen
import org.starfall.generative.features.home.chat.ui.input.UserInputArea
import org.starfall.generative.shared.model.AgentSkill
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.features.home.shared.ui.QuickActionsSheetContent

class AgentSkillsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatOptionsShowsSkillsTabAndDefaultCreateSkill() {
        compose.setContent {
            MaterialTheme {
                ChatOptionsScreen(
                    providers = emptyList(),
                    settings = ToolSettings(),
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Skills").performClick()
        compose.onNodeWithText("Create Skill").assertIsDisplayed()
    }

    @Test
    fun slashOpensSkillsMenuAboveInputAndSelectingSkillInsertsItsInstructions() {
        val skill = AgentSkill("summarize", "Summarize", "Summarize the selected text.")
        compose.setContent {
            MaterialTheme {
                UserInputArea(
                    isGenerating = false,
                    onSendMessage = { _, _ -> true },
                    onSendMedia = { false },
                    onEditMessage = { _, _, _ -> false },
                    editDraft = null,
                    onCancelEdit = {},
                    onStopGenerating = {},
                    selectedModelName = "",
                    providers = emptyList(),
                    selectedProviderId = "",
                    onSelectModel = { _, _ -> },
                    conversationReasoningEffort = null,
                    onSetReasoningEffort = {},
                    agentSkills = listOf(skill)
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("/")
        compose.onNodeWithTag("agent-skills-menu").assertIsDisplayed()
        val menuBounds = compose.onNodeWithTag("agent-skills-menu").fetchSemanticsNode().boundsInRoot
        val inputBounds = compose.onNodeWithTag("chat-input").fetchSemanticsNode().boundsInRoot
        assertTrue("Skills menu must be laid out above the input", menuBounds.bottom <= inputBounds.top + 2f)

        compose.onNodeWithText("Summarize").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("Summarize the selected text.")
    }

    @Test
    fun skillsPopupManageButtonOpensSkillsSettingsWithoutChangingDraft() {
        var openedSettings = false
        compose.setContent {
            MaterialTheme {
                UserInputArea(
                    isGenerating = false,
                    onSendMessage = { _, _ -> true },
                    onSendMedia = { false },
                    onEditMessage = { _, _, _ -> false },
                    editDraft = null,
                    onCancelEdit = {},
                    onStopGenerating = {},
                    selectedModelName = "",
                    providers = emptyList(),
                    selectedProviderId = "",
                    onSelectModel = { _, _ -> },
                    conversationReasoningEffort = null,
                    onSetReasoningEffort = {},
                    onOpenSkillsSettings = { openedSettings = true }
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("/")
        compose.onNodeWithTag("agent-skills-manage").performClick()
        compose.runOnIdle { assertTrue(openedSettings) }
        compose.onNodeWithTag("agent-skills-menu").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextEquals("/")
    }

    @Test
    fun quickActionsShowsSkillsRowBelowVideo() {
        var opened = false
        compose.setContent {
            MaterialTheme {
                QuickActionsSheetContent(
                    onPickImage = {},
                    onPickDocument = {},
                    onTakePhoto = {},
                    onCreateImage = {},
                    onCreateVideo = {},
                    onOpenInstructions = {},
                    onOpenFeatureTools = {},
                    onOpenMcpConnectors = {},
                    onDismiss = {},
                    onOpenSkills = { opened = true }
                )
            }
        }

        compose.onNodeWithText("Skills").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
