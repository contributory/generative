package org.starfall.generative.integrations.remote

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.starfall.generative.shared.model.REMOTE_SFTP_HOME
import org.starfall.generative.shared.model.RemoteMachine

/**
 * Lazily opens the SSH connections used by the SFTP DocumentsProvider.
 *
 * Nothing connects until the provider asks for a lease. The SSH session is shared by the short
 * provider operations of one machine and is disconnected after [IDLE_DISCONNECT_MILLIS] without a
 * lease, so browsing files never leaves a permanent background connection running.
 */
internal class RemoteSftpSessionPool(
    private val openSession: suspend (RemoteMachine) -> Session
) {
    private class Entry(
        val machineId: String,
        val fingerprint: Int,
        val session: Session
    ) {
        var users: Int = 0
        var retired: Boolean = false
        var closed: Boolean = false
        var idleJob: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private val machineLocks = HashMap<String, Mutex>()

    private fun lockFor(machineId: String): Mutex = synchronized(lock) {
        machineLocks.getOrPut(machineId) { Mutex() }
    }

    suspend fun acquire(machine: RemoteMachine): RemoteSftpLease = lockFor(machine.id).withLock {
        val fingerprint = machine.copy(sortOrder = 0).hashCode()
        val existing = synchronized(lock) {
            val current = entries[machine.id]
            if (current != null && current.fingerprint == fingerprint && !current.retired &&
                current.session.isConnected
            ) {
                current.users++
                current.idleJob?.cancel()
                current.idleJob = null
                current
            } else {
                current?.let { retireLocked(it) }
                null
            }
        }
        val entry = existing ?: openEntry(machine, fingerprint)
        try {
            openLease(entry, machine)
        } catch (error: Throwable) {
            discard(entry)
            throw error
        }
    }

    private suspend fun openEntry(machine: RemoteMachine, fingerprint: Int): Entry {
        val session = openSession(machine)
        val entry = Entry(machine.id, fingerprint, session)
        synchronized(lock) {
            entries[machine.id]?.let { retireLocked(it) }
            entries[machine.id] = entry
            entry.users = 1
        }
        return entry
    }

    private suspend fun openLease(entry: Entry, machine: RemoteMachine): RemoteSftpLease =
        withContext(Dispatchers.IO) {
            val channel = entry.session.openChannel("sftp") as ChannelSftp
            try {
                channel.connect(machine.connectTimeoutSeconds * 1_000)
                val root = resolveRoot(channel, machine.sftpDirectory)
                RemoteSftpLease(channel, root) { release(entry) }
            } catch (error: Throwable) {
                runCatching { channel.disconnect() }
                throw error
            }
        }

    private fun resolveRoot(channel: ChannelSftp, configured: String): String {
        val requested = configured.trim().ifBlank { REMOTE_SFTP_HOME }
        val path = when {
            requested == REMOTE_SFTP_HOME || requested == "~" -> channel.getHome()
            requested.startsWith("$REMOTE_SFTP_HOME/") ->
                channel.getHome().trimEnd('/') + requested.removePrefix(REMOTE_SFTP_HOME)
            requested.startsWith("~/") -> channel.getHome().trimEnd('/') + requested.removePrefix("~")
            else -> requested
        }
        channel.cd(path)
        return channel.pwd().trimEnd('/').ifBlank { "/" }
    }

    private fun discard(entry: Entry) {
        synchronized(lock) {
            if (entry.users > 0) entry.users--
            entry.retired = true
            entry.idleJob?.cancel()
            entry.idleJob = null
            if (entry.users == 0) closeLocked(entry)
        }
    }

    private fun release(entry: Entry) {
        synchronized(lock) {
            if (entry.users > 0) entry.users--
            if (entry.users > 0) return
            if (entry.retired) {
                closeLocked(entry)
                return
            }
            entry.idleJob?.cancel()
            entry.idleJob = scope.launch {
                delay(IDLE_DISCONNECT_MILLIS)
                synchronized(lock) {
                    if (entry.users == 0 && !entry.retired && entries[entry.machineId] === entry) {
                        closeLocked(entry)
                    }
                }
            }
        }
    }

    /** Drops a machine's connection after its settings changed or the machine was removed. */
    fun invalidate(machineId: String) {
        synchronized(lock) {
            entries.remove(machineId)?.let { retireLocked(it) }
        }
    }

    private fun retireLocked(entry: Entry) {
        entry.retired = true
        entry.idleJob?.cancel()
        entry.idleJob = null
        if (entry.users == 0) closeLocked(entry)
    }

    private fun closeLocked(entry: Entry) {
        if (entry.closed) return
        entry.closed = true
        entries.remove(entry.machineId, entry)
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { entry.session.disconnect() } }
        }
    }

    private companion object {
        const val IDLE_DISCONNECT_MILLIS = 60_000L
    }
}

/** One SFTP channel opened for the duration of a provider operation. */
internal class RemoteSftpLease internal constructor(
    val channel: ChannelSftp,
    val root: String,
    private val onClose: () -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    /** Resolves a document-relative path below this lease's root. */
    fun absolute(relative: String): String = when {
        relative.isBlank() -> root
        root == "/" -> "/$relative"
        else -> "$root/$relative"
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            channel.disconnect()
        } finally {
            onClose()
        }
    }
}
