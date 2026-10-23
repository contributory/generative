package org.starfall.generative.shared.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.remote.TerminalInputMode
import org.starfall.generative.shared.terminal.RemoteTerminalBridge
import org.starfall.generative.shared.terminal.TerminalSlot
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

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
                        color = MaterialTheme.colorScheme.primary
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(slot) }
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
internal fun TerminalAppBar(
    bridge: RemoteTerminalBridge?,
    activeTerminal: Boolean,
    onToggleSessions: () -> Unit,
    onDisconnect: () -> Unit,
    onInputModeChange: (TerminalInputMode) -> Unit,
    onToggleForceMouse: () -> Unit,
    fontDp: Int,
    onFontChange: (Int) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleSessions) {
                FluentIcon(
                    Icons.Outlined.FormatListBulleted,
                    stringResource(R.string.remote_machine_terminal_toggle_sessions)
                )
            }
            Box(Modifier.weight(1f)) {
                val inputMode = bridge?.inputMode ?: TerminalInputMode.REMOTE
                val nextInputMode = when (inputMode) {
                    TerminalInputMode.REMOTE -> TerminalInputMode.LOCAL
                    TerminalInputMode.LOCAL -> TerminalInputMode.IME
                    TerminalInputMode.IME -> TerminalInputMode.REMOTE
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TerminalToolbarToggle(
                        icon = if (inputMode == TerminalInputMode.IME) Icons.Outlined.Edit else Icons.Outlined.Keyboard,
                        label = stringResource(
                            when (inputMode) {
                                TerminalInputMode.REMOTE -> R.string.remote_machine_terminal_input_remote_short
                                TerminalInputMode.LOCAL -> R.string.remote_machine_terminal_input_local_short
                                TerminalInputMode.IME -> R.string.remote_machine_terminal_input_ime_short
                            }
                        ),
                        description = stringResource(R.string.remote_machine_terminal_input_settings),
                        checked = bridge != null,
                        enabled = bridge != null && (nextInputMode.usesLocalInput || !bridge.hasLocalDraft),
                        onClick = { onInputModeChange(nextInputMode) }
                    )
                    TerminalToolbarToggle(
                        iconRes = R.drawable.ic_mouse_24,
                        label = stringResource(R.string.remote_machine_terminal_mouse_short),
                        description = stringResource(R.string.remote_machine_terminal_force_mouse),
                        checked = bridge?.forceMouse == true,
                        enabled = bridge != null,
                        onClick = onToggleForceMouse
                    )
                    VerticalDivider(Modifier.height(36.dp).padding(horizontal = 4.dp))
                    TerminalToolbarButton(
                        description = stringResource(R.string.remote_machine_terminal_font_smaller),
                        enabled = fontDp > MIN_TERMINAL_FONT_DP,
                        onClick = { onFontChange(-1) }
                    ) {
                        Text("A−", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    TerminalToolbarButton(
                        description = stringResource(R.string.remote_machine_terminal_font_larger),
                        enabled = fontDp < MAX_TERMINAL_FONT_DP,
                        onClick = { onFontChange(1) }
                    ) {
                        Text("A+", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            IconButton(
                enabled = activeTerminal,
                onClick = onDisconnect
            ) {
                FluentIcon(
                    Icons.Outlined.LinkOff,
                    stringResource(R.string.remote_machine_disconnect)
                )
            }
        }
    }
}

@Composable
private fun TerminalToolbarToggle(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        FilledIconToggleButton(checked = checked, onCheckedChange = { onClick() }, enabled = enabled) {
            FluentIcon(icon, description, Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TerminalToolbarToggle(
    iconRes: Int,
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        FilledIconToggleButton(checked = checked, onCheckedChange = { onClick() }, enabled = enabled) {
            FluentIcon(iconRes, description, Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TerminalToolbarButton(
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .semantics { contentDescription = description }
    ) { icon() }
}

@Composable
internal fun BoxScope.LocalInputEditor(bridge: RemoteTerminalBridge) {
    // Same box as the LOCAL draft overlay; the only difference is that the text is typeable.
    Surface(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val draftScroll = rememberScrollState()
        val textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            Modifier
                .heightIn(max = 140.dp)
                .verticalScroll(draftScroll)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.remote_machine_terminal_local_input_label) + ": ",
                style = textStyle,
                color = MaterialTheme.colorScheme.primary
            )
            BasicTextField(
                value = bridge.localInput,
                onValueChange = bridge::replaceLocalInput,
                modifier = Modifier.weight(1f),
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { bridge.submitEditorInput() })
            )
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
                FluentIcon(Icons.Outlined.Keyboard, null)
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
    Box(
        Modifier
            .defaultMinSize(minWidth = 40.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
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
