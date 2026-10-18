@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.starfall.multigateway.features.machines.shared.ui

import android.app.Activity
import android.content.ClipData
import android.content.ContextWrapper
import android.content.ClipboardManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.ByteArrayOutputStream
import org.starfall.multigateway.integrations.remote.RemoteTerminal

/**
 * Adapter between a remote byte stream and Termux's maintained ANSI terminal renderer. A tiny
 * sleeping local session is used only to give TerminalView its emulator lifecycle; remote output
 * is rendered here, while input is either buffered locally or routed to the JSch channel by mode.
 */
enum class TerminalInputMode { LOCAL, REMOTE }

internal class RemoteTerminalBridge(
    private val context: android.content.Context,
    initialInputMode: TerminalInputMode
) :
    TerminalSessionClient,
    TerminalViewClient {
    var session: TerminalSession? = null
    var view: TerminalView? = null
    private var remote: RemoteTerminal? = null
    private var closed = false
    private var emulatorReady = false
    private val pending = ByteArrayOutputStream()
    private var replyFifo: java.io.File? = null

    /** Called when the user pinches: true = bigger text, false = smaller text. */
    var onZoomStep: (Boolean) -> Unit = {}
    var ctrlActive by mutableStateOf(false)
    var altActive by mutableStateOf(false)
    var inputMode by mutableStateOf(initialInputMode)
        private set
    var localInput by mutableStateOf("")
        private set
    var submittedLocalInput by mutableStateOf<String?>(null)
        private set
    private var localCursor = 0
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = 0
    private var historyDraft = ""

    val hasLocalDraft: Boolean get() = localInput.isNotEmpty()

    fun setInputMode(mode: TerminalInputMode): Boolean {
        if (mode == TerminalInputMode.REMOTE && hasLocalDraft) return false
        inputMode = mode
        if (mode == TerminalInputMode.REMOTE) submittedLocalInput = null
        clearModifiers()
        return true
    }

    fun localDraftForDisplay(): String? {
        val submitted = submittedLocalInput
        if (submitted == null && localInput.isEmpty()) return null
        return buildString {
            submitted?.let(::append)
            if (localInput.isNotEmpty()) {
                if (submitted != null) append("  ·  ")
                append(localInput, 0, localCursor)
                append('▏')
                append(localInput.substring(localCursor))
            }
        }
    }

    private fun insertLocalText(value: String) {
        if (value.isEmpty()) return
        localInput = localInput.substring(0, localCursor) + value + localInput.substring(localCursor)
        localCursor += value.length
        historyIndex = commandHistory.size
    }

    private fun deleteLocalPrevious() {
        if (localCursor == 0) return
        val previous = Character.offsetByCodePoints(localInput, localCursor, -1)
        localInput = localInput.removeRange(previous, localCursor)
        localCursor = previous
        historyIndex = commandHistory.size
    }

    private fun deleteLocalNext() {
        if (localCursor >= localInput.length) return
        val next = Character.offsetByCodePoints(localInput, localCursor, 1)
        localInput = localInput.removeRange(localCursor, next)
        historyIndex = commandHistory.size
    }

    private fun moveLocalCursor(direction: Int) {
        if (direction < 0 && localCursor == 0) return
        if (direction > 0 && localCursor == localInput.length) return
        localCursor = Character.offsetByCodePoints(localInput, localCursor, direction)
            .coerceIn(0, localInput.length)
    }

    private fun moveLocalHistory(direction: Int) {
        if (commandHistory.isEmpty()) return
        if (direction < 0) {
            if (historyIndex == commandHistory.size) historyDraft = localInput
            historyIndex = (historyIndex - 1).coerceAtLeast(0)
        } else {
            historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size)
        }
        localInput = if (historyIndex == commandHistory.size) historyDraft else commandHistory[historyIndex]
        localCursor = localInput.length
    }

    private fun clearLocalInput() {
        localInput = ""
        localCursor = 0
        historyIndex = commandHistory.size
    }

    private fun submitLocalInput() {
        val line = localInput
        submittedLocalInput = line.takeIf { it.isNotEmpty() }
        if (line.isNotBlank() && commandHistory.lastOrNull() != line) commandHistory += line
        historyIndex = commandHistory.size
        historyDraft = ""
        clearLocalInput()
        sendRemote((line + "\r").toByteArray(Charsets.UTF_8))
    }

    private fun handleLocalControl(codePoint: Int, alt: Boolean) {
        if (alt) {
            insertLocalText(String(Character.toChars(codePoint)))
            return
        }
        val controlCharacter = if (codePoint in 1..26) 'a'.code + codePoint - 1 else codePoint
        when (controlCharacter.toChar().lowercaseChar()) {
            'a' -> localCursor = 0
            'e' -> localCursor = localInput.length
            'u' -> clearLocalInput()
            'w' -> {
                while (localCursor > 0 && localInput[localCursor - 1].isWhitespace()) deleteLocalPrevious()
                while (localCursor > 0 && !localInput[localCursor - 1].isWhitespace()) deleteLocalPrevious()
            }
            'c' -> {
                clearLocalInput()
                sendRemote(byteArrayOf(3))
            }
            'd' -> {
                if (localCursor < localInput.length) deleteLocalNext()
                else if (localInput.isEmpty()) sendRemote(byteArrayOf(4))
            }
            else -> sendRemote(codePointBytes(codePoint, ctrl = true, alt = false))
        }
    }

    /**
     * Creates the tiny local session TerminalView needs for its emulator. Everything the emulator
     * itself writes (cursor reports, mouse events, scroll arrows in vim/tmux...) goes to this
     * session's pty; a raw, non-echoing `cat` forwards it through a FIFO to the remote channel.
     */
    fun createSession(filesDir: java.io.File): TerminalSession {
        val script = runCatching {
            val fifo = java.io.File(filesDir, "remote-machines/term-${System.nanoTime()}.in")
            fifo.parentFile?.mkdirs()
            fifo.delete()
            android.system.Os.mkfifo(fifo.absolutePath, 384)
            replyFifo = fifo
            startReplyReader(fifo)
            "stty raw -echo 2>/dev/null; exec cat > '${fifo.absolutePath}'"
        }.getOrElse { "stty raw -echo 2>/dev/null; exec cat > /dev/null" }
        // A null working directory triggers a JNI GetStringUTFChars abort on Android 11.
        val created = TerminalSession(
            // TerminalSession passes these as arguments to shellPath, so -c must be the first arg.
            // A leading "sh" makes the shell try to open a script file named "sh" and the FIFO
            // forwarder never starts.
            "/system/bin/sh", filesDir.absolutePath, arrayOf("-c", script), emptyArray(), 2_000, this
        )
        session = created
        return created
    }

    private fun startReplyReader(fifo: java.io.File) {
        Thread {
            runCatching {
                java.io.FileInputStream(fifo).use { input ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sendRemote(buffer.copyOf(count))
                    }
                }
            }
            fifo.delete()
        }.apply { isDaemon = true; name = "remote-terminal-replies" }.start()
    }

    fun attachRemote(terminal: RemoteTerminal) {
        remote = terminal
        closed = false
        // close() shuts the sender down, but this bridge is reused when the user retries after a lost connection.
        if (sender.isShutdown) sender = newSender()
        flushPending()
        reportSize()
    }

    /** Sends the emulator's current size to the remote pty, off the main thread. */
    private fun reportSize() {
        val terminal = remote ?: return
        val emulator = session?.emulator ?: return
        val columns = emulator.mColumns
        val rows = emulator.mRows
        Thread { terminal.resize(columns, rows) }.apply { isDaemon = true }.start()
    }

    fun append(bytes: ByteArray) {
        if (closed) return
        submittedLocalInput = null
        val emulator = session?.emulator
        if (!emulatorReady || emulator == null) {
            pending.write(bytes)
            return
        }
        emulator.append(bytes, bytes.size)
        view?.onScreenUpdated()
    }

    fun close() {
        closed = true
        remote = null
        session?.finishIfRunning()
        session = null
        view = null
        replyFifo?.delete()
        replyFifo = null
        sender.shutdown()
    }

    private fun flushPending() {
        if (!emulatorReady || pending.size() == 0) return
        val bytes = pending.toByteArray()
        pending.reset()
        append(bytes)
    }

    // Socket writes throw NetworkOnMainThreadException on Android's main thread, and keys/paste
    // arrive on the main thread. They used to be swallowed by runCatching, so nothing typed or
    // pasted ever reached the server. A single-thread executor keeps the writes ordered.
    @Volatile private var sender = newSender()

    private fun newSender() = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "remote-terminal-input").apply { isDaemon = true }
    }

    private fun sendRemote(bytes: ByteArray) {
        if (closed) return
        val output = remote?.output ?: return
        try {
            sender.execute {
                runCatching {
                    output.write(bytes)
                    output.flush()
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun sendText(value: String) {
        if (inputMode == TerminalInputMode.LOCAL) insertLocalText(value)
        else sendRemote(value.toByteArray(Charsets.UTF_8))
    }

    private fun sendCodePoint(codePoint: Int, ctrl: Boolean, alt: Boolean) {
        if (inputMode == TerminalInputMode.LOCAL) {
            if (ctrl) {
                handleLocalControl(codePoint, alt)
                clearModifiers()
                return
            }
            when (codePoint) {
                '\n'.code, '\r'.code -> submitLocalInput()
                '\b'.code, 0x7f -> deleteLocalPrevious()
                0x1b -> clearLocalInput()
                else -> insertLocalText(String(Character.toChars(codePoint)))
            }
            clearModifiers()
            return
        }
        sendRemote(codePointBytes(codePoint, ctrl, alt))
    }

    private fun codePointBytes(codePoint: Int, ctrl: Boolean, alt: Boolean): ByteArray {
        var value = codePoint
        if (value == '\n'.code) value = '\r'.code
        if (ctrl) {
            value = when {
                value in 'a'.code..'z'.code -> value - 'a'.code + 1
                value in 'A'.code..'Z'.code -> value - 'A'.code + 1
                value == ' '.code || value == '2'.code -> 0
                value == '['.code || value == '3'.code -> 27
                value == '\\'.code || value == '4'.code -> 28
                value == ']'.code || value == '5'.code -> 29
                value == '^'.code || value == '6'.code -> 30
                value == '_'.code || value == '7'.code || value == '/'.code -> 31
                else -> value
            }
        }
        val prefix = if (alt) "\u001b" else ""
        return (prefix + String(Character.toChars(value))).toByteArray(Charsets.UTF_8)
    }

    fun sendChar(char: Char) {
        sendCodePoint(char.code, ctrlActive, altActive)
        clearModifiers()
    }

    private fun clearModifiers() {
        ctrlActive = false
        altActive = false
    }

    /** Sends an escape sequence, adding xterm modifier parameters / application cursor mode. */
    fun sendSpecial(sequence: String, ctrl: Boolean = ctrlActive, alt: Boolean = altActive) {
        if (inputMode == TerminalInputMode.LOCAL && sequence in setOf("\r", "\n")) {
            submitLocalInput()
            clearModifiers()
            return
        }
        if (inputMode == TerminalInputMode.LOCAL && !ctrl && !alt) {
            when (sequence) {
                "\r", "\n" -> submitLocalInput()
                "\u0009" -> insertLocalText("\t")
                "\u007f", "\b" -> deleteLocalPrevious()
                "\u001b[3~" -> deleteLocalNext()
                "\u001b[D" -> moveLocalCursor(-1)
                "\u001b[C" -> moveLocalCursor(1)
                "\u001b[H" -> localCursor = 0
                "\u001b[F" -> localCursor = localInput.length
                "\u001b[A" -> moveLocalHistory(-1)
                "\u001b[B" -> moveLocalHistory(1)
                "\u001b" -> clearLocalInput()
                else -> sendRemote(sequence.toByteArray(Charsets.UTF_8))
            }
            clearModifiers()
            return
        }
        val match = Regex("^\u001b\\[(\\d*)([A-Za-z~])$").matchEntire(sequence)
        val out = when {
            match != null && (ctrl || alt) -> {
                val number = match.groupValues[1].ifEmpty { "1" }
                val modifier = 1 + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
                "\u001b[$number;$modifier${match.groupValues[2]}"
            }
            match != null && match.groupValues[1].isEmpty() && match.groupValues[2] in "ABCD" &&
                view?.mEmulator?.isCursorKeysApplicationMode == true -> "\u001bO${match.groupValues[2]}"
            alt && sequence.length == 1 -> "\u001b$sequence"
            else -> sequence
        }
        sendRemote(out.toByteArray(Charsets.UTF_8))
        clearModifiers()
    }

    private fun sendKey(keyCode: Int, event: KeyEvent): Boolean {
        val sequence = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
            else -> null
        }
        val ctrl = event.isCtrlPressed || ctrlActive
        val alt = event.isAltPressed || altActive
        if (sequence != null) {
            sendSpecial(sequence, ctrl, alt)
            return true
        }
        val unicode = event.unicodeChar
        if (unicode != 0) {
            sendCodePoint(unicode, ctrl, alt)
            clearModifiers()
        }
        return true
    }

    // TerminalSessionClient
    override fun onTextChanged(changedSession: TerminalSession) { view?.onScreenUpdated() }
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        (context.getSystemService(ClipboardManager::class.java))?.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(::sendText)
    }
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String, message: String) = Unit
    override fun logWarn(tag: String, message: String) = Unit
    override fun logInfo(tag: String, message: String) = Unit
    override fun logDebug(tag: String, message: String) = Unit
    override fun logVerbose(tag: String, message: String) = Unit
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
    override fun logStackTrace(tag: String, e: Exception) = Unit

    // TerminalViewClient
    // Pinch changes the font size (like Termux) instead of scaling the view.
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            onZoomStep(scale > 1f)
            return 1.0f
        }
        return scale
    }

    private fun windowFor(view: android.view.View): android.view.Window? {
        var current = view.context
        while (true) {
            if (current is Activity) return current.window
            if (current !is ContextWrapper) return null
            current = current.baseContext
        }
    }

    private fun focusAndShowKeyboard() {
        val terminalView = view ?: return
        terminalView.post {
            if (closed) return@post
            terminalView.requestFocus()
            windowFor(terminalView)?.let { window ->
                WindowCompat.getInsetsController(window, terminalView).show(WindowInsetsCompat.Type.ime())
            }
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(terminalView, 0)
        }
    }

    fun toggleKeyboard() {
        val terminalView = view ?: return
        val visible = ViewCompat.getRootWindowInsets(terminalView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (visible) {
            val window = windowFor(terminalView)
            if (window != null) {
                WindowCompat.getInsetsController(window, terminalView).hide(WindowInsetsCompat.Type.ime())
            } else {
                context.getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(terminalView.windowToken, 0)
            }
        } else {
            focusAndShowKeyboard()
        }
    }

    override fun onSingleTapUp(e: MotionEvent) { focusAndShowKeyboard() }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = sendKey(keyCode, e)
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = true
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = ctrlActive
    override fun readAltKey(): Boolean = altActive
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        sendCodePoint(codePoint, ctrlDown, altActive)
        clearModifiers()
        return true
    }
    // TerminalView calls this on EVERY size change (e.g. when the keyboard shows or hides). Reset
    // the local renderer once, but don't focus the view or open the IME during terminal setup.
    override fun onEmulatorSet() {
        val first = !emulatorReady
        emulatorReady = true
        if (first) {
            session?.reset()
            flushPending()
        }
        reportSize()
    }
}
