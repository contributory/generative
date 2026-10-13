@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.starfall.multigateway.extservices.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Keyboard
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.ByteArrayOutputStream
import org.starfall.multigateway.R
import org.starfall.multigateway.ui.components.AppAlertDialog
import org.starfall.multigateway.ui.components.SelectableOutlinedTextField
import org.starfall.multigateway.ui.navigation.SlideScreenContent

private data class MachineEditor(val machine: RemoteMachine, val isNew: Boolean)

private enum class RemoteMachinesPage { SETTINGS, TERMINAL }
private enum class TerminalInputMode { LOCAL, REMOTE }

private val TERMINAL_BACKGROUND = Color(0xFF101214)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMachinesScreen(
    viewModel: RemoteMachinesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateCompat()
    var editor by remember { mutableStateOf<MachineEditor?>(null) }
    var page by remember { mutableStateOf<RemoteMachinesPage?>(null) }
    var deleting by remember { mutableStateOf<RemoteMachine?>(null) }
    var terminalMachine by remember { mutableStateOf<RemoteMachine?>(null) }
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
            terminalMachine != null -> RemoteTerminalScreen(
                machine = terminalMachine!!,
                viewModel = viewModel,
                onBack = { terminalMachine = null }
            )
            selected is MachineEditor -> RemoteMachineEditor(
                initial = selected.machine,
                isNew = selected.isNew,
                state = state,
                onSave = {
                    viewModel.saveMachine(it)
                    editor = null
                },
                onBack = { editor = null },
                onRefreshIp = { viewModel.refreshIp(it) },
                onAuthorizeOAuth = { viewModel.authorize(it) }
            )
            selected == RemoteMachinesPage.SETTINGS -> RemoteMachineSettings(
                state = state,
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
                        onTerminal = { terminalMachine = it },
                        onEdit = { editor = MachineEditor(it, false) },
                        onDelete = { deleting = it }
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                actions = {
                    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, stringResource(R.string.remote_machine_add)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.remote_machine_settings)) }
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
                    Icon(Icons.Outlined.Computer, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.remote_machine_empty), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.remote_machine_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.remote_machine_add)) }
                }
            } else content()
        }
    }
}

@Composable
private fun RemoteMachineList(
    machines: List<RemoteMachine>,
    onTerminal: (RemoteMachine) -> Unit,
    onEdit: (RemoteMachine) -> Unit,
    onDelete: (RemoteMachine) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(machines, key = { machine -> machine.id }) { machine ->
            RemoteMachineCard(
                machine = machine,
                onTerminal = { onTerminal(machine) },
                onEdit = { onEdit(machine) },
                onDelete = { onDelete(machine) }
            )
        }
    }
}

@Composable
private fun RemoteMachineCard(
    machine: RemoteMachine,
    onTerminal: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                Icon(
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
            IconButton(onClick = onTerminal) { Icon(Icons.Outlined.Terminal, stringResource(R.string.remote_machine_open_terminal)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteMachineEditor(
    initial: RemoteMachine,
    isNew: Boolean,
    state: RemoteMachineState,
    onSave: (RemoteMachine) -> Unit,
    onBack: () -> Unit,
    onRefreshIp: suspend (RemoteMachine) -> Result<RemoteMachine>,
    onAuthorizeOAuth: suspend (GCloudOAuthClient) -> Result<Unit>
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var type by remember(initial.id) { mutableStateOf(initial.type) }
    var host by remember(initial.id) { mutableStateOf(initial.host) }
    var port by remember(initial.id) { mutableStateOf(initial.port.toString()) }
    var username by remember(initial.id) { mutableStateOf(initial.username) }
    var auth by remember(initial.id) { mutableStateOf(initial.authentication) }
    var password by remember(initial.id) { mutableStateOf(initial.password) }
    var showPassword by remember(initial.id) { mutableStateOf(false) }
    var keyboardInteractive by remember(initial.id) { mutableStateOf(initial.keyboardInteractive) }
    var identityFile by remember(initial.id) { mutableStateOf(initial.identityFile) }
    var passphrase by remember(initial.id) { mutableStateOf(initial.identityPassphrase) }
    var proxyCommand by remember(initial.id) { mutableStateOf(initial.proxyCommand) }
    var strictHostKey by remember(initial.id) { mutableStateOf(initial.strictHostKeyChecking) }
    var compression by remember(initial.id) { mutableStateOf(initial.compression) }
    var keepAlive by remember(initial.id) { mutableStateOf(initial.keepAliveSeconds.toString()) }
    var timeout by remember(initial.id) { mutableStateOf(initial.connectTimeoutSeconds.toString()) }
    var oauthClientId by remember(initial.id) { mutableStateOf(initial.oauthClientId) }
    var environment by remember(initial.id) { mutableStateOf(initial.environmentName) }
    var gcloudUsername by remember(initial.id) { mutableStateOf(initial.gcloudUsername) }
    var aiTools by remember(initial.id) {
        mutableStateOf(
            initial.aiTools.ifEmpty {
                if (initial.aiToolsEnabled) emptyMap()
                else initial.supportedToolNames().associateWith { false }
            }
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingIp by remember { mutableStateOf(false) }
    var authorizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.remote_machine_add_title else R.string.remote_machine_edit_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                actions = { TextButton(onClick = {
                    val machine = initial.copy(
                        name = name,
                        type = type,
                        host = host,
                        port = port.toIntOrNull() ?: 22,
                        username = username,
                        authentication = auth,
                        password = password,
                        keyboardInteractive = keyboardInteractive,
                        identityFile = identityFile,
                        identityPassphrase = passphrase,
                        proxyCommand = proxyCommand,
                        strictHostKeyChecking = strictHostKey,
                        compression = compression,
                        keepAliveSeconds = keepAlive.toIntOrNull() ?: 30,
                        connectTimeoutSeconds = timeout.toIntOrNull() ?: 15,
                        oauthClientId = oauthClientId,
                        environmentName = environment,
                        gcloudUsername = gcloudUsername,
                        aiToolsEnabled = true,
                        aiTools = aiTools
                    )
                    runCatching {
                        require(name.isNotBlank()) { "Machine name is required." }
                        require(type != RemoteMachineType.SSH || host.isNotBlank()) { "Host is required for SSH machines." }
                    }.onSuccess { onSave(machine) }.onFailure { error = it.message }
                }) { Text(stringResource(R.string.common_save)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RemoteSection(stringResource(R.string.remote_machine_basic_section)) {
                RemoteTextField(name, { name = it }, stringResource(R.string.common_name))
                Text(stringResource(R.string.remote_machine_type_label), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == RemoteMachineType.SSH, { type = RemoteMachineType.SSH }, label = { Text(stringResource(R.string.remote_machine_type_ssh)) })
                    FilterChip(type == RemoteMachineType.GCLOUD_SHELL, { type = RemoteMachineType.GCLOUD_SHELL }, label = { Text(stringResource(R.string.remote_machine_type_gcloud)) })
                }
            }
            RemoteSection(stringResource(R.string.remote_machine_connection_section)) {
                if (type == RemoteMachineType.SSH) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RemoteTextField(host, { host = it }, stringResource(R.string.remote_machine_host), Modifier.weight(1f))
                        RemoteTextField(port, { if (it.all(Char::isDigit)) port = it }, stringResource(R.string.remote_machine_port), Modifier.width(100.dp))
                    }
                } else {
                    RemoteTextField(environment, { environment = it }, stringResource(R.string.remote_machine_environment))
                    RemoteTextField(gcloudUsername, { gcloudUsername = it }, stringResource(R.string.remote_machine_gcloud_user))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteTextField(host, {}, stringResource(R.string.remote_machine_ip), Modifier.weight(1f), readOnly = true)
                        OutlinedButton(enabled = !loadingIp, onClick = {
                            loadingIp = true
                            scope.launch {
                            onRefreshIp(initial.copy(name = name, type = type, environmentName = environment, gcloudUsername = gcloudUsername, oauthClientId = oauthClientId))
                                    .onSuccess { updated -> host = updated.host; port = updated.port.toString(); username = updated.username }
                                    .onFailure { error = it.message }
                                loadingIp = false
                            }
                        }) {
                            if (loadingIp) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.remote_machine_get_ip))
                        }
                    }
                }
                RemoteTextField(username, { username = it }, stringResource(R.string.remote_machine_username))
                Text(stringResource(R.string.remote_machine_authentication), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(auth == RemoteAuthentication.PRIVATE_KEY, { auth = RemoteAuthentication.PRIVATE_KEY }, label = { Text(stringResource(R.string.remote_machine_private_key)) })
                    FilterChip(auth == RemoteAuthentication.PASSWORD, { auth = RemoteAuthentication.PASSWORD }, label = { Text(stringResource(R.string.remote_machine_password)) })
                }
                if (auth == RemoteAuthentication.PASSWORD) {
                    RemoteTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.remote_machine_password),
                        visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (showPassword) R.string.remote_machine_hide_password else R.string.remote_machine_show_password
                                    )
                                )
                            }
                        }
                    )
                    RemoteToggle(
                        stringResource(R.string.remote_machine_keyboard_interactive),
                        keyboardInteractive
                    ) { keyboardInteractive = it }
                    Text(
                        stringResource(R.string.remote_machine_keyboard_interactive_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    RemoteTextField(
                        identityFile,
                        { identityFile = it },
                        stringResource(R.string.remote_machine_identity_file),
                        supporting = stringResource(R.string.remote_machine_identity_file_help)
                    )
                    var keyMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { keyMenu = true }) {
                            Icon(Icons.Outlined.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.remote_machine_choose_key))
                        }
                        DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                            if (state.identities.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remote_machine_no_uploaded_keys)) },
                                    onClick = { keyMenu = false },
                                    enabled = false
                                )
                            }
                            state.identities.forEach { key ->
                                DropdownMenuItem(
                                    text = { Text(key.name) },
                                    onClick = { identityFile = key.path; keyMenu = false }
                                )
                            }
                        }
                    }
                    RemoteTextField(passphrase, { passphrase = it }, stringResource(R.string.remote_machine_key_passphrase), visual = PasswordVisualTransformation())
                }
            }
            RemoteSection(stringResource(R.string.remote_machine_ssh_options)) {
                RemoteTextField(proxyCommand, { proxyCommand = it }, stringResource(R.string.remote_machine_proxy_command), supporting = stringResource(R.string.remote_machine_proxy_command_help))
                RemoteTextField(timeout, { if (it.all(Char::isDigit)) timeout = it }, stringResource(R.string.remote_machine_timeout))
                RemoteTextField(keepAlive, { if (it.all(Char::isDigit)) keepAlive = it }, stringResource(R.string.remote_machine_keep_alive))
                RemoteToggle(stringResource(R.string.remote_machine_strict_host_key), strictHostKey) { strictHostKey = it }
                RemoteToggle(stringResource(R.string.remote_machine_compression), compression) { compression = it }
            }
            if (type == RemoteMachineType.GCLOUD_SHELL) {
                RemoteSection(stringResource(R.string.remote_machine_google_section)) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                        SelectableOutlinedTextField(
                            value = state.oauthClients.firstOrNull { it.id == oauthClientId }?.name
                                ?: stringResource(R.string.remote_machine_select_oauth),
                            onValueChange = {}, readOnly = true,
                            label = { Text(stringResource(R.string.remote_machine_oauth_client)) },
                            trailingIcon = { TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            state.oauthClients.forEach { client ->
                                DropdownMenuItem(text = { Text(client.name) }, onClick = { oauthClientId = client.id; expanded = false })
                            }
                        }
                    }
                    if (state.oauthClients.isEmpty()) Text(stringResource(R.string.remote_machine_add_oauth_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    val selectedOAuthClient = state.oauthClients.firstOrNull { it.id == oauthClientId }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            enabled = selectedOAuthClient != null && !authorizing,
                            onClick = {
                                val client = selectedOAuthClient ?: return@OutlinedButton
                                authorizing = true
                                scope.launch {
                                    onAuthorizeOAuth(client)
                                        .onFailure { error = it.message }
                                    authorizing = false
                                }
                            }
                        ) {
                            if (authorizing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Key, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.remote_machine_oauth_sign_in))
                        }
                        if (state.oauthTokens.containsKey(oauthClientId)) {
                            Text(
                                stringResource(R.string.remote_machine_oauth_authorized),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            RemoteSection(stringResource(R.string.remote_machine_tools_section)) {
                Text(
                    stringResource(R.string.remote_machine_tools_choose),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val toolNames = when (type) {
                    RemoteMachineType.SSH -> listOf(REMOTE_TOOL_EXECUTE)
                    RemoteMachineType.GCLOUD_SHELL -> listOf(REMOTE_TOOL_START_MACHINE, REMOTE_TOOL_EXECUTE)
                }
                toolNames.forEach { toolName ->
                    RemoteToggle(
                        label = stringResource(
                            if (toolName == REMOTE_TOOL_START_MACHINE) {
                                R.string.remote_machine_tool_start_machine
                            } else {
                                R.string.remote_machine_tool_execute
                            }
                        ),
                        checked = aiTools[toolName] != false
                    ) { enabled ->
                        aiTools = aiTools + (toolName to enabled)
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun RemoteMachineSettings(
    state: RemoteMachineState,
    viewModel: RemoteMachinesViewModel,
    onBack: () -> Unit
) {
    var sshConfig by remember(state.sshConfig) { mutableStateOf(state.sshConfig) }
    var clientEditor by remember { mutableStateOf<GCloudOAuthClient?>(null) }
    var binaryError by remember { mutableStateOf<String?>(null) }
    var identityError by remember { mutableStateOf<String?>(null) }
    var oauthError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            sshConfig = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
        }
    }
    val identityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importIdentityKey(uri)
                .onSuccess { identityError = null }
                .onFailure { identityError = it.message }
        }
    }
    val binaryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importBinary(uri).onFailure { binaryError = it.message }
        }
    }
    val oauthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importOAuthClientJson(uri)
                .onSuccess { client ->
                    oauthError = null
                    clientEditor = client
                }
                .onFailure { oauthError = it.message }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_machine_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                actions = { TextButton(onClick = { viewModel.saveSshConfig(sshConfig); onBack() }) { Text(stringResource(R.string.common_save)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RemoteSection(stringResource(R.string.remote_machine_ssh_config)) {
                Text(stringResource(R.string.remote_machine_ssh_config_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { configLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Outlined.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.remote_machine_open_ssh_config))
                }
                RemoteTextField(
                    value = sshConfig,
                    onValueChange = { sshConfig = it },
                    label = stringResource(R.string.remote_machine_ssh_config),
                    minLines = 10,
                    maxLines = 20,
                    font = FontFamily.Monospace,
                    visual = SshConfigVisualTransformation(
                        SshHighlightColors(
                            comment = MaterialTheme.colorScheme.outline,
                            keyword = MaterialTheme.colorScheme.primary,
                            value = MaterialTheme.colorScheme.onSurface
                        )
                    )
                )
                TextButton(onClick = {
                    val parsed = SshConfigParser.parse(sshConfig)
                    if (parsed.isNotEmpty()) {
                        // Import only if aliases are not already represented; the full config stays editable above.
                        scope.launch {
                            parsed.map { SshConfigParser.toMachine(it) }.forEach { machine ->
                                if (state.machines.none { it.name == machine.name }) viewModel.saveMachine(machine)
                            }
                        }
                    }
                }) { Text(stringResource(R.string.remote_machine_import_hosts)) }
            }
            RemoteSection(stringResource(R.string.remote_machine_oauth_clients)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { oauthLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }) {
                        Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.remote_machine_import_oauth_json))
                    }
                    OutlinedButton(onClick = { clientEditor = viewModel.newOAuthClient() }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.remote_machine_add_oauth))
                    }
                }
                state.oauthClients.forEach { client ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(client.name, style = MaterialTheme.typography.titleSmall)
                            Text(client.clientId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { clientEditor = client }) { Text(stringResource(R.string.common_edit)) }
                        IconButton(onClick = { viewModel.deleteOAuthClient(client.id) }) { Icon(Icons.Outlined.Lock, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error) }
                    }
                }
                oauthError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            RemoteSection(stringResource(R.string.remote_machine_ssh_keys)) {
                Text(stringResource(R.string.remote_machine_ssh_keys_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.identities.forEach { key ->
                    val users = state.machines.count { it.identityFile == key.path || it.identityFile.substringAfterLast('/') == key.name }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(key.name, style = MaterialTheme.typography.titleSmall)
                            Text(key.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            if (users > 0) Text(stringResource(R.string.remote_machine_ssh_key_used_by, users), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.deleteIdentityKey(key.id) }) { Icon(Icons.Outlined.Lock, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error) }
                    }
                }
                OutlinedButton(onClick = { identityLauncher.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.remote_machine_upload_ssh_key))
                }
                identityError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            RemoteSection(stringResource(R.string.remote_machine_binaries)) {
                Text(stringResource(R.string.remote_machine_binaries_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.binaries.forEach { binary ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Code, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(binary.name, style = MaterialTheme.typography.titleSmall)
                            Text(binary.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { viewModel.deleteBinary(binary.id) }) { Icon(Icons.Outlined.Lock, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error) }
                    }
                }
                OutlinedButton(onClick = { binaryLauncher.launch(arrayOf("application/octet-stream", "application/x-executable", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.remote_machine_add_binary))
                }
                binaryError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    clientEditor?.let { initial ->
        OAuthClientDialog(
            initial = initial,
            onDismiss = { clientEditor = null },
            onSave = { client ->
                viewModel.saveOAuthClient(client)
                clientEditor = null
            },
            onAuthorize = { client ->
                scope.launch {
                    viewModel.saveOAuthClientNow(client)
                    viewModel.authorize(client)
                    clientEditor = null
                }
            }
        )
    }
}

@Composable
private fun OAuthClientDialog(
    initial: GCloudOAuthClient,
    onDismiss: () -> Unit,
    onSave: (GCloudOAuthClient) -> Unit,
    onAuthorize: (GCloudOAuthClient) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var clientId by remember(initial.id) { mutableStateOf(initial.clientId) }
    var secret by remember(initial.id) { mutableStateOf(initial.clientSecret) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_machine_oauth_client)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteTextField(name, { name = it }, stringResource(R.string.common_name))
                RemoteTextField(clientId, { clientId = it }, stringResource(R.string.remote_machine_client_id))
                RemoteTextField(secret, { secret = it }, stringResource(R.string.remote_machine_client_secret), visual = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onAuthorize(GCloudOAuthClient(initial.id, name, clientId, secret)) }) { Text(stringResource(R.string.remote_machine_authorize)) }
                Button(onClick = { onSave(GCloudOAuthClient(initial.id, name, clientId, secret)) }) { Text(stringResource(R.string.common_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun RemoteTerminalScreen(machine: RemoteMachine, viewModel: RemoteMachinesViewModel, onBack: () -> Unit) {
    val requiresInteractivePassword =
        machine.type == RemoteMachineType.SSH &&
            machine.authentication == RemoteAuthentication.PASSWORD &&
            machine.password.isEmpty()
    var terminal by remember(machine.id) { mutableStateOf<RemoteTerminal?>(null) }
    var connecting by remember(machine.id) { mutableStateOf(!requiresInteractivePassword) }
    var error by remember { mutableStateOf<String?>(null) }
    var readerJob by remember { mutableStateOf<Job?>(null) }
    var retryAttempt by remember(machine.id) { mutableStateOf(0) }
    var passwordInput by remember(machine.id) { mutableStateOf("") }
    var showPromptPassword by remember(machine.id) { mutableStateOf(false) }
    var submittedPassword by remember(machine.id) { mutableStateOf<String?>(null) }
    var inputModeMenuExpanded by remember(machine.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val terminalPrefs = remember {
        context.getSharedPreferences(REMOTE_TERMINAL_UI_PREFERENCES, android.content.Context.MODE_PRIVATE)
    }
    var fontDp by remember { mutableIntStateOf(terminalPrefs.getInt("font_dp", 14).coerceIn(MIN_TERMINAL_FONT_DP, MAX_TERMINAL_FONT_DP)) }
    val textSizePx = (fontDp * density).toInt()
    val initialInputMode = when (terminalPrefs.getString(remoteTerminalInputModePreferenceKey(machine.id), null)) {
        TerminalInputMode.LOCAL.name -> TerminalInputMode.LOCAL
        else -> TerminalInputMode.REMOTE
    }
    val bridge = remember(machine.id) { RemoteTerminalBridge(context, initialInputMode) }
    SideEffect {
        bridge.onZoomStep = { increase ->
            fontDp = (fontDp + if (increase) 1 else -1).coerceIn(MIN_TERMINAL_FONT_DP, MAX_TERMINAL_FONT_DP)
            terminalPrefs.edit().putInt("font_dp", fontDp).apply()
        }
    }
    val currentTerminal by rememberUpdatedState(terminal)
    val currentReaderJob by rememberUpdatedState(readerJob)

    fun closeTerminal() {
        readerJob?.cancel()
        bridge.close()
        terminal?.close()
        terminal = null
    }
    // Phone back button: disconnect explicitly (same as the app bar back arrow).
    BackHandler { closeTerminal(); onBack() }
    DisposableEffect(machine.id) {
        onDispose {
            currentReaderJob?.cancel()
            bridge.close()
            currentTerminal?.close()
        }
    }
    LaunchedEffect(machine.id, retryAttempt) {
        if (requiresInteractivePassword && submittedPassword == null) {
            connecting = false
            return@LaunchedEffect
        }
        connecting = true
        error = null
        try {
            val opened = viewModel.openTerminal(machine, submittedPassword)
            terminal = opened
            bridge.attachRemote(opened)
            connecting = false
            readerJob = launch(Dispatchers.IO) {
                val buffer = ByteArray(4096)
                try {
                    while (true) {
                        val count = opened.input.read(buffer)
                        if (count < 0) break
                        val chunk = buffer.copyOf(count)
                        withContext(Dispatchers.Main) { bridge.append(chunk) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: java.io.IOException) {
                    // Stream closed: handled below like an end of stream.
                }
                // The remote side ended the session (not a local back/disconnect, which cancels us).
                if (coroutineContext.isActive) {
                    val cleanExit = opened.exitStatus >= 0
                    withContext(Dispatchers.Main) {
                        closeTerminal()
                        if (cleanExit) onBack() else error = "Connection lost"
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            connecting = false
            error = failure.message?.takeIf { it.isNotBlank() } ?: "Connection failed"
            terminal?.close()
            terminal = null
        }
    }

    Scaffold(
        containerColor = TERMINAL_BACKGROUND,
        topBar = {
            TopAppBar(
                title = { Text(machine.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { closeTerminal(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                actions = {
                    Box {
                        IconButton(onClick = { inputModeMenuExpanded = true }) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.remote_machine_terminal_input_settings))
                        }
                        DropdownMenu(
                            expanded = inputModeMenuExpanded,
                            onDismissRequest = { inputModeMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remote_machine_terminal_local_input)) },
                                trailingIcon = { if (bridge.inputMode == TerminalInputMode.LOCAL) Text("✓") },
                                onClick = {
                                    if (bridge.setInputMode(TerminalInputMode.LOCAL)) {
                                        terminalPrefs.edit()
                                            .putString(remoteTerminalInputModePreferenceKey(machine.id), TerminalInputMode.LOCAL.name)
                                            .apply()
                                    }
                                    inputModeMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                enabled = !bridge.hasLocalDraft,
                                text = { Text(stringResource(R.string.remote_machine_terminal_remote_input)) },
                                trailingIcon = { if (bridge.inputMode == TerminalInputMode.REMOTE) Text("✓") },
                                onClick = {
                                    if (bridge.setInputMode(TerminalInputMode.REMOTE)) {
                                        terminalPrefs.edit()
                                            .putString(remoteTerminalInputModePreferenceKey(machine.id), TerminalInputMode.REMOTE.name)
                                            .apply()
                                    }
                                    inputModeMenuExpanded = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TERMINAL_BACKGROUND,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().background(TERMINAL_BACKGROUND)
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                terminal?.let {
                    AndroidView(
                        factory = { viewContext ->
                            val view = TerminalView(viewContext, null)
                            view.isFocusable = true
                            view.isFocusableInTouchMode = true
                            view.setBackgroundColor(android.graphics.Color.rgb(16, 18, 20))
                            view.setTextSize(textSizePx)
                            view.tag = textSizePx
                            view.setTerminalViewClient(bridge)
                            bridge.view = view
                            val session = bridge.createSession(context.filesDir)
                            view.attachSession(session)
                            view
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = {
                            bridge.view = it
                            if (it.tag != textSizePx) {
                                it.tag = textSizePx
                                it.setTextSize(textSizePx)
                            }
                        }
                    )
                }
                bridge.localDraftForDisplay()?.let { draft ->
                    Surface(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE1B1E21)
                    ) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.remote_machine_terminal_local_input_label) + ": ",
                                color = Color(0xFF9CCC65),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Text(
                                draft,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (connecting) {
                    Surface(
                        Modifier.align(Alignment.Center),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.remote_machine_terminal_connecting))
                        }
                    }
                }
                if (!connecting && error != null) {
                    Surface(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.remote_machine_terminal_connection_error),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = {
                                if (requiresInteractivePassword) {
                                    submittedPassword = null
                                    passwordInput = ""
                                }
                                retryAttempt++
                            }) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }
                }
                if (!connecting && error == null && requiresInteractivePassword && submittedPassword == null) {
                    Surface(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.remote_machine_terminal_password_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.remote_machine_terminal_password_help),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RemoteTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = stringResource(R.string.remote_machine_password),
                                visual = if (showPromptPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPromptPassword = !showPromptPassword }) {
                                        Icon(
                                            if (showPromptPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = stringResource(
                                                if (showPromptPassword) R.string.remote_machine_hide_password else R.string.remote_machine_show_password
                                            )
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onBack) {
                                    Text(stringResource(R.string.common_cancel))
                                }
                                Button(
                                    enabled = passwordInput.isNotEmpty(),
                                    onClick = {
                                        submittedPassword = passwordInput
                                        retryAttempt++
                                    }
                                ) {
                                    Text(stringResource(R.string.remote_machine_terminal_connect))
                                }
                            }
                        }
                    }
                }
            }
            if (terminal != null) TerminalExtraKeys(bridge)
        }
    }
}

private const val MIN_TERMINAL_FONT_DP = 8
private const val MAX_TERMINAL_FONT_DP = 32

@Composable
private fun TerminalExtraKeys(bridge: RemoteTerminalBridge) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1B1E21)).padding(vertical = 3.dp),
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
            TerminalKey("←", repeat = true) { bridge.sendSpecial("\u001b[D") }
            TerminalKey("↑", repeat = true) { bridge.sendSpecial("\u001b[A") }
            TerminalKey("↓", repeat = true) { bridge.sendSpecial("\u001b[B") }
            TerminalKey("→", repeat = true) { bridge.sendSpecial("\u001b[C") }
            TerminalKey("HOME") { bridge.sendSpecial("\u001b[H") }
            TerminalKey("END") { bridge.sendSpecial("\u001b[F") }
            TerminalKey("PGUP") { bridge.sendSpecial("\u001b[5~") }
            TerminalKey("PGDN") { bridge.sendSpecial("\u001b[6~") }
            listOf('|', '~', '-', '/', '\\', '_', '$', '&', '*', '<', '>', '`').forEach { char ->
                TerminalKey(char.toString()) { bridge.sendChar(char) }
            }
        }
        IconButton(onClick = { bridge.toggleKeyboard() }) {
            Icon(Icons.Outlined.Keyboard, null, tint = Color.White)
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
            .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF2B2F33))
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    current()
                    if (repeat) {
                        coroutineScope {
                            val job = launch {
                                delay(350)
                                while (true) { current(); delay(60) }
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
            color = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

/**
 * Adapter between a remote byte stream and Termux's maintained ANSI terminal renderer. A tiny
 * sleeping local session is used only to give TerminalView its emulator lifecycle; remote output
 * is rendered here, while input is either buffered locally or routed to the JSch channel by mode.
 */
private class RemoteTerminalBridge(
    private val context: android.content.Context,
    initialInputMode: TerminalInputMode
) :
    TerminalSessionClient,
    TerminalViewClient {
    var session: TerminalSession? = null
    var view: TerminalView? = null
    private var remote: RemoteTerminal? = null
    private var closed = false
    private var emulatorReady = false
    private val pending = ByteArrayOutputStream()
    private var replyFifo: java.io.File? = null

    /** Called when the user pinches: true = bigger text, false = smaller text. */
    var onZoomStep: (Boolean) -> Unit = {}
    var ctrlActive by mutableStateOf(false)
    var altActive by mutableStateOf(false)
    var inputMode by mutableStateOf(initialInputMode)
        private set
    var localInput by mutableStateOf("")
        private set
    var submittedLocalInput by mutableStateOf<String?>(null)
        private set
    private var localCursor = 0
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = 0
    private var historyDraft = ""

    val hasLocalDraft: Boolean get() = localInput.isNotEmpty()

    fun setInputMode(mode: TerminalInputMode): Boolean {
        if (mode == TerminalInputMode.REMOTE && hasLocalDraft) return false
        inputMode = mode
        if (mode == TerminalInputMode.REMOTE) submittedLocalInput = null
        clearModifiers()
        return true
    }

    fun localDraftForDisplay(): String? {
        val submitted = submittedLocalInput
        if (inputMode != TerminalInputMode.LOCAL && submitted == null) return null
        return buildString {
            submitted?.let(::append)
            if (localInput.isNotEmpty()) {
                if (submitted != null) append("  ·  ")
                append(localInput, 0, localCursor)
                append('▏')
                append(localInput.substring(localCursor))
            } else if (submitted == null && inputMode == TerminalInputMode.LOCAL) {
                append('▏')
            }
        }
    }

    private fun insertLocalText(value: String) {
        if (value.isEmpty()) return
        localInput = localInput.substring(0, localCursor) + value + localInput.substring(localCursor)
        localCursor += value.length
        historyIndex = commandHistory.size
    }

    private fun deleteLocalPrevious() {
        if (localCursor == 0) return
        val previous = Character.offsetByCodePoints(localInput, localCursor, -1)
        localInput = localInput.removeRange(previous, localCursor)
        localCursor = previous
        historyIndex = commandHistory.size
    }

    private fun deleteLocalNext() {
        if (localCursor >= localInput.length) return
        val next = Character.offsetByCodePoints(localInput, localCursor, 1)
        localInput = localInput.removeRange(localCursor, next)
        historyIndex = commandHistory.size
    }

    private fun moveLocalCursor(direction: Int) {
        if (direction < 0 && localCursor == 0) return
        if (direction > 0 && localCursor == localInput.length) return
        localCursor = Character.offsetByCodePoints(localInput, localCursor, direction)
            .coerceIn(0, localInput.length)
    }

    private fun moveLocalHistory(direction: Int) {
        if (commandHistory.isEmpty()) return
        if (direction < 0) {
            if (historyIndex == commandHistory.size) historyDraft = localInput
            historyIndex = (historyIndex - 1).coerceAtLeast(0)
        } else {
            historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size)
        }
        localInput = if (historyIndex == commandHistory.size) historyDraft else commandHistory[historyIndex]
        localCursor = localInput.length
    }

    private fun clearLocalInput() {
        localInput = ""
        localCursor = 0
        historyIndex = commandHistory.size
    }

    private fun submitLocalInput() {
        val line = localInput
        submittedLocalInput = line.takeIf { it.isNotEmpty() }
        if (line.isNotBlank() && commandHistory.lastOrNull() != line) commandHistory += line
        historyIndex = commandHistory.size
        historyDraft = ""
        clearLocalInput()
        sendRemote((line + "\r").toByteArray(Charsets.UTF_8))
    }

    private fun handleLocalControl(codePoint: Int, alt: Boolean) {
        if (alt) {
            insertLocalText(String(Character.toChars(codePoint)))
            return
        }
        val controlCharacter = if (codePoint in 1..26) 'a'.code + codePoint - 1 else codePoint
        when (controlCharacter.toChar().lowercaseChar()) {
            'a' -> localCursor = 0
            'e' -> localCursor = localInput.length
            'u' -> clearLocalInput()
            'w' -> {
                while (localCursor > 0 && localInput[localCursor - 1].isWhitespace()) deleteLocalPrevious()
                while (localCursor > 0 && !localInput[localCursor - 1].isWhitespace()) deleteLocalPrevious()
            }
            'c' -> {
                clearLocalInput()
                sendRemote(byteArrayOf(3))
            }
            'd' -> {
                if (localCursor < localInput.length) deleteLocalNext()
                else if (localInput.isEmpty()) sendRemote(byteArrayOf(4))
            }
            else -> sendRemote(codePointBytes(codePoint, ctrl = true, alt = false))
        }
    }

    /**
     * Creates the tiny local session TerminalView needs for its emulator. Everything the emulator
     * itself writes (cursor reports, mouse events, scroll arrows in vim/tmux...) goes to this
     * session's pty; a raw, non-echoing `cat` forwards it through a FIFO to the remote channel.
     */
    fun createSession(filesDir: java.io.File): TerminalSession {
        val script = runCatching {
            val fifo = java.io.File(filesDir, "remote-machines/term-${System.nanoTime()}.in")
            fifo.parentFile?.mkdirs()
            fifo.delete()
            android.system.Os.mkfifo(fifo.absolutePath, 384)
            replyFifo = fifo
            startReplyReader(fifo)
            "stty raw -echo 2>/dev/null; exec cat > '${fifo.absolutePath}'"
        }.getOrElse { "stty raw -echo 2>/dev/null; exec cat > /dev/null" }
        // A null working directory triggers a JNI GetStringUTFChars abort on Android 11.
        val created = TerminalSession(
            // TerminalSession passes these as arguments to shellPath, so -c must be the first arg.
            // A leading "sh" makes the shell try to open a script file named "sh" and the FIFO
            // forwarder never starts.
            "/system/bin/sh", filesDir.absolutePath, arrayOf("-c", script), emptyArray(), 2_000, this
        )
        session = created
        return created
    }

    private fun startReplyReader(fifo: java.io.File) {
        Thread {
            runCatching {
                java.io.FileInputStream(fifo).use { input ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sendRemote(buffer.copyOf(count))
                    }
                }
            }
            fifo.delete()
        }.apply { isDaemon = true; name = "remote-terminal-replies" }.start()
    }

    fun attachRemote(terminal: RemoteTerminal) {
        remote = terminal
        closed = false
        // close() shuts the sender down, but this bridge is reused when the user retries after a lost connection.
        if (sender.isShutdown) sender = newSender()
        flushPending()
        reportSize()
    }

    /** Sends the emulator's current size to the remote pty, off the main thread. */
    private fun reportSize() {
        val terminal = remote ?: return
        val emulator = session?.emulator ?: return
        val columns = emulator.mColumns
        val rows = emulator.mRows
        Thread { terminal.resize(columns, rows) }.apply { isDaemon = true }.start()
    }

    fun append(bytes: ByteArray) {
        if (closed) return
        submittedLocalInput = null
        val emulator = session?.emulator
        if (!emulatorReady || emulator == null) {
            pending.write(bytes)
            return
        }
        emulator.append(bytes, bytes.size)
        view?.onScreenUpdated()
    }

    fun close() {
        closed = true
        remote = null
        session?.finishIfRunning()
        session = null
        view = null
        replyFifo?.delete()
        replyFifo = null
        sender.shutdown()
    }

    private fun flushPending() {
        if (!emulatorReady || pending.size() == 0) return
        val bytes = pending.toByteArray()
        pending.reset()
        append(bytes)
    }

    // Socket writes throw NetworkOnMainThreadException on Android's main thread, and keys/paste
    // arrive on the main thread. They used to be swallowed by runCatching, so nothing typed or
    // pasted ever reached the server. A single-thread executor keeps the writes ordered.
    @Volatile private var sender = newSender()

    private fun newSender() = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "remote-terminal-input").apply { isDaemon = true }
    }

    private fun sendRemote(bytes: ByteArray) {
        if (closed) return
        val output = remote?.output ?: return
        try {
            sender.execute {
                runCatching {
                    output.write(bytes)
                    output.flush()
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun sendText(value: String) {
        if (inputMode == TerminalInputMode.LOCAL) insertLocalText(value)
        else sendRemote(value.toByteArray(Charsets.UTF_8))
    }

    private fun sendCodePoint(codePoint: Int, ctrl: Boolean, alt: Boolean) {
        if (inputMode == TerminalInputMode.LOCAL) {
            if (ctrl) {
                handleLocalControl(codePoint, alt)
                clearModifiers()
                return
            }
            when (codePoint) {
                '\n'.code, '\r'.code -> submitLocalInput()
                '\b'.code, 0x7f -> deleteLocalPrevious()
                0x1b -> clearLocalInput()
                else -> insertLocalText(String(Character.toChars(codePoint)))
            }
            clearModifiers()
            return
        }
        sendRemote(codePointBytes(codePoint, ctrl, alt))
    }

    private fun codePointBytes(codePoint: Int, ctrl: Boolean, alt: Boolean): ByteArray {
        var value = codePoint
        if (value == '\n'.code) value = '\r'.code
        if (ctrl) {
            value = when {
                value in 'a'.code..'z'.code -> value - 'a'.code + 1
                value in 'A'.code..'Z'.code -> value - 'A'.code + 1
                value == ' '.code || value == '2'.code -> 0
                value == '['.code || value == '3'.code -> 27
                value == '\\'.code || value == '4'.code -> 28
                value == ']'.code || value == '5'.code -> 29
                value == '^'.code || value == '6'.code -> 30
                value == '_'.code || value == '7'.code || value == '/'.code -> 31
                else -> value
            }
        }
        val prefix = if (alt) "\u001b" else ""
        return (prefix + String(Character.toChars(value))).toByteArray(Charsets.UTF_8)
    }

    fun sendChar(char: Char) {
        sendCodePoint(char.code, ctrlActive, altActive)
        clearModifiers()
    }

    private fun clearModifiers() {
        ctrlActive = false
        altActive = false
    }

    /** Sends an escape sequence, adding xterm modifier parameters / application cursor mode. */
    fun sendSpecial(sequence: String, ctrl: Boolean = ctrlActive, alt: Boolean = altActive) {
        if (inputMode == TerminalInputMode.LOCAL && sequence in setOf("\r", "\n")) {
            submitLocalInput()
            clearModifiers()
            return
        }
        if (inputMode == TerminalInputMode.LOCAL && !ctrl && !alt) {
            when (sequence) {
                "\r", "\n" -> submitLocalInput()
                "\u0009" -> insertLocalText("\t")
                "\u007f", "\b" -> deleteLocalPrevious()
                "\u001b[3~" -> deleteLocalNext()
                "\u001b[D" -> moveLocalCursor(-1)
                "\u001b[C" -> moveLocalCursor(1)
                "\u001b[H" -> localCursor = 0
                "\u001b[F" -> localCursor = localInput.length
                "\u001b[A" -> moveLocalHistory(-1)
                "\u001b[B" -> moveLocalHistory(1)
                "\u001b" -> clearLocalInput()
                else -> sendRemote(sequence.toByteArray(Charsets.UTF_8))
            }
            clearModifiers()
            return
        }
        val match = Regex("^\u001b\\[(\\d*)([A-Za-z~])$").matchEntire(sequence)
        val out = when {
            match != null && (ctrl || alt) -> {
                val number = match.groupValues[1].ifEmpty { "1" }
                val modifier = 1 + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
                "\u001b[$number;$modifier${match.groupValues[2]}"
            }
            match != null && match.groupValues[1].isEmpty() && match.groupValues[2] in "ABCD" &&
                view?.mEmulator?.isCursorKeysApplicationMode == true -> "\u001bO${match.groupValues[2]}"
            alt && sequence.length == 1 -> "\u001b$sequence"
            else -> sequence
        }
        sendRemote(out.toByteArray(Charsets.UTF_8))
        clearModifiers()
    }

    private fun sendKey(keyCode: Int, event: KeyEvent): Boolean {
        val sequence = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
            else -> null
        }
        val ctrl = event.isCtrlPressed || ctrlActive
        val alt = event.isAltPressed || altActive
        if (sequence != null) {
            sendSpecial(sequence, ctrl, alt)
            return true
        }
        val unicode = event.unicodeChar
        if (unicode != 0) {
            sendCodePoint(unicode, ctrl, alt)
            clearModifiers()
        }
        return true
    }

    // TerminalSessionClient
    override fun onTextChanged(changedSession: TerminalSession) { view?.onScreenUpdated() }
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        (context.getSystemService(ClipboardManager::class.java))?.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(::sendText)
    }
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String, message: String) = Unit
    override fun logWarn(tag: String, message: String) = Unit
    override fun logInfo(tag: String, message: String) = Unit
    override fun logDebug(tag: String, message: String) = Unit
    override fun logVerbose(tag: String, message: String) = Unit
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
    override fun logStackTrace(tag: String, e: Exception) = Unit

    // TerminalViewClient
    // Pinch changes the font size (like Termux) instead of scaling the view.
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            onZoomStep(scale > 1f)
            return 1.0f
        }
        return scale
    }

    private fun focusAndShowKeyboard() {
        val terminalView = view ?: return
        terminalView.post {
            if (closed) return@post
            terminalView.requestFocus()
            ViewCompat.getWindowInsetsController(terminalView)?.show(WindowInsetsCompat.Type.ime())
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(terminalView, 0)
        }
    }

    fun toggleKeyboard() {
        val terminalView = view ?: return
        val visible = ViewCompat.getRootWindowInsets(terminalView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (visible) {
            ViewCompat.getWindowInsetsController(terminalView)?.hide(WindowInsetsCompat.Type.ime())
                ?: context.getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(terminalView.windowToken, 0)
        } else {
            focusAndShowKeyboard()
        }
    }

    override fun onSingleTapUp(e: MotionEvent) { focusAndShowKeyboard() }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = sendKey(keyCode, e)
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = true
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = ctrlActive
    override fun readAltKey(): Boolean = altActive
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        sendCodePoint(codePoint, ctrlDown, altActive)
        clearModifiers()
        return true
    }
    // TerminalView calls this on EVERY size change (e.g. when the keyboard shows or hides). Reset
    // the local renderer once, but don't focus the view or open the IME during terminal setup.
    override fun onEmulatorSet() {
        val first = !emulatorReady
        emulatorReady = true
        if (first) {
            session?.reset()
            flushPending()
        }
        reportSize()
    }
}

@Composable
private fun RemoteSection(title: String, content: @Composable () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun RemoteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    readOnly: Boolean = false,
    visual: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) 20 else 1,
    singleLine: Boolean = minLines == 1,
    font: FontFamily? = null
) {
    SelectableOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        supportingText = supporting?.let { { Text(it) } },
        readOnly = readOnly,
        visualTransformation = visual,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        textStyle = if (font == null) LocalTextStyle.current else LocalTextStyle.current.copy(fontFamily = font)
    )
}

@Composable
private fun RemoteToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange)
    }
}

private data class SshHighlightColors(
    val comment: Color,
    val keyword: Color,
    val value: Color
)

private class SshConfigVisualTransformation(
    private val colors: SshHighlightColors
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        highlightSshConfig(text.text, colors),
        OffsetMapping.Identity
    )
}

private fun highlightSshConfig(
    text: String,
    colors: SshHighlightColors
): AnnotatedString = buildAnnotatedString {
    text.lineSequence().forEachIndexed { index, line ->
        val content = line.trimStart()
        when {
            content.startsWith("#") -> withStyle(SpanStyle(color = colors.comment)) { append(line) }
            content.substringBeforeAnyWhitespace().lowercase() in SSH_CONFIG_KEYWORDS -> {
                val leading = line.take(line.length - content.length)
                val split = content.indexOfFirst { it == ' ' || it == '\t' }
                append(leading)
                if (split < 0) {
                    withStyle(SpanStyle(color = colors.keyword)) { append(content) }
                } else {
                    withStyle(SpanStyle(color = colors.keyword)) { append(content.substring(0, split)) }
                    withStyle(SpanStyle(color = colors.value)) { append(content.substring(split)) }
                }
            }
            else -> withStyle(SpanStyle(color = colors.value)) { append(line) }
        }
        if (index < text.lineSequence().count() - 1) append('\n')
    }
}

private val SSH_CONFIG_KEYWORDS = setOf(
    "host", "hostname", "user", "port", "proxycommand", "localcommand", "identityfile",
    "stricthostkeychecking", "compression", "serveraliveinterval"
)

private fun String.substringBeforeAnyWhitespace(): String =
    substringBeforeAny { it == ' ' || it == '\t' }

private inline fun String.substringBeforeAny(predicate: (Char) -> Boolean): String {
    val index = indexOfFirst(predicate)
    return if (index < 0) this else substring(0, index)
}
