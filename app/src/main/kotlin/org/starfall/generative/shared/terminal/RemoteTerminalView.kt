package org.starfall.generative.shared.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.termux.view.TerminalView

/** Uses Termux's raw key input or a composing Android editor, independently of Local/Remote. */
internal class RemoteTerminalView(context: Context, private val bridge: RemoteTerminalBridge) : FrameLayout(context) {
    val terminalView = TerminalView(context, null)
    val inputTarget: View get() = if (bridge.imeEnabled) this else terminalView
    private var composingConnection: RemoteTerminalInputConnection? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // Termux paints solid black while its emulator is null, ignoring the View background.
        // Keep the renderer hidden until onEmulatorSet applies the current theme palette;
        // the FrameLayout behind it displays the actual screen background in the meantime.
        terminalView.visibility = View.INVISIBLE
        addView(terminalView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        updateInputMode()
    }

    fun updateInputMode() {
        terminalView.isFocusable = !bridge.imeEnabled
        terminalView.isFocusableInTouchMode = !bridge.imeEnabled
    }

    override fun onCheckIsTextEditor(): Boolean = bridge.imeEnabled

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        finishImeComposition()
        composingConnection = null
        if (!bridge.imeEnabled) return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return RemoteTerminalInputConnection(
            this,
            onCommit = bridge::sendImeText,
            onKey = bridge::sendImeKey,
            onComposition = { bridge.composingText = it }
        ).also { composingConnection = it }
    }

    fun finishImeComposition() {
        composingConnection?.finishComposingText()
    }

    fun sendKeyToIme(event: KeyEvent): Boolean? =
        if (bridge.imeEnabled) composingConnection?.sendKeyEvent(event) else null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        return sendKeyToIme(event) ?: bridge.sendImeKey(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK) super.onKeyUp(keyCode, event) else true
}
