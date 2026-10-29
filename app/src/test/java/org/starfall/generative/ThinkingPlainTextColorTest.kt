package org.starfall.generative

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.ui.ThinkingPlainText

/** Thinking displays raw text with the same semantic color throughout updates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = LibraryTestApplication::class)
class ThinkingPlainTextColorTest {
    @get:Rule val compose = createComposeRule()

    private fun assertTextColor(text: String, expected: Color) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout -> getLayout(layouts) }
        assertTrue("The rendered Thinking text must expose its resolved style", layouts.isNotEmpty())
        layouts.forEach { assertEquals(expected, it.layoutInput.style.color) }
    }

    private fun verifyColors(dark: Boolean) {
        val reasoning = mutableStateOf("Considering the first option.")
        val variant = if (dark) Color(0xFFB8C7DA) else Color(0xFF35445A)
        val colors = if (dark) darkColorScheme(onSurfaceVariant = variant)
            else lightColorScheme(onSurfaceVariant = variant)
        val expected = variant.copy(alpha = 0.85f)
        compose.setContent {
            MaterialTheme(colorScheme = colors) {
                // Deliberately different from the Thinking theme to detect inherited fallback color.
                CompositionLocalProvider(LocalContentColor provides Color.Red) {
                    ThinkingPlainText(reasoning.value)
                }
            }
        }
        assertTextColor(reasoning.value, expected)
        compose.waitForIdle()
        assertTextColor(reasoning.value, expected)
        compose.runOnIdle { reasoning.value = "Considering the first option and the second option." }
        assertTextColor(reasoning.value, expected)
        compose.waitForIdle()
        assertTextColor(reasoning.value, expected)
    }

    @Test fun lightThemeKeepsThinkingColorDuringUpdates() = verifyColors(dark = false)
    @Test fun darkThemeKeepsThinkingColorDuringUpdates() = verifyColors(dark = true)

    @Test fun thinkingPreservesLiteralMarkdownCharacters() {
        val source = "**bold**\n# heading\n`code` [link](https://example.org)"
        compose.setContent { MaterialTheme { ThinkingPlainText(source) } }
        compose.onNodeWithText(source, useUnmergedTree = true).assertExists()
    }
}
