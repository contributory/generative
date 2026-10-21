package org.starfall.generative.features.machines.shared.ui

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.integrations.remote.RemoteTerminal

internal class TerminalSlot(
    val terminal: RemoteTerminal,
    val bridge: RemoteTerminalBridge
) {
    var readerJob: Job? = null
}

/**
 * Process-wide registry of open remote terminals. The terminal screen only displays what is
 * registered here, so leaving the screen no longer closes the sessions: they keep running and
 * recording output until the user closes them, the remote shell exits, or the machine disconnects.
 */
internal object RemoteTerminalSessions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val slots = mutableStateMapOf<String, List<TerminalSlot>>()
    private val active = mutableStateMapOf<String, String?>()
    private val lost = mutableStateMapOf<String, Boolean>()

    fun slotsFor(machineId: String): List<TerminalSlot> = slots[machineId].orEmpty()
    fun activeIdFor(machineId: String): String? = active[machineId]
    fun setActive(machineId: String, terminalId: String?) { active[machineId] = terminalId }
    fun connectionLost(machineId: String): Boolean = lost[machineId] == true
    fun clearConnectionLost(machineId: String) { lost.remove(machineId) }

    /** Registers an opened terminal, makes it the active one and starts reading its output. */
    fun register(machineId: String, terminal: RemoteTerminal, bridge: RemoteTerminalBridge): TerminalSlot {
        val slot = TerminalSlot(terminal, bridge)
        slots[machineId] = slotsFor(machineId) + slot
        active[machineId] = terminal.id
        lost.remove(machineId)
        slot.readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val count = terminal.input.read(buffer)
                    if (count < 0) break
                    val chunk = buffer.copyOf(count)
                    withContext(Dispatchers.Main) { bridge.append(chunk) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: java.io.IOException) {
                // Stream closed: handled below like an end of stream.
            }
            // The remote side ended the session (a local close cancels this job instead).
            if (isActive) {
                val cleanExit = terminal.exitStatus >= 0
                withContext(Dispatchers.Main) {
                    remove(machineId, slot)
                    bridge.close()
                    terminal.close()
                    if (!cleanExit && slotsFor(machineId).isEmpty()) lost[machineId] = true
                }
            }
        }
        return slot
    }

    /**
     * Closes one session. Closing the last registered terminal disconnects the machine (see the
     * connection manager), so no separate disconnect call is needed.
     */
    fun close(machineId: String, slot: TerminalSlot) {
        slot.readerJob?.cancel()
        slot.bridge.close()
        slot.terminal.close()
        remove(machineId, slot)
    }

    private fun remove(machineId: String, slot: TerminalSlot) {
        val before = slotsFor(machineId)
        val index = before.indexOf(slot)
        val after = before.filterNot { it === slot }
        if (after.isEmpty()) {
            slots.remove(machineId)
            active.remove(machineId)
            return
        }
        slots[machineId] = after
        if (active[machineId] == slot.terminal.id) {
            active[machineId] = after[index.coerceIn(0, after.lastIndex)].terminal.id
        }
    }
}
