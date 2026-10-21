package org.starfall.generative.features.machines.shared.ui

import android.app.Activity
import android.content.ClipData
import android.content.ContextWrapper
import android.content.ClipboardManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import org.starfall.generative.integrations.remote.RemoteTerminal

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
    // Everything the remote side printed (most recent part), replayed into a fresh emulator when the
    // terminal screen is reopened, so a session keeps its screen after the user leaves and returns.
    private val history = java.util.ArrayDeque<ByteArray>()
    private var historySize = 0

    /** Called when the user pinches: true = bigger text, false = smaller text. */
    var onZoomStep: (Boolean) -> Unit = {}
    /**
     * When on, taps and drags inside full-screen apps are sent to the server as mouse reports
     * (tap = left click, drag = wheel) even if the app never asked for mouse tracking.
     */
    var forceMouse by mutableStateOf(false)
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

    /** Replaces the local command draft from the IME-backed editor. */
    fun replaceLocalInput(value: String) {
        localInput = value
        localCursor = value.length
        historyIndex = commandHistory.size
        submittedLocalInput = null
    }

    /** Sends the current IME/editor draft as one command line. */
    fun submitEditorInput() = submitLocalInput()

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
     * Creates the silent local session TerminalView needs for its emulator lifecycle. Called again
     * whenever the screen is reopened: the new emulator is refilled from [history].
     */
    fun createSession(filesDir: java.io.File): TerminalSession {
        session?.finishIfRunning()
        emulatorReady = false
        // The renderer receives remote bytes through append() and sends input through sendRemote().
        val created = TerminalSession(
            // The array is the complete argv (no program name is prepended), so argv[0] must come
            // first; without it sh took "-c" as its name and tried to run the command as a script
            // file ("exec /system/bin/cat >/dev/null: No such file or directory", code 127).
            // stty -echo stops the pty from echoing the emulator's own query replies back on screen.
            "/system/bin/sh", filesDir.absolutePath,
            arrayOf("sh", "-c", "/system/bin/stty -echo 2>/dev/null; exec /system/bin/cat >/dev/null"),
            emptyArray(), 2_000, this
        )
        session = created
        return created
    }

    /** The screen was destroyed (user left); the remote session keeps running and recording. */
    fun detachView(released: TerminalView) {
        if (view !== released) return
        view = null
        emulatorReady = false
    }

    fun attachRemote(terminal: RemoteTerminal) {
        remote = terminal
        closed = false
        // close() shuts the sender down, but this bridge is reused when the user retries after a lost connection.
        if (sender.isShutdown) sender = newSender()
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
        if (bytes.isEmpty()) return
        submittedLocalInput = null
        record(bytes)
        if (emulatorReady) writeToEmulator(bytes)
    }

    private fun writeToEmulator(bytes: ByteArray) {
        val emulator = session?.emulator ?: return
        val terminalView = view
        // TerminalView.onScreenUpdated() snaps back to the bottom on every update, so while a command
        // kept printing (build, tail -f, ...) the user could never scroll up. When the user has
        // scrolled back, keep the same lines on screen instead: shift the top row by the number of
        // rows that scrolled into the history and just redraw.
        val scrolledBack = terminalView != null && terminalView.topRow < 0 && !emulator.isAlternateBufferActive
        emulator.append(bytes, bytes.size)
        if (scrolledBack && terminalView != null) {
            val transcript = emulator.screen.activeTranscriptRows
            terminalView.topRow = (terminalView.topRow - emulator.scrollCounter).coerceIn(-transcript, 0)
            emulator.clearScrollCounter()
            terminalView.invalidate()
        } else {
            terminalView?.onScreenUpdated()
        }
    }

    private fun record(bytes: ByteArray) {
        history.addLast(bytes)
        historySize += bytes.size
        while (historySize > MAX_HISTORY_BYTES && history.size > 1) historySize -= history.removeFirst().size
    }

    /** Jumps back to the live screen when the user types, like any terminal does. */
    private fun snapToBottom() {
        val terminalView = view ?: return
        if (terminalView.topRow != 0) {
            terminalView.topRow = 0
            terminalView.invalidate()
        }
    }

    fun close() {
        closed = true
        remote = null
        session?.finishIfRunning()
        session = null
        view = null
        sender.shutdown()
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

    /** Sends an xterm SGR (1006) mouse report; [column] and [row] are 1-based cells. */
    fun sendMouseReport(button: Int, column: Int, row: Int, pressed: Boolean) {
        sendRemote("\u001b[<$button;$column;$row${if (pressed) 'M' else 'm'}".toByteArray(Charsets.US_ASCII))
    }

    private fun sendText(value: String) {
        snapToBottom()
        if (inputMode == TerminalInputMode.LOCAL) insertLocalText(value)
        else sendRemote(value.toByteArray(Charsets.UTF_8))
    }

    private fun sendCodePoint(codePoint: Int, ctrl: Boolean, alt: Boolean) {
        snapToBottom()
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
    fun sendSpecial(
        sequence: String,
        ctrl: Boolean = ctrlActive,
        alt: Boolean = altActive,
        forceRemote: Boolean = false
    ) {
        snapToBottom()
        if (!forceRemote && inputMode == TerminalInputMode.LOCAL && sequence in setOf("\r", "\n")) {
            submitLocalInput()
            clearModifiers()
            return
        }
        if (!forceRemote && inputMode == TerminalInputMode.LOCAL && !ctrl && !alt) {
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

    private companion object {
        const val MAX_HISTORY_BYTES = 512 * 1024
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

    /**
     * Moves input focus to the terminal view so key events and the IME reach this session.
     * Switching sessions must re-focus: the previously active view kept the focus (and the IME
     * connection), which is why typed text used to land in a hidden session.
     */
    fun focusTerminalView(showKeyboard: Boolean = false) {
        val terminalView = view ?: return
        terminalView.post {
            if (closed) return@post
            terminalView.requestFocus()
            if (showKeyboard) focusAndShowKeyboard()
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
            history.forEach(::writeToEmulator)
        }
        reportSize()
    }
}

/**
 * Touch listener for TerminalView (a final class, so it cannot be subclassed). With
 * [RemoteTerminalBridge.forceMouse] on it turns single-finger gestures into mouse reports sent
 * straight to the server: tap = left click, drag = mouse wheel. It only steps in for full-screen
 * apps (alternate screen) or when the app already tracks the mouse; a plain shell keeps its local
 * scrollback. Pinch and text selection still go to TerminalView's own handling.
 */
internal class RemoteMouseTouchListener(
    context: android.content.Context,
    private val view: TerminalView,
    private val bridge: RemoteTerminalBridge
) : android.view.View.OnTouchListener {
    private var scrollRemainder = 0f

    private val detector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val (column, row) = cellAt(e)
                bridge.sendMouseReport(MOUSE_LEFT, column, row, pressed = true)
                bridge.sendMouseReport(MOUSE_LEFT, column, row, pressed = false)
                bridge.onSingleTapUp(e)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val rows = view.mEmulator?.mRows?.takeIf { it > 0 } ?: return true
                val lineHeight = view.height.toFloat() / rows
                scrollRemainder += distanceY
                val steps = (scrollRemainder / lineHeight).toInt()
                if (steps == 0) return true
                scrollRemainder -= steps * lineHeight
                val (column, row) = cellAt(e2)
                // Finger moving up (positive distance) scrolls the content up, i.e. wheel down.
                val button = if (steps > 0) MOUSE_WHEEL_DOWN else MOUSE_WHEEL_UP
                repeat(kotlin.math.abs(steps).coerceAtMost(MAX_WHEEL_STEPS)) {
                    bridge.sendMouseReport(button, column, row, pressed = true)
                }
                return true
            }
        }
    ).apply { setIsLongpressEnabled(false) }

    private fun cellAt(event: MotionEvent): Pair<Int, Int> {
        val cell = view.getColumnAndRow(event, false)
        return (cell[0] + 1) to (cell[1] + 1)
    }

    override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
        val emulator = view.mEmulator
        val takeOver = bridge.forceMouse && emulator != null && !view.isSelectingText &&
            (emulator.isAlternateBufferActive || emulator.isMouseTrackingActive) &&
            event.pointerCount == 1 && event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        if (!takeOver) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) scrollRemainder = 0f
        detector.onTouchEvent(event)
        return true
    }

    private companion object {
        const val MOUSE_LEFT = 0
        const val MOUSE_WHEEL_UP = 64
        const val MOUSE_WHEEL_DOWN = 65
        const val MAX_WHEEL_STEPS = 10
    }
}
