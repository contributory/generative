package org.starfall.generative

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.model.remote.TerminalInputMode
import org.starfall.generative.shared.storage.preferences.remoteTerminalImePreferenceKey
import org.starfall.generative.shared.storage.preferences.remoteTerminalInputModePreferenceKey
import org.starfall.generative.shared.storage.preferences.remoteTerminalInputPreferences
import org.starfall.generative.shared.terminal.RemoteTerminalBridge
import org.starfall.generative.shared.terminal.RemoteTerminalInput
import org.starfall.generative.shared.terminal.RemoteTerminalInputConnection
import org.starfall.generative.shared.terminal.RemoteTerminalView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = LibraryTestApplication::class)
class RemoteTerminalInputTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private class Terminal(mode: TerminalInputMode, context: Context) {
        val output = ByteArrayOutputStream()
        val input = RemoteTerminalInput(mode, output::write, {}, { false })
        var preedit = ""
        val connection = RemoteTerminalInputConnection(View(context), input::sendImeText, input::sendKey) { preedit = it }
        fun text() = output.toString("UTF-8")
    }

    @Test
    fun composingVietnameseCommitsOnceAndRoutesAccordingToMode() {
        TerminalInputMode.entries.forEach { mode ->
            val terminal = Terminal(mode, context)
            terminal.connection.setComposingText("tieng", 1)
            terminal.connection.setComposingText("tiếng", 1)
            assertEquals("tiếng", terminal.preedit)
            assertEquals("", terminal.text())
            assertEquals("", terminal.input.localInput)

            terminal.connection.commitText("tiếng Việt 🎉", 1)
            terminal.connection.finishComposingText()
            assertEquals("", terminal.preedit)
            if (mode == TerminalInputMode.LOCAL) {
                assertEquals("tiếng Việt 🎉", terminal.input.localInput)
                assertEquals("", terminal.text())
            } else {
                assertEquals("tiếng Việt 🎉", terminal.text())
                assertEquals("", terminal.input.localInput)
            }
            terminal.connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
            assertEquals("tiếng Việt 🎉\r", terminal.text())
            assertEquals("", terminal.input.localInput)
        }
    }

    @Test
    fun enterFinishesCompositionBeforeSubmittingTheCommand() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.setComposingText("echo chào", 1)
        terminal.connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        assertEquals("echo chào\r", terminal.text())
        assertEquals("", terminal.preedit)
    }

    @Test
    fun backspaceEditsCompositionWithoutDeletingRemoteTextOrSplittingEmoji() {
        val terminal = Terminal(TerminalInputMode.REMOTE, context)
        terminal.connection.setComposingText("á🎉", 1)
        terminal.connection.deleteSurroundingText(1, 0)
        assertEquals("á", terminal.preedit)
        assertEquals("", terminal.text())
        terminal.connection.setComposingText("ấ🎉", 1)
        terminal.connection.deleteSurroundingTextInCodePoints(1, 0)
        assertEquals("ấ", terminal.preedit)
        terminal.connection.finishComposingText()
        assertEquals("ấ", terminal.text())
        terminal.connection.deleteSurroundingText(1, 0)
        assertEquals("ấ\u007f", terminal.text())
    }

    @Test
    fun imeToggleChangesTheRealEditorTypeInBothModes() {
        TerminalInputMode.entries.forEach { mode ->
            val bridge = RemoteTerminalBridge(context, mode)
            val view = RemoteTerminalView(context, bridge)
            view.terminalView.setTerminalViewClient(bridge)
            bridge.view = view.terminalView
            bridge.inputView = view
            val raw = EditorInfo()
            view.inputTarget.onCreateInputConnection(raw)
            assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                raw.inputType and InputType.TYPE_MASK_VARIATION)

            bridge.updateImeEnabled(true)
            val composing = EditorInfo()
            val connection = requireNotNull(view.onCreateInputConnection(composing))
            assertEquals(InputType.TYPE_CLASS_TEXT, composing.inputType and InputType.TYPE_MASK_CLASS)
            assertEquals(0, composing.inputType and InputType.TYPE_MASK_VARIATION)
            connection.setComposingText("xin chào", 1)
            assertEquals("xin chào", bridge.composingText)
            bridge.updateImeEnabled(false)
            assertEquals("", bridge.composingText)
            assertEquals(mode, bridge.inputMode)
            bridge.close()
        }
    }

    @Test
    fun modeChangePreservesUnsentLocalDraft() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.input.sendImeText("echo chào")
        assertFalse(terminal.input.setInputMode(TerminalInputMode.REMOTE))
        assertEquals("echo chào", terminal.input.localInput)
        terminal.input.sendSpecial("\r")
        assertTrue(terminal.input.setInputMode(TerminalInputMode.REMOTE))
        terminal.input.sendImeText("ls\n")
        assertEquals("echo chào\rls\r", terminal.text())
    }

    @Test
    fun preferencesPreserveLegacyImeAndKeepNewImeIndependentOfRouting() {
        val preferences = context.getSharedPreferences("terminal_test", Context.MODE_PRIVATE)
        val modeKey = remoteTerminalInputModePreferenceKey("machine")
        val imeKey = remoteTerminalImePreferenceKey("machine")
        preferences.edit().clear().putString(modeKey, "IME").putBoolean(imeKey, false).commit()
        assertEquals(TerminalInputMode.LOCAL, preferences.remoteTerminalInputPreferences("machine").mode)
        assertTrue(preferences.remoteTerminalInputPreferences("machine").imeEnabled)
        assertEquals("LOCAL", preferences.getString(modeKey, null))
        preferences.edit().putString(modeKey, "LOCAL").putBoolean(imeKey, false).commit()
        assertFalse(preferences.remoteTerminalInputPreferences("machine").imeEnabled)
        preferences.edit().putString(modeKey, "REMOTE").putBoolean(imeKey, true).commit()
        assertEquals(TerminalInputMode.REMOTE, preferences.remoteTerminalInputPreferences("machine").mode)
        assertTrue(preferences.remoteTerminalInputPreferences("machine").imeEnabled)
    }
}
