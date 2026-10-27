package org.starfall.generative.shared.terminal

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
        // Termux's text-selection toolbar uses More -> View.showContextMenu(). It stores the
        // selected text until the menu closes, so add our action at precisely that point.
        terminalView.setOnCreateContextMenuListener { menu, _, _ ->
            val url = terminalBrowserUrl(terminalView.storedSelectedText)
            if (url != null) {
                menu.add(org.starfall.generative.R.string.remote_machine_terminal_open_browser)
                    .setOnMenuItemClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            // No browser installed.
                        } catch (_: SecurityException) {
                            // The device does not permit this link to open.
                        }
                        terminalView.unsetStoredSelectedText()
                        true
                    }
            }
        }
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
        // Single-line editor + Send action: some keyboards do not emit Enter for multiline text.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_ACTION_SEND or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        val local = bridge.inputMode.usesLocalInput
        outAttrs.initialSelStart = if (local) bridge.localCursorForIme else bridge.composingText.length
        outAttrs.initialSelEnd = outAttrs.initialSelStart
        return RemoteTerminalInputConnection(
            this,
            onCommit = bridge::sendImeText,
            onEdit = bridge::updateLocalImeDraft,
            onKey = bridge::sendImeKey,
            onComposition = { bridge.composingText = it },
            isLocal = local,
            initialText = if (local) bridge.localInputForIme else bridge.composingText,
            initialCursor = if (local) bridge.localCursorForIme else bridge.composingText.length
        ).also { composingConnection = it }
    }

    fun restartImeConnection() {
        finishImeComposition()
        composingConnection = null
        if (bridge.imeEnabled && hasFocus()) {
            context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.restartInput(this)
        }
    }

    fun finishImeComposition() {
        composingConnection?.finishComposingText()
    }

    fun flushRemoteImeDraft() {
        composingConnection?.flushRemoteDraft()
    }

    fun syncLocalImeDraft(text: String, cursor: Int) {
        composingConnection?.syncLocalDraft(text, cursor)
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

/** Only open explicit web links (or the common www. shorthand), never arbitrary commands. */
internal fun terminalBrowserUrl(selection: String?): String? {
    val value = selection?.trim()?.trimEnd('.', ',', ';', ')', ']', '}', '!', '?') ?: return null
    if (value.isBlank() || value.any { it.isWhitespace() }) return null
    val url = if (value.startsWith("www.", ignoreCase = true)) "https://$value" else value
    val uri = Uri.parse(url)
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return url
}
