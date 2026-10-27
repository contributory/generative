package org.starfall.generative.shared.ui.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.terminal.RemoteTerminalBridge
import org.starfall.generative.shared.terminal.TerminalSlot
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.theme.LocalAmoledMode

internal const val MIN_TERMINAL_FONT_DP = 8
internal const val MAX_TERMINAL_FONT_DP = 32

@Composable
internal fun TerminalSessionsSideBar(
    machine: RemoteMachine,
    slots: List<TerminalSlot>,
    activeTerminalId: String?,
    connecting: Boolean,
    onSelect: (TerminalSlot) -> Unit,
    onClose: (TerminalSlot) -> Unit,
    onNew: () -> Unit
) {
    Surface(
        Modifier.width(252.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.remote_machine_terminal_sessions),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(
                            if (slots.isEmpty()) R.string.remote_machine_terminal_disconnected
                            else R.string.remote_machine_terminal_connected
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(enabled = !connecting, onClick = onNew) {
                    FluentIcon(Icons.Default.Add, stringResource(R.string.remote_machine_terminal_add))
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(slots, key = { _, slot -> slot.terminal.id }) { index, slot ->
                    val selected = slot.terminal.id == activeTerminalId
                    val amoledSelected = selected && LocalAmoledMode.current
                    Surface(
                        onClick = { onSelect(slot) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected && !amoledSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (amoledSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TerminalStatusDot(slot.terminal.isOpen)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.remote_machine_terminal_tab, index + 1),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${machine.username.ifBlank { "?" }}@${machine.displayHost}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onClose(slot) }, modifier = Modifier.size(32.dp)) {
                                FluentIcon(
                                    Icons.Default.Close,
                                    stringResource(R.string.remote_machine_terminal_close_session),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                if (slots.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.remote_machine_terminal_no_sessions_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TerminalExtraKeys(bridge: RemoteTerminalBridge) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TerminalKey("ESC") { bridge.sendSpecial("\u001b") }
                TerminalKey("TAB") { bridge.sendSpecial("\t") }
                TerminalKey("CTRL", active = bridge.ctrlActive) { bridge.ctrlActive = !bridge.ctrlActive }
                TerminalKey("ALT", active = bridge.altActive) { bridge.altActive = !bridge.altActive }
                TerminalKey("←", repeat = true) { bridge.sendSpecial("\u001b[D", forceRemote = true) }
                TerminalKey("↑", repeat = true) { bridge.sendSpecial("\u001b[A", forceRemote = true) }
                TerminalKey("↓", repeat = true) { bridge.sendSpecial("\u001b[B", forceRemote = true) }
                TerminalKey("→", repeat = true) { bridge.sendSpecial("\u001b[C", forceRemote = true) }
                TerminalKey("HOME") { bridge.sendSpecial("\u001b[H", forceRemote = true) }
                TerminalKey("END") { bridge.sendSpecial("\u001b[F", forceRemote = true) }
                TerminalKey("PGUP") { bridge.sendSpecial("\u001b[5~", forceRemote = true) }
                TerminalKey("PGDN") { bridge.sendSpecial("\u001b[6~", forceRemote = true) }
                listOf('|', '~', '-', '/', '\\', '_', '$', '&', '*', '<', '>', '`').forEach { char ->
                    TerminalKey(char.toString()) { bridge.sendChar(char) }
                }
            }
            IconButton(onClick = { bridge.toggleKeyboard() }) {
                FluentIcon(Icons.Outlined.Keyboard, stringResource(R.string.remote_machine_terminal_keyboard))
            }
        }
    }
}

@Composable
private fun TerminalKey(
    label: String,
    active: Boolean = false,
    repeat: Boolean = false,
    onPress: () -> Unit
) {
    val current by rememberUpdatedState(onPress)
    val keyShape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .defaultMinSize(minWidth = 40.dp)
            .height(38.dp)
            .clip(keyShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .then(
                if (!active && LocalAmoledMode.current) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, keyShape)
                } else {
                    Modifier
                }
            )
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    current()
                    if (repeat) {
                        coroutineScope {
                            val job = launch {
                                delay(350)
                                while (true) {
                                    current()
                                    delay(60)
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                    } else {
                        tryAwaitRelease()
                    }
                })
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun TerminalStatusDot(connected: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (connected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
            )
    )
}
