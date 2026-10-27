package org.starfall.generative.shared.terminal

import android.text.Selection
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo

/** Keeps IME preedit text editable until committed, before routing it to either input mode. */
internal class RemoteTerminalInputConnection(
    view: View,
    private val onCommit: (String) -> Unit,
    private val onKey: (Int, KeyEvent) -> Boolean,
    private val onComposition: (String) -> Unit
) : BaseInputConnection(view, true) {
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val handled = super.setComposingText(text, newCursorPosition)
        updateComposition()
        return handled
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        val handled = super.setComposingRegion(start, end)
        updateComposition()
        return handled
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        super.commitText(text, newCursorPosition)
        flush()
        return true
    }

    override fun finishComposingText(): Boolean {
        super.finishComposingText()
        flush()
        return true
    }

    private fun flush() {
        val buffer = editable ?: return
        val text = buffer.toString()
        buffer.clear()
        Selection.setSelection(buffer, 0)
        onComposition("")
        if (text.isNotEmpty()) onCommit(text)
    }

    private fun updateComposition() = onComposition(editable?.toString().orEmpty())

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
        deleteAroundCursor(beforeLength, afterLength, inCodePoints = false)

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
        deleteAroundCursor(beforeLength, afterLength, inCodePoints = true)

    private fun deleteAroundCursor(before: Int, after: Int, inCodePoints: Boolean): Boolean {
        if (before < 0 || after < 0) return false
        val buffer = editable ?: return false
        val start = Selection.getSelectionStart(buffer).coerceIn(0, buffer.length)
        val end = Selection.getSelectionEnd(buffer).coerceIn(start, buffer.length)
        val availableBefore = if (inCodePoints) Character.codePointCount(buffer, 0, start) else start
        val availableAfter = if (inCodePoints) Character.codePointCount(buffer, end, buffer.length) else buffer.length - end
        val removeBefore = minOf(before, availableBefore)
        val removeAfter = minOf(after, availableAfter)
        var left = if (inCodePoints) Character.offsetByCodePoints(buffer, start, -removeBefore) else start - removeBefore
        var right = if (inCodePoints) Character.offsetByCodePoints(buffer, end, removeAfter) else end + removeAfter
        // Never leave half a supplementary Unicode character in the composition buffer.
        if (left > 0 && left < buffer.length && Character.isLowSurrogate(buffer[left]) &&
            Character.isHighSurrogate(buffer[left - 1])) left--
        if (right > 0 && right < buffer.length && Character.isHighSurrogate(buffer[right - 1]) &&
            Character.isLowSurrogate(buffer[right])) right++
        buffer.delete(end, right)
        buffer.delete(left, start)
        Selection.setSelection(buffer, left)
        updateComposition()
        repeat(before - removeBefore) { sendDelete(KeyEvent.KEYCODE_DEL) }
        repeat(after - removeAfter) { sendDelete(KeyEvent.KEYCODE_FORWARD_DEL) }
        return true
    }

    private fun sendDelete(keyCode: Int) {
        onKey(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.keyCode == KeyEvent.KEYCODE_DEL) return deleteSurroundingText(1, 0)
        if (event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) return deleteSurroundingText(0, 1)
        finishComposingText()
        return onKey(event.keyCode, event)
    }

    override fun performEditorAction(actionCode: Int): Boolean {
        if (actionCode != EditorInfo.IME_ACTION_NONE) {
            finishComposingText()
            return onKey(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }
        return false
    }
}
