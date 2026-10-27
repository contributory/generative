package org.starfall.generative.shared.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.starfall.generative.R
import org.starfall.generative.shared.model.remote.TerminalInputMode
import org.starfall.generative.shared.terminal.RemoteTerminalBridge
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalAppBar(
    bridge: RemoteTerminalBridge?,
    onToggleSessions: () -> Unit,
    onDisconnect: () -> Unit,
    canUseRemoteInput: Boolean,
    onInputModeChange: (TerminalInputMode) -> Unit,
    onToggleIme: () -> Unit,
    onToggleForceMouse: () -> Unit
) {
    var inputMenuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {},
        // The screen handles system insets for the entire terminal surface.
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(onClick = onDisconnect) {
                FluentIcon(Icons.Outlined.LinkOff, stringResource(R.string.remote_machine_terminal_disconnect_close))
            }
        },
        actions = {
            val imeDescription = stringResource(R.string.remote_machine_terminal_ime)
            IconToggleButton(
                checked = bridge?.imeEnabled == true,
                onCheckedChange = { onToggleIme() },
                enabled = bridge != null,
                modifier = Modifier.semantics { contentDescription = imeDescription }
            ) {
                Text(stringResource(R.string.remote_machine_terminal_input_ime_short),
                    style = MaterialTheme.typography.labelMedium)
            }
            Box {
                val mode = bridge?.inputMode ?: TerminalInputMode.REMOTE
                val modeLabel = stringResource(if (mode == TerminalInputMode.LOCAL)
                    R.string.remote_machine_terminal_input_local_short else R.string.remote_machine_terminal_input_remote_short)
                IconButton(enabled = bridge != null, onClick = { inputMenuOpen = true }) {
                    val description = stringResource(R.string.remote_machine_terminal_input_settings) + ": " + modeLabel
                    if (mode == TerminalInputMode.LOCAL) FluentIcon(Icons.Outlined.Edit, description)
                    else FluentIcon(Icons.Outlined.Terminal, description)
                }
                DropdownMenu(expanded = inputMenuOpen, onDismissRequest = { inputMenuOpen = false }) {
                    TerminalInputMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(if (option == TerminalInputMode.LOCAL)
                                R.string.remote_machine_terminal_input_local_short else R.string.remote_machine_terminal_input_remote_short)) },
                            leadingIcon = {
                                if (option == mode) FluentIcon(Icons.Default.Check, null)
                            },
                            enabled = bridge != null && (option.usesLocalInput || canUseRemoteInput),
                            onClick = { onInputModeChange(option); inputMenuOpen = false }
                        )
                    }
                }
            }
            IconToggleButton(
                checked = bridge?.forceMouse == true,
                onCheckedChange = { onToggleForceMouse() },
                enabled = bridge != null
            ) {
                FluentIcon(R.drawable.ic_mouse_24, stringResource(R.string.remote_machine_terminal_force_mouse))
            }
            IconButton(onClick = onToggleSessions) {
                FluentIcon(Icons.Outlined.FormatListBulleted, stringResource(R.string.remote_machine_terminal_toggle_sessions))
            }
        }
    )
}
