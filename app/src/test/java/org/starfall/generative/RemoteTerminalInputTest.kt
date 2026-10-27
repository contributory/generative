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
        val connection = RemoteTerminalInputConnection(
            View(context), input::updateLocalDraftFromIme, input::sendKey
        )
        fun text() = output.toString("UTF-8")
    }

    @Test
    fun localImeComposesVietnameseUntilEnter() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.setComposingText("tieng", 1)
        terminal.connection.setComposingText("tiếng", 1)
        assertEquals("", terminal.text())
        assertEquals("tiếng", terminal.input.localInput)

        terminal.connection.commitText("tiếng Việt 🎉", 1)
        terminal.connection.finishComposingText()
        assertEquals("", terminal.text())
        assertEquals("tiếng Việt 🎉", terminal.input.localInput)
        terminal.connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals("tiếng Việt 🎉\r", terminal.text())
        assertEquals("", terminal.input.localInput)
    }

    @Test
    fun localImeCanCommitChinesePinyinCandidate() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.setComposingText("nihao", 1)
        terminal.connection.commitText("你好", 1)
        assertEquals("你好", terminal.input.localInput)
        assertEquals("", terminal.text())
        terminal.connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals("你好\r", terminal.text())
    }

    @Test
    fun enterFinishesCompositionBeforeSubmittingTheCommand() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.setComposingText("echo chào", 1)
        terminal.connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        assertEquals("echo chào\r", terminal.text())
    }

    @Test
    fun localImeBackspaceDoesNotSplitEmoji() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.setComposingText("á🎉", 1)
        terminal.connection.deleteSurroundingTextInCodePoints(1, 0)
        assertEquals("á", terminal.input.localInput)
        assertEquals("", terminal.text())
        terminal.connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals("á\r", terminal.text())
    }

    @Test
    fun remoteModeRejectsImeEvenWhenExplicitlyEnabled() {
        val bridge = RemoteTerminalBridge(context, TerminalInputMode.REMOTE)
        val view = RemoteTerminalView(context, bridge)
        view.terminalView.setTerminalViewClient(bridge)
        bridge.view = view.terminalView
        bridge.inputView = view

        bridge.updateImeEnabled(true)
        assertFalse(bridge.imeEnabled)
        assertFalse(view.onCheckIsTextEditor())
        assertSame(view.terminalView, view.inputTarget)
        assertNull(view.onCreateInputConnection(EditorInfo()))
        bridge.close()
    }

    @Test
    fun switchingLocalImeToRemoteDisablesCompositionAndRestoresRawTerminalInput() {
        val bridge = RemoteTerminalBridge(context, TerminalInputMode.LOCAL)
        val view = RemoteTerminalView(context, bridge)
        view.terminalView.setTerminalViewClient(bridge)
        bridge.view = view.terminalView
        bridge.inputView = view
        bridge.updateImeEnabled(true)
        assertTrue(bridge.imeEnabled)
        assertTrue(view.onCheckIsTextEditor())
        assertSame(view, view.inputTarget)
        val composing = EditorInfo()
        assertNotNull(view.onCreateInputConnection(composing))
        assertEquals(InputType.TYPE_CLASS_TEXT, composing.inputType and InputType.TYPE_MASK_CLASS)

        assertTrue(bridge.setInputMode(TerminalInputMode.REMOTE))
        assertFalse(bridge.imeEnabled)
        assertFalse(view.onCheckIsTextEditor())
        assertSame(view.terminalView, view.inputTarget)
        assertNull(view.onCreateInputConnection(EditorInfo()))
        bridge.updateImeEnabled(true)
        assertFalse(bridge.imeEnabled)

        assertTrue(bridge.setInputMode(TerminalInputMode.LOCAL))
        bridge.updateImeEnabled(true)
        assertTrue(bridge.imeEnabled)
        assertSame(view, view.inputTarget)
        bridge.close()
    }

    @Test
    fun localImeCanReplaceAlreadyCommittedVietnameseSyllablesWithoutDuplication() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.commitText("cha", 1)
        terminal.connection.deleteSurroundingText(1, 0)
        terminal.connection.setComposingText("ào", 1)
        terminal.connection.commitText("ào", 1)
        assertEquals("", terminal.text())
        assertEquals("chào", terminal.input.localInput)
        terminal.connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals("chào\r", terminal.text())
    }

    @Test
    fun localImeKeepsSpacesEditableUntilEnter() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.commitText("xin ", 1)
        terminal.connection.commitText("chào", 1)
        assertEquals("", terminal.text())
        assertEquals("xin chào", terminal.input.localInput)
        terminal.connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals("xin chào\r", terminal.text())
    }

    @Test
    fun localImeSynchronizesExternalCursorChanges() {
        val terminal = Terminal(TerminalInputMode.LOCAL, context)
        terminal.connection.commitText("abcd", 1)
        terminal.input.sendSpecial("\u001b[D")
        terminal.connection.syncLocalDraft(terminal.input.localInput, terminal.input.localCursorPosition)
        terminal.connection.commitText("X", 1)
        assertEquals("abcXd", terminal.input.localInput)
        terminal.connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals("abcXd\r", terminal.text())
    }

    @Test
    fun shiftModifierChangesNextCharacterAndModifiedArrows() {
        val terminal = Terminal(TerminalInputMode.REMOTE, context)
        terminal.input.shiftActive = true
        terminal.input.sendChar('a')
        assertEquals("A", terminal.text())
        terminal.input.shiftActive = true
        terminal.input.sendSpecial("\u001b[A")
        assertEquals("A\u001b[1;2A", terminal.text())
        assertFalse(terminal.input.shiftActive)
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
    fun preferencesKeepLocalImeChoiceButNeverActivateImeInRemoteMode() {
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
        assertFalse(preferences.remoteTerminalInputPreferences("machine").imeEnabled)
        // The Local IME preference survives the Remote mode change.
        assertTrue(preferences.getBoolean(imeKey, false))
        preferences.edit().putString(modeKey, "LOCAL").commit()
        assertTrue(preferences.remoteTerminalInputPreferences("machine").imeEnabled)
    }
}
