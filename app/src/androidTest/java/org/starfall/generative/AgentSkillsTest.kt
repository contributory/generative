package org.starfall.generative

import androidx.activity.ComponentActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
        compose.onNodeWithText("create-skill").assertIsDisplayed().performClick()
        // Bundled skills can be viewed, but must not open the editable skill form.
        compose.onNodeWithTag("agent_skill_name_input").assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun newSkillEditorShowsCompleteSkillMdAndAllowsReplacingItInOnePaste() {
        var saved: List<AgentSkill> = emptyList()
        compose.setContent {
            MaterialTheme {
                ChatOptionsScreen(
                    providers = emptyList(),
                    settings = ToolSettings(),
                    onSave = { _, _ -> },
                    onAgentSkillsChange = { saved = it },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Skills").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("agent_skill_name_input").assertDoesNotExist()
        compose.onNodeWithTag("agent_skill_description_input").assertDoesNotExist()
        compose.onNodeWithTag("agent_skill_content_input").assertDoesNotExist()
        compose.onNodeWithTag("agent_skill_document_input")
            .assertTextContains("---", substring = true)
            .assertTextContains("name: new-agent-skill", substring = true)
            .assertTextContains("description:", substring = true)
            .assertTextContains("## Instructions", substring = true)
            .assertTextContains("## Examples", substring = true)

        val replacement = """---
name: writing-helper
description: Write helpful responses
license: MIT
metadata:
  author: test
---

# Writing Helper

## Instructions
Write clearly.
""".trimIndent()
        compose.onNodeWithTag("agent_skill_document_input").performTextReplacement(replacement)
        compose.onNodeWithText("writing-helper").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save skill").performClick()
        compose.runOnIdle {
            assertTrue(saved.any { it.name == "writing-helper" &&
                it.extraFrontmatter?.contains("license: MIT") == true &&
                it.extraFrontmatter?.contains("  author: test") == true })
        }
    }

    @Test
    fun skillEditorCopiesAndPastesEntireSkillMdFromClipboard() {
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithContentDescription("Copy SKILL.md").performClick()
        compose.runOnIdle {
            val copied = clipboard.primaryClip?.getItemAt(0)?.text.toString()
            assertTrue(copied.startsWith("---\nname: new-agent-skill"))
            assertTrue(copied.contains("## Instructions"))
        }
        val pasted = "---\nname: from-clipboard\ndescription: Imported skill\n---\n\n# Instructions"
        clipboard.setPrimaryClip(ClipData.newPlainText("SKILL.md", pasted))
        compose.onNodeWithText("Paste").performClick()
        compose.onNodeWithTag("agent_skill_document_input").assertTextContains(pasted, substring = true)
    }

    @Test
    fun slashOpensSkillsMenuAndSelectionInsertsOnlySkillMention() {
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
        compose.onNode(hasSetTextAction()).assertTextEquals("/summarize")
        compose.onNodeWithTag("selected-agent-skill").assertDoesNotExist()
    }

    @Test
    fun selectedSkillMentionSendsMetadataWithoutPastingTheSkillBody() {
        val skill = AgentSkill("awesome-skill", "awesome-skill", "A large hidden instruction body")
        var sentText: String? = null
        var sentSkill: String? = null
        compose.setContent {
            MaterialTheme {
                UserInputArea(
                    isGenerating = false,
                    onSendMessage = { _, _ -> false },
                    onSendMessageWithSkill = { text, _, _, name ->
                        sentText = text
                        sentSkill = name
                        true
                    },
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
        compose.onNode(hasSetTextAction()).performTextInput("/aw Review this file")
        compose.onNodeWithTag("agent-skills-menu").assertIsDisplayed()
        compose.onNodeWithText("awesome-skill").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("/awesome-skill Review this file")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals("/awesome-skill Review this file", sentText)
            org.junit.Assert.assertEquals("awesome-skill", sentSkill)
        }
        compose.onNode(hasSetTextAction()).assertTextEquals("")
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
