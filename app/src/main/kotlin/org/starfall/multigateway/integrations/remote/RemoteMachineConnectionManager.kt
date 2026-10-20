package org.starfall.multigateway.integrations.remote

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.starfall.multigateway.shared.model.RemoteMachine



/** Owns persistent SSH sessions and interactive/exec channels for remote-machine operations. */
internal class RemoteMachineConnectionManager(
    private val openSessionCallback: suspend (RemoteMachine, String?, RemoteSecretPrompt?) -> Pair<Session, Process?>
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, SharedConnection>()
    private val machineLocks = ConcurrentHashMap<String, Mutex>()
    private val _connectedMachineIds = MutableStateFlow<Set<String>>(emptySet())
    val connectedMachineIds: StateFlow<Set<String>> = _connectedMachineIds.asStateFlow()

    private suspend fun openSession(machine: RemoteMachine, passwordOverride: String? = null,
        askSecret: RemoteSecretPrompt? = null): Pair<Session, Process?> =
        openSessionCallback(machine, passwordOverride, askSecret)

    suspend fun execute(machine: RemoteMachine, command: String, timeoutSeconds: Int): RemoteCommandResult =
        runSshCommand(machine, command, timeoutSeconds)

    suspend fun openTerminal(machine: RemoteMachine, passwordOverride: String? = null,
        askSecret: RemoteSecretPrompt? = null): RemoteTerminal {
        val shared = acquireShared(machine.id, create = null)
        if (shared != null) {
            try {
                val (channel, input, output) = openShellChannel(shared.session, machine)
                return RemoteTerminal(shared.session, channel, input, output, null,
                    ownsSession = false, onClosed = shared::release)
            } catch (error: Throwable) {
                shared.release()
                if (error is CancellationException) throw error
                dropConnection(shared)
            }
        }
        val (session, local) = openSession(machine, passwordOverride, askSecret)
        return try {
            val (channel, input, output) = openShellChannel(session, machine)
            RemoteTerminal(session, channel, input, output, local, ownsSession = true)
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { session.disconnect() }; runCatching { local?.destroy() }
            }
            throw error
        }
    }

    fun isConnected(machineId: String): Boolean = connections[machineId]?.session?.isConnected == true
    fun disconnectMachine(machineId: String) { ioScope.launch { disconnectMachineNow(machineId) } }
    fun disconnectAllWhenIdle() { ioScope.launch {
        connections.values.toList().forEach { connection -> if (connection.requestClose()) closeConnection(connection) }
    } }
    suspend fun disconnectMachineNow(machineId: String): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        val connection = connections[machineId] ?: return@withContext false
        closeConnection(connection); true
    }

    /** One SSH session shared by tool commands and terminals of the same machine. */
    private inner class SharedConnection(
        val machineId: String,
        val session: Session,
        private val localCommand: Process?
    ) {
        private var users = 0
        private var closeOnRelease = false
        private var idleJob: Job? = null
        private var monitorJob: Job? = null

        val inUse: Boolean @Synchronized get() = users > 0

        @Synchronized
        fun acquire() {
            users++
            closeOnRelease = false
            idleJob?.cancel()
            idleJob = null
        }

        /** Closes now when nothing uses the connection, else as soon as the last user releases it. */
        @Synchronized
        fun requestClose(): Boolean {
            if (users == 0) return true
            closeOnRelease = true
            return false
        }

        /** Every acquire() needs exactly one release(); the idle countdown starts at the last one. */
        @Synchronized
        fun release() {
            if (users > 0) users--
            if (users == 0) {
                val wait = if (closeOnRelease) 0L else RemoteMachineService.IDLE_DISCONNECT_MILLIS
                idleJob?.cancel()
                idleJob = ioScope.launch {
                    delay(wait)
                    disconnectIfIdle(this@SharedConnection)
                }
            }
        }

        /** Drops the entry when the server or network closed the session, so the UI stays truthful. */
        fun startMonitor() {
            monitorJob = ioScope.launch {
                while (isActive) {
                    delay(RemoteMachineService.CONNECTION_POLL_MILLIS)
                    if (!session.isConnected) {
                        closeConnection(this@SharedConnection)
                        break
                    }
                }
            }
        }

        /** Blocking network I/O: call off the main thread. */
        fun shutdown() {
            monitorJob?.cancel()
            synchronized(this) {
                idleJob?.cancel()
                idleJob = null
            }
            runCatching { session.disconnect() }
            runCatching { localCommand?.destroy() }
        }
    }

    private suspend fun disconnectIfIdle(connection: SharedConnection) {
        machineLocks.getOrPut(connection.machineId) { Mutex() }.withLock {
            if (connection.inUse || connections[connection.machineId] !== connection) return
            closeConnection(connection)
        }
    }

    private fun closeConnection(connection: SharedConnection) {
        connections.remove(connection.machineId, connection)
        publishConnected()
        connection.shutdown()
    }

    private suspend fun dropConnection(connection: SharedConnection) =
        withContext(NonCancellable + Dispatchers.IO) { closeConnection(connection) }

    private fun publishConnected() {
        _connectedMachineIds.value = connections.keys.toSet()
    }

    private suspend fun acquireConnection(machine: RemoteMachine): SharedConnection =
        acquireShared(machine.id, create = machine) ?: error("Could not open an SSH connection.")

    /**
     * Returns the machine's live connection with one use acquired, opening it when [create] is
     * given. Without [create] it returns null if the machine is not connected.
     */
    private suspend fun acquireShared(machineId: String, create: RemoteMachine?): SharedConnection? =
        withContext(Dispatchers.IO) {
            machineLocks.getOrPut(machineId) { Mutex() }.withLock {
                var connection = connections[machineId]
                if (connection != null && !connection.session.isConnected) {
                    closeConnection(connection)
                    connection = null
                }
                if (connection == null) {
                    if (create == null) return@withLock null
                    val (session, local) = openSession(create, null, null)
                    connection = SharedConnection(machineId, session, local)
                    connections[machineId] = connection
                    connection.startMonitor()
                    publishConnected()
                }
                connection.acquire()
                connection
            }
        }

    private suspend fun openShellChannel(
        session: Session,
        machine: RemoteMachine
    ): Triple<ChannelShell, InputStream, OutputStream> = withContext(Dispatchers.IO) {
        val channel = session.openChannel("shell") as ChannelShell
        try {
            channel.setPtyType("xterm-256color")
            // Ask for a UTF-8 locale; without it servers that don't set LANG themselves run the shell
            // in the POSIX locale and mangle (or escape) every multi-byte character. Servers whose
            // sshd lacks AcceptEnv just ignore these requests.
            channel.setEnv("LANG", "C.UTF-8")
            channel.setEnv("LC_ALL", "C.UTF-8")
            // pty mode IUTF8 (opcode 42) = 1, terminated by TTY_OP_END, so the line discipline erases
            // whole characters on Backspace instead of single bytes.
            channel.setTerminalMode(byteArrayOf(42, 0, 0, 0, 1, 0))
            // JSch only wires the piped streams into the channel thread if they are requested BEFORE
            // connect(). Asking for them afterwards left the channel with no stdin reader (nothing the
            // user typed was ever sent) and could drop the first output bytes.
            val input = channel.getInputStream()
            val output = channel.getOutputStream()
            channel.connect(machine.connectTimeoutSeconds * 1_000)
            Triple(channel, input, output)
        } catch (error: Throwable) {
            runCatching { channel.disconnect() }
            throw error
        }
    }

    private class ExecChannel(
        val channel: ChannelExec,
        val input: InputStream,
        val errors: ByteArrayOutputStream
    )

    private fun openExecChannel(session: Session, machine: RemoteMachine, command: String): ExecChannel {
        val channel = session.openChannel("exec") as ChannelExec
        val errors = ByteArrayOutputStream()
        try {
            channel.setCommand(command)
            channel.setInputStream(null)
            channel.setErrStream(errors)
            val input = channel.getInputStream()
            channel.connect(machine.connectTimeoutSeconds * 1_000)
            return ExecChannel(channel, input, errors)
        } catch (error: Throwable) {
            runCatching { channel.disconnect() }
            throw error
        }
    }

    /** Runs a command over the machine's persistent connection, opening it first when needed. */
    private suspend fun runSshCommand(
        machine: RemoteMachine,
        command: String,
        timeoutSeconds: Int
    ): RemoteCommandResult {
        var retried = false
        while (true) {
            val connection = acquireConnection(machine)
            val exec = try {
                withContext(Dispatchers.IO) { openExecChannel(connection.session, machine, command) }
            } catch (error: Throwable) {
                connection.release()
                if (error is CancellationException) throw error
                // The server or network may have dropped the connection since its last use. The
                // command never started, so reconnecting once and retrying cannot repeat it.
                dropConnection(connection)
                if (retried) throw error
                retried = true
                continue
            }
            try {
                return collectExecResult(exec, timeoutSeconds)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { exec.channel.disconnect() }
                }
                connection.release()
            }
        }
    }

    private fun stopExec(channel: ChannelExec) {
        // The session stays open, so ask the server to end the process too (best effort; older
        // servers ignore the signal) before closing the channel.
        runCatching { channel.sendSignal("TERM") }
        runCatching { channel.disconnect() }
    }

    private suspend fun collectExecResult(exec: ExecChannel, timeoutSeconds: Int): RemoteCommandResult {
        // JSch's read calls block their thread and ignore coroutine cancellation, so reading runs in
        // a child coroutine and the channel is closed when cancelled. That makes the Stop button
        // take effect immediately instead of after the remote command.
        val limitSeconds = timeoutSeconds.coerceIn(1, 600)
        val collected = ByteArrayOutputStream()
        var timedOut = false
        coroutineScope {
            val reader = async(Dispatchers.IO) {
                val buffer = ByteArray(8_192)
                try {
                    while (true) {
                        val count = exec.input.read(buffer)
                        if (count < 0) break
                        synchronized(collected) { collected.write(buffer, 0, count) }
                    }
                } catch (_: java.io.IOException) {
                    // The channel was closed by cancel/timeout below.
                }
            }
            try {
                withTimeout(limitSeconds * 1_000L) { reader.await() }
            } catch (_: TimeoutCancellationException) {
                // A timeout is a tool result for the model, not a cancelled generation:
                // TimeoutCancellationException is a CancellationException and would
                // otherwise abort the whole chat turn as if the user pressed Stop.
                timedOut = true
                stopExec(exec.channel)
                reader.cancel()
            } catch (error: Throwable) {
                stopExec(exec.channel)
                reader.cancel()
                throw error
            }
        }
        val output = synchronized(collected) { collected.toString(Charsets.UTF_8.name()) }
        // The channel can finish writing stderr just after stdout reaches EOF.
        delay(20)
        val stderr = String(exec.errors.toByteArray(), Charsets.UTF_8)
        return if (timedOut) {
            RemoteCommandResult(
                output,
                (stderr + "\n[Command timed out after ${limitSeconds}s and was terminated; output above is partial.]").trim(),
                -1
            )
        } else {
            RemoteCommandResult(output, stderr, exec.channel.getExitStatus())
        }
    }

}
