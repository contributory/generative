@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.starfall.multigateway.features.machines.list.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.features.machines.shared.logic.RemoteMachinesViewModel
import org.starfall.multigateway.shared.model.RemoteMachine
import org.starfall.multigateway.shared.model.RemoteMachineType
import org.starfall.multigateway.shared.ui.dialogs.AppAlertDialog
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons

private data class MachineIpUpdateNotice(val machineName: String, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMachinesScreen(
    viewModel: RemoteMachinesViewModel,
    onBack: () -> Unit,
    onOpenEditor: (RemoteMachine, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTerminal: (RemoteMachine) -> Unit
) {
    val state by viewModel.state.collectAsStateCompat()
    val connectedIds by viewModel.connectedMachineIds.collectAsStateCompat()
    var deleting by remember { mutableStateOf<RemoteMachine?>(null) }
    var ipUpdateNotice by remember { mutableStateOf<MachineIpUpdateNotice?>(null) }
    val scope = rememberCoroutineScope()

    RemoteMachinesRootScaffold(
        machines = state.machines,
        onBack = onBack,
        onAdd = { onOpenEditor(viewModel.newMachine(), true) },
        onSettings = onOpenSettings,
        content = {
            RemoteMachineList(
                machines = state.machines,
                connectedIds = connectedIds,
                onDisconnect = { viewModel.disconnect(it.id) },
                onTerminal = onOpenTerminal,
                onEdit = { onOpenEditor(it, false) },
                onDelete = { deleting = it },
                onUpdateIp = { machine ->
                    scope.launch {
                        viewModel.refreshIp(machine).fold(
                            onSuccess = { updated ->
                                ipUpdateNotice = MachineIpUpdateNotice(
                                    machine.name,
                                    "${updated.host}:${updated.port}"
                                )
                            },
                            onFailure = { error ->
                                ipUpdateNotice = MachineIpUpdateNotice(
                                    machine.name,
                                    error.message ?: error.toString()
                                )
                            }
                        )
                    }
                }
            )
        }
    )

    deleting?.let { machine ->
        AppAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.remote_machine_delete_title)) },
            text = { Text(stringResource(R.string.remote_machine_delete_message, machine.name)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteMachine(machine.id)
                    deleting = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    ipUpdateNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { ipUpdateNotice = null },
            title = { Text(stringResource(R.string.remote_machine_update_ip_title, notice.machineName)) },
            text = { Text(notice.message) },
            confirmButton = {
                TextButton(onClick = { ipUpdateNotice = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

/** StateFlow collection without making the extension depend on an app-specific helper. */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> =
    this.collectAsStateWithLifecycle()

@Composable
private fun RemoteMachinesRootScaffold(
    machines: List<RemoteMachine>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.remote_machines_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text(stringResource(R.string.remote_machines_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                actions = {
                    IconButton(onClick = onAdd) { FluentIcon(Icons.Default.Add, stringResource(R.string.remote_machine_add)) }
                    IconButton(onClick = onSettings) { FluentIcon(Icons.Outlined.Settings, stringResource(R.string.remote_machine_settings)) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (machines.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FluentIcon(Icons.Outlined.Computer, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.remote_machine_empty), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.remote_machine_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAdd) { FluentIcon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.remote_machine_add)) }
                }
            } else content()
        }
    }
}

@Composable
private fun RemoteMachineList(
    machines: List<RemoteMachine>,
    connectedIds: Set<String>,
    onDisconnect: (RemoteMachine) -> Unit,
    onTerminal: (RemoteMachine) -> Unit,
    onEdit: (RemoteMachine) -> Unit,
    onDelete: (RemoteMachine) -> Unit,
    onUpdateIp: (RemoteMachine) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(machines, key = { machine -> machine.id }) { machine ->
            RemoteMachineCard(
                machine = machine,
                connected = machine.id in connectedIds,
                onDisconnect = { onDisconnect(machine) },
                onTerminal = { onTerminal(machine) },
                onEdit = { onEdit(machine) },
                onDelete = { onDelete(machine) },
                onUpdateIp = { onUpdateIp(machine) }
            )
        }
    }
}

@Composable
private fun RemoteMachineCard(
    machine: RemoteMachine,
    connected: Boolean,
    onDisconnect: () -> Unit,
    onTerminal: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdateIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().then(modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (machine.type == RemoteMachineType.GCLOUD_SHELL) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            ) {
                FluentIcon(
                    if (machine.type == RemoteMachineType.GCLOUD_SHELL) Icons.Outlined.Cloud else Icons.Outlined.Computer,
                    null,
                    tint = if (machine.type == RemoteMachineType.GCLOUD_SHELL) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(machine.name.ifBlank { stringResource(R.string.remote_machine_unnamed) }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    stringResource(if (machine.type == RemoteMachineType.GCLOUD_SHELL) R.string.remote_machine_type_gcloud else R.string.remote_machine_type_ssh),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${machine.username.ifBlank { "?" }}@${machine.displayHost}:${machine.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (connected) {
                IconButton(onClick = onDisconnect) {
                    FluentIcon(Icons.Outlined.LinkOff, stringResource(R.string.remote_machine_disconnect))
                }
            }
            IconButton(onClick = onTerminal) { FluentIcon(Icons.Outlined.Terminal, stringResource(R.string.remote_machine_open_terminal)) }
            Box {
                IconButton(onClick = { menu = true }) { FluentIcon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (machine.type == RemoteMachineType.GCLOUD_SHELL) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remote_machine_update_ip)) },
                            leadingIcon = { FluentIcon(Icons.Outlined.Refresh, null) },
                            onClick = { menu = false; onUpdateIp() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        leadingIcon = { FluentIcon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { FluentIcon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
