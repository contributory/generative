package org.starfall.generative.shared.terminal

import android.text.Selection
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo

/**
 * Keep the entire editable line, including committed characters, so Vietnamese IMEs can revise
 * previously committed syllables instead of appending a duplicate. Local edits update the local
 * draft continuously; remote edits are sent at word boundaries (space), on Enter, or before
 * an explicit terminal control key. A space keeps the same IME connection and keyboard.
 */
internal class RemoteTerminalInputConnection(
    view: View,
    private val onCommit: (String) -> Unit,
    private val onEdit: (String, Int) -> Unit,
    private val onKey: (Int, KeyEvent) -> Boolean,
    private val onComposition: (String) -> Unit,
    private val isLocal: Boolean,
    initialText: String = "",
    initialCursor: Int = initialText.length
) : BaseInputConnection(view, true) {
    private var syncingExternal = false

    init {
        val buffer = editable
        buffer?.append(initialText)
        if (buffer != null) Selection.setSelection(buffer, initialCursor.coerceIn(0, buffer.length))
        publish()
    }

    private fun publish() {
        if (syncingExternal) return
        val buffer = editable ?: return
        if (isLocal) {
            val text = buffer.toString()
            onEdit(text, Selection.getSelectionEnd(buffer).coerceIn(0, text.length))
            return
        }

        // A Vietnamese IME may supply the space via commitText or setComposingText. Keep the
        // text editable until that boundary, then send the completed words WITH their spaces,
        // never a carriage return (the shell command must still run only when Enter is pressed).
        val pending = buffer.toString()
        val boundary = pending.lastIndexOf(' ')
        if (boundary >= 0) {
            super.finishComposingText()
            val cursor = Selection.getSelectionEnd(buffer).coerceIn(0, buffer.length)
            val complete = pending.substring(0, boundary + 1)
            buffer.delete(0, boundary + 1)
            Selection.setSelection(buffer, (cursor - boundary - 1).coerceIn(0, buffer.length))
            onComposition(buffer.toString())
            // Consecutive spaces and pasted multi-word text must preserve every separator.
            var from = 0
            for (i in complete.indices) {
                if (complete[i] == ' ') {
                    onCommit(complete.substring(from, i + 1))
                    from = i + 1
                }
            }
        } else {
            onComposition(pending)
        }
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val result = super.setComposingText(text, newCursorPosition)
        publish()
        return result
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        val result = super.setComposingRegion(start, end)
        publish()
        return result
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text?.toString() == "\n" || text?.toString() == "\r") {
            submit()
            return true
        }
        val result = super.commitText(text, newCursorPosition)
        publish()
        return result
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        val result = super.setSelection(start, end)
        publish()
        return result
    }

    override fun finishComposingText(): Boolean {
        val result = super.finishComposingText()
        publish()
        return result
    }

    /** Mirrors external local changes (extra keys, history navigation and paste) into the IME. */
    fun syncLocalDraft(text: String, cursor: Int) {
        if (!isLocal) return
        val buffer = editable ?: return
        val position = cursor.coerceIn(0, text.length)
        if (buffer.toString() == text && Selection.getSelectionEnd(buffer) == position) return
        syncingExternal = true
        try {
            buffer.replace(0, buffer.length, text)
            Selection.setSelection(buffer, position)
        } finally {
            syncingExternal = false
        }
    }

    /** Flush before a special remote key so controls keep their correct input ordering. */
    fun flushRemoteDraft() {
        if (isLocal) return
        finishComposingText()
        val buffer = editable ?: return
        val text = buffer.toString()
        buffer.clear()
        Selection.setSelection(buffer, 0)
        onComposition("")
        if (text.isNotEmpty()) onCommit(text)
    }

    private fun submit() {
        finishComposingText()
        if (!isLocal) flushRemoteDraft()
        onKey(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        val buffer = editable ?: return
        buffer.clear()
        Selection.setSelection(buffer, 0)
        publish()
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
        deleteAroundCursor(beforeLength, afterLength, inCodePoints = false)

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
        deleteAroundCursor(beforeLength, afterLength, inCodePoints = true)

    private fun deleteAroundCursor(before: Int, after: Int, inCodePoints: Boolean): Boolean {
        if (before < 0 || after < 0) return false
        val buffer = editable ?: return false
        val start = Selection.getSelectionStart(buffer).coerceIn(0, buffer.length)
        val end = Selection.getSelectionEnd(buffer).coerceIn(start, buffer.length)
        val beforeAvailable = if (inCodePoints) Character.codePointCount(buffer, 0, start) else start
        val afterAvailable = if (inCodePoints) Character.codePointCount(buffer, end, buffer.length) else buffer.length - end
        val removeBefore = minOf(before, beforeAvailable)
        val removeAfter = minOf(after, afterAvailable)
        var left = if (inCodePoints) Character.offsetByCodePoints(buffer, start, -removeBefore) else start - removeBefore
        var right = if (inCodePoints) Character.offsetByCodePoints(buffer, end, removeAfter) else end + removeAfter
        if (left > 0 && left < buffer.length && Character.isLowSurrogate(buffer[left]) &&
            Character.isHighSurrogate(buffer[left - 1])) left--
        if (right > 0 && right < buffer.length && Character.isHighSurrogate(buffer[right - 1]) &&
            Character.isLowSurrogate(buffer[right])) right++
        buffer.delete(end, right)
        buffer.delete(left, start)
        Selection.setSelection(buffer, left)
        publish()
        repeat(before - removeBefore) { sendDelete(KeyEvent.KEYCODE_DEL) }
        repeat(after - removeAfter) { sendDelete(KeyEvent.KEYCODE_FORWARD_DEL) }
        return true
    }

    private fun sendDelete(keyCode: Int) {
        onKey(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> { submit(); true }
            KeyEvent.KEYCODE_DEL -> deleteSurroundingText(1, 0)
            KeyEvent.KEYCODE_FORWARD_DEL -> deleteSurroundingText(0, 1)
            KeyEvent.KEYCODE_SPACE -> {
                if (event.isCtrlPressed || event.isAltPressed) {
                    finishComposingText()
                    flushRemoteDraft()
                    onKey(event.keyCode, event)
                } else {
                    // Some keyboards dispatch the space as a hardware key rather than commitText.
                    commitText(" ", 1)
                }
            }
            else -> {
                finishComposingText()
                flushRemoteDraft()
                onKey(event.keyCode, event)
            }
        }
    }

    override fun performEditorAction(actionCode: Int): Boolean {
        if (actionCode == EditorInfo.IME_ACTION_NONE) return false
        submit()
        return true
    }
}
