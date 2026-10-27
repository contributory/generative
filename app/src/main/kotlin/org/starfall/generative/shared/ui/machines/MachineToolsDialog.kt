package org.starfall.generative.shared.ui.machines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.REMOTE_TOOL_CLOSE_TERMINAL
import org.starfall.generative.shared.model.REMOTE_TOOL_DISCONNECT
import org.starfall.generative.shared.model.REMOTE_TOOL_LIST_TERMINAL
import org.starfall.generative.shared.model.REMOTE_TOOL_READ_TERMINAL
import org.starfall.generative.shared.model.REMOTE_TOOL_OPEN_TERMINAL
import org.starfall.generative.shared.model.REMOTE_TOOL_UPDATE_IP
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.remoteMachineToolNames
import org.starfall.generative.shared.ui.ToolSwitch
import org.starfall.generative.shared.ui.dialogs.AppDialog

/** Shared per-machine tool editor used by the drawer and the Remote Machines context menu. */
@Composable
fun MachineToolsDialog(
    machine: RemoteMachine,
    onDismiss: () -> Unit,
    onSave: (RemoteMachine) -> Unit
) {
    var aiTools by remember(machine.id) {
        mutableStateOf(
            machine.aiTools + machine.supportedToolNames().associateWith { toolName ->
                machine.isToolEnabled(toolName)
            }
        )
    }

    AppDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column {
                MachineDialogTopBar(
                    title = stringResource(R.string.remote_machine_tools_section),
                    machine = machine
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    remoteMachineToolNames(machine.type).forEach { toolName ->
                        val label = stringResource(
                            when (toolName) {
                                REMOTE_TOOL_UPDATE_IP -> R.string.remote_machine_update_ip
                                REMOTE_TOOL_DISCONNECT -> R.string.remote_machine_disconnect
                                REMOTE_TOOL_OPEN_TERMINAL -> R.string.remote_machine_open_terminal
                                REMOTE_TOOL_CLOSE_TERMINAL -> R.string.remote_machine_tool_close_terminal
                                REMOTE_TOOL_LIST_TERMINAL -> R.string.remote_machine_tool_list_terminal
                                REMOTE_TOOL_READ_TERMINAL -> R.string.remote_machine_tool_read_terminal
                                else -> R.string.remote_machine_tool_execute
                            }
                        )
                        val checked = aiTools[toolName] != false
                        ToolSwitch(
                            label = label,
                            checked = checked,
                            onChange = { enabled -> aiTools = aiTools + (toolName to enabled) }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(machine.copy(aiToolsEnabled = true, aiTools = aiTools))
                    }) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}
