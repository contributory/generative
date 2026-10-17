@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.starfall.multigateway.features.remotemachines.remotemachines.screen

import android.app.Activity
import android.content.ClipData
import android.net.Uri
import android.content.ClipboardManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.LocalTextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.ByteArrayOutputStream
import org.starfall.multigateway.R
import org.starfall.multigateway.features.remotemachines.machinedetails.screen.MachineDetailsScreen
import org.starfall.multigateway.features.remotemachines.machinedetails.screen.RemoteMachineSettings
import org.starfall.multigateway.features.remotemachines.machinedetails.screen.RemoteTerminalScreen
import org.starfall.multigateway.integrations.remote.ExecutableBinary
import org.starfall.multigateway.shared.model.RemoteMachine
import org.starfall.multigateway.shared.model.RemoteMachineType
import org.starfall.multigateway.shared.ui.dialogs.AppAlertDialog
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField
import org.starfall.multigateway.shared.ui.layout.SlideScreenContent

internal data class MachineEditor(val machine: RemoteMachine, val isNew: Boolean)
private data class MachineIpUpdateNotice(val machineName: String, val message: String)
internal data class BinaryTestNotice(val binaryName: String, val status: String, val output: String)

private enum class RemoteMachinesPage { SETTINGS, TERMINAL }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMachinesScreen(
    viewModel: RemoteMachinesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateCompat()
    val binaries by viewModel.binaries.collectAsStateCompat()
    val connectedIds by viewModel.connectedMachineIds.collectAsStateCompat()
    var editor by remember { mutableStateOf<MachineEditor?>(null) }
    var page by remember { mutableStateOf<RemoteMachinesPage?>(null) }
    var deleting by remember { mutableStateOf<RemoteMachine?>(null) }
    var terminalMachine by remember { mutableStateOf<RemoteMachine?>(null) }
    var ipUpdateNotice by remember { mutableStateOf<MachineIpUpdateNotice?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = editor != null || page != null || terminalMachine != null) {
        when {
            terminalMachine != null -> terminalMachine = null
            editor != null -> editor = null
            page != null -> page = null
        }
    }

    val editorPage = editor
    val activePage = page
    SlideScreenContent(
        editor = when {
            editorPage != null -> editorPage
            activePage != null -> activePage
            terminalMachine != null -> terminalMachine
            else -> null
        },
        label = stringResource(R.string.remote_machines_title)
    ) { selected ->
        when {
            // AnimatedContent composes outgoing and incoming pages together. Use each slot's own
            // target value so the outgoing list does not briefly render as a second terminal.
            selected is RemoteMachine -> RemoteTerminalScreen(
                machine = selected,
                viewModel = viewModel,
                onBack = { terminalMachine = null }
            )
            selected is MachineEditor -> MachineDetailsScreen(
                initial = selected.machine,
                isNew = selected.isNew,
                state = state,
                onSave = {
                    viewModel.saveMachine(it)
                    editor = null
                },
                onBack = { editor = null },
                onRefreshIp = { viewModel.refreshIp(it) },
                onFetchUsername = { viewModel.fetchGCloudUsername(it) },
                onAuthorizeOAuth = { viewModel.authorize(it) },
                onStoreAndroidOAuthToken = { client, token -> viewModel.storeAndroidOAuthToken(client, token) }
            )
            selected == RemoteMachinesPage.SETTINGS -> RemoteMachineSettings(
                state = state,
                binaries = binaries,
                viewModel = viewModel,
                onBack = { page = null }
            )
            else -> RemoteMachinesRootScaffold(
                machines = state.machines,
                onBack = onBack,
                onAdd = { editor = MachineEditor(viewModel.newMachine(), true) },
                onSettings = { page = RemoteMachinesPage.SETTINGS },
                content = {
                    RemoteMachineList(
                        machines = state.machines,
                        connectedIds = connectedIds,
                        onDisconnect = { viewModel.disconnect(it.id) },
                        onTerminal = { terminalMachine = it },
                        onEdit = { editor = MachineEditor(it, false) },
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
        }
    }

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
