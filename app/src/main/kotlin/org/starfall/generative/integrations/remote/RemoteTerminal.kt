package org.starfall.generative.integrations.remote

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal const val REMOTE_TERMINAL_UI_PREFERENCES = "remote_terminal_ui"
internal fun remoteTerminalInputModePreferenceKey(machineId: String) = "input_mode:$machineId"

/** Result returned by an SSH command execution. */
data class RemoteCommandResult(
    val output: String,
    val error: String = "",
    val exitCode: Int = 0
) {
    val isError: Boolean get() = exitCode != 0
}

data class RemoteTerminalInfo(
    val id: String,
    val isConnected: Boolean
)

/** A secret requested by the SSH server while connecting. */
data class RemoteSecretRequest(val passphrase: Boolean, val prompt: String)

/** Called only when SSH requests a password or key passphrase that is not already saved. */
typealias RemoteSecretPrompt = suspend (RemoteSecretRequest) -> String?

/**
 * A connected interactive shell. The owner must close it when the terminal screen is disposed.
 * A terminal that reuses a shared SSH connection only closes its own channel; one that opened a
 * private connection ([ownsSession]) closes the whole connection as well.
 */
class RemoteTerminal internal constructor(
    val id: String,
    private val session: Session,
    private val channel: ChannelShell,
    val input: InputStream,
    val output: OutputStream,
    private val ownsSession: Boolean = false,
    private val onClosed: () -> Unit = {}
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    internal val createdAtNanos: Long = System.nanoTime()

    val isOpen: Boolean get() = !closed.get() && channel.isConnected

    /** Exit status of the remote shell, or -1 while it is running / if the connection was dropped. */
    val exitStatus: Int get() = channel.exitStatus

    /** Tells the server the terminal size changed (blocking network I/O; call off the main thread). */
    fun resize(columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0 || !channel.isConnected) return
        runCatching { channel.setPtySize(columns, rows, 0, 0) }
    }

    /**
     * Disconnects the channel (and the SSH session when this terminal owns it). JSch writes to the
     * socket when disconnecting, which throws NetworkOnMainThreadException on the UI thread
     * (previously swallowed, leaving the session open), so the work always runs on a background
     * thread. Safe to call repeatedly.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        Thread {
            runCatching { channel.disconnect() }
            if (ownsSession) {
                runCatching { session.disconnect() }
            }
            runCatching { onClosed() }
        }.apply { isDaemon = true; name = "remote-terminal-disconnect" }.start()
    }
}
