@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.starfall.generative.features.machines.details.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.features.machines.shared.logic.RemoteMachinesViewModel
import org.starfall.generative.features.machines.shared.ui.RemoteMouseTouchListener
import org.starfall.generative.features.machines.shared.ui.RemoteTerminalBridge
import org.starfall.generative.features.machines.shared.ui.RemoteTerminalSessions
import org.starfall.generative.features.machines.shared.ui.TerminalSlot
import org.starfall.generative.features.machines.shared.ui.RemoteTextField
import org.starfall.generative.features.machines.shared.ui.TerminalInputMode
import org.starfall.generative.integrations.remote.REMOTE_TERMINAL_UI_PREFERENCES
import org.starfall.generative.integrations.remote.RemoteSecretRequest
import org.starfall.generative.integrations.remote.RemoteTerminal
import org.starfall.generative.integrations.remote.remoteTerminalImePreferenceKey
import org.starfall.generative.integrations.remote.remoteTerminalInputModePreferenceKey
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive

/** Only the terminal viewport stays dark in every theme; all chrome follows the app theme. */
private val TerminalBackground = Color(0xFF0B0D0F)
private val TerminalChromeText = Color.White

private class PendingSecretRequest(
    val request: RemoteSecretRequest,
    val answer: CompletableDeferred<String?>
)

@Composable
internal fun RemoteTerminalScreen(machine: RemoteMachine, viewModel: RemoteMachinesViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val terminalPrefs = remember {
        context.getSharedPreferences(REMOTE_TERMINAL_UI_PREFERENCES, Context.MODE_PRIVATE)
    }
    // Sessions live in a process-wide registry so they survive leaving this screen.
    val terminalSlots = RemoteTerminalSessions.slotsFor(machine.id)
    val activeTerminalId = RemoteTerminalSessions.activeIdFor(machine.id)
    var connecting by remember(machine.id) { mutableStateOf(false) }
    var openError by remember(machine.id) { mutableStateOf<String?>(null) }
    val error = openError ?: if (RemoteTerminalSessions.connectionLost(machine.id)) {
        stringResource(R.string.remote_machine_terminal_connection_lost)
    } else null
    var secretRequest by remember(machine.id) { mutableStateOf<PendingSecretRequest?>(null) }
    var passwordInput by remember(machine.id) { mutableStateOf("") }
    var showPromptPassword by remember(machine.id) { mutableStateOf(false) }
    // Null means "follow the layout": open on wide screens, collapsed on narrow ones.
    var drawerExpanded by remember(machine.id) { mutableStateOf<Boolean?>(null) }
    val sessionsDrawerOpen = drawerExpanded ?: (LocalConfiguration.current.screenWidthDp >= 720)
    var localImeEnabled by remember(machine.id) {
        mutableStateOf(terminalPrefs.getBoolean(remoteTerminalImePreferenceKey(machine.id), true))
    }
    var fontDp by remember {
        mutableIntStateOf(terminalPrefs.getInt("font_dp", 14).coerceIn(MIN_TERMINAL_FONT_DP, MAX_TERMINAL_FONT_DP))
    }
    val textSizePx = (fontDp * density).toInt()
    val activeSlot = terminalSlots.firstOrNull { it.terminal.id == activeTerminalId }
    val activeBridge = activeSlot?.bridge
    val currentSecretRequest by rememberUpdatedState(secretRequest)

    fun setFontDp(value: Int) {
        fontDp = value.coerceIn(MIN_TERMINAL_FONT_DP, MAX_TERMINAL_FONT_DP)
        terminalPrefs.edit().putInt("font_dp", fontDp).apply()
    }

    fun closeSlot(slot: TerminalSlot) = RemoteTerminalSessions.close(machine.id, slot)

    // Leaving the screen keeps the sessions alive; only a pending password prompt is dropped.
    fun leaveScreen() {
        currentSecretRequest?.answer?.complete(null)
    }

    fun openTerminal() {
        scope.launch {
            connecting = true
            openError = null
            RemoteTerminalSessions.clearConnectionLost(machine.id)
            try {
                val opened = viewModel.openTerminal(machine) { request ->
                    // The SSH thread pauses here only after the server requests an unsaved secret.
                    val pending = PendingSecretRequest(request, CompletableDeferred())
                    withContext(Dispatchers.Main) {
                        passwordInput = ""
                        showPromptPassword = false
                        secretRequest = pending
                    }
                    try {
                        pending.answer.await()
                    } finally {
                        withContext(NonCancellable + Dispatchers.Main) {
                            if (secretRequest === pending) secretRequest = null
                        }
                    }
                }
                // Read the live preference so sessions opened after a toolbar toggle use it too.
                val currentInputMode = when (terminalPrefs.getString(remoteTerminalInputModePreferenceKey(machine.id), null)) {
                    TerminalInputMode.LOCAL.name -> TerminalInputMode.LOCAL
                    else -> TerminalInputMode.REMOTE
                }
                if (!isActive) {
                    // The user left while connecting: do not leave an unowned shell behind.
                    opened.close()
                    throw CancellationException()
                }
                val newBridge = RemoteTerminalBridge(context.applicationContext, currentInputMode).also { bridge ->
                    bridge.forceMouse = terminalPrefs.getBoolean("force_mouse", false)
                    bridge.attachRemote(opened)
                }
                RemoteTerminalSessions.register(machine.id, opened, newBridge)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                openError = failure.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.remote_machine_terminal_connection_failed)
            } finally {
                connecting = false
            }
        }
    }

    // Phone back button: disconnect explicitly (same as the app bar back arrow).
    BackHandler(enabled = LocalScreenTransitionActive.current) { leaveScreen(); onBack() }
    DisposableEffect(machine.id) {
        onDispose { leaveScreen() }
    }
    // Pinch zoom callbacks belong to this composition, so (re)bind them on every entry.
    DisposableEffect(terminalSlots) {
        terminalSlots.forEach { slot ->
            slot.bridge.onZoomStep = { increase -> setFontDp(fontDp + if (increase) 1 else -1) }
        }
        onDispose { terminalSlots.forEach { it.bridge.onZoomStep = {} } }
    }
    LaunchedEffect(machine.id) {
        if (RemoteTerminalSessions.slotsFor(machine.id).isEmpty()) openTerminal()
    }
    // Keep IME/keyboard focus on the session the user is looking at, not on a hidden one.
    LaunchedEffect(activeTerminalId, terminalSlots) {
        activeSlot?.bridge?.focusTerminalView()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            machine.name.ifBlank { stringResource(R.string.remote_machine_unnamed) },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${machine.username.ifBlank { "?" }}@${machine.displayHost}:${machine.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { leaveScreen(); onBack() }) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { drawerExpanded = !sessionsDrawerOpen }) {
                        FluentIcon(Icons.Outlined.FormatListBulleted, stringResource(R.string.remote_machine_terminal_toggle_sessions))
                    }
                    IconButton(enabled = !connecting, onClick = { openTerminal() }) {
                        FluentIcon(Icons.Default.Add, stringResource(R.string.remote_machine_terminal_add))
                    }
                    IconButton(enabled = activeSlot != null, onClick = { activeSlot?.let { closeSlot(it) } }) {
                        FluentIcon(Icons.Default.Close, stringResource(R.string.remote_machine_terminal_close))
                    }
                }
            )
        }
    ) { padding ->
        Row(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
        ) {
                AnimatedVisibility(
                    visible = sessionsDrawerOpen,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    TerminalSessionsDrawer(
                        machine = machine,
                        slots = terminalSlots,
                        activeTerminalId = activeTerminalId,
                        connecting = connecting,
                        onSelect = { slot ->
                            RemoteTerminalSessions.setActive(machine.id, slot.terminal.id)
                            slot.bridge.focusTerminalView()
                        },
                        onClose = { slot -> closeSlot(slot) },
                        onNew = { openTerminal() }
                    )
                }
                if (sessionsDrawerOpen) VerticalDivider(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (activeBridge != null) {
                        TerminalToolbar(
                            bridge = activeBridge,
                            sessionsDrawerOpen = sessionsDrawerOpen,
                            onToggleSessions = { drawerExpanded = !sessionsDrawerOpen },
                            onNewTerminal = { openTerminal() },
                            localImeEnabled = localImeEnabled,
                            onToggleLocalIme = {
                                localImeEnabled = !localImeEnabled
                                terminalPrefs.edit()
                                    .putBoolean(remoteTerminalImePreferenceKey(machine.id), localImeEnabled)
                                    .apply()
                            },
                            onInputModeChange = { mode ->
                                // Input mode is a per-machine preference: apply it to every
                                // session, not just the visible one.
                                if (activeBridge.setInputMode(mode)) {
                                    terminalSlots.forEach { slot ->
                                        if (slot.bridge !== activeBridge) slot.bridge.setInputMode(mode)
                                    }
                                    terminalPrefs.edit()
                                        .putString(remoteTerminalInputModePreferenceKey(machine.id), mode.name)
                                        .apply()
                                }
                            },
                            onToggleForceMouse = {
                                val enabled = !activeBridge.forceMouse
                                terminalSlots.forEach { slot -> slot.bridge.forceMouse = enabled }
                                terminalPrefs.edit().putBoolean("force_mouse", enabled).apply()
                            },
                            fontDp = fontDp,
                            onFontChange = { delta -> setFontDp(fontDp + delta) }
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().background(TerminalBackground)) {
                        // Every session keeps its own TerminalView (and emulator) alive while it
                        // exists; inactive views are laid out at zero size so their scrollback
                        // and remote size survive switching. Previously a single shared view
                        // stayed bound to the first session, so after adding or switching
                        // sessions typed input went to a hidden terminal.
                        terminalSlots.forEach { slot ->
                            key(slot.terminal.id) {
                                AndroidView(
                                    factory = { viewContext ->
                                        val view = TerminalView(viewContext, null)
                                        val slotBridge = slot.bridge
                                        view.setOnTouchListener(RemoteMouseTouchListener(viewContext, view, slotBridge))
                                        view.isFocusable = true
                                        view.isFocusableInTouchMode = true
                                        view.setBackgroundColor(android.graphics.Color.rgb(11, 13, 15))
                                        view.setTextSize(textSizePx)
                                        view.tag = textSizePx
                                        view.setTerminalViewClient(slotBridge)
                                        slotBridge.view = view
                                        view.attachSession(slotBridge.createSession(viewContext.filesDir))
                                        view
                                    },
                                    modifier = if (slot.terminal.id == activeTerminalId) {
                                        Modifier.fillMaxSize()
                                    } else {
                                        Modifier.size(0.dp)
                                    },
                                    onRelease = { view -> slot.bridge.detachView(view) },
                                    update = { view ->
                                        // Do not request focus here: update also runs while the
                                        // local IME editor is focused, and stealing focus back
                                        // would break typing there. Focus moves only when the
                                        // active session changes (see the LaunchedEffect above)
                                        // or when the user taps the terminal.
                                        slot.bridge.view = view
                                        if (view.tag != textSizePx) {
                                            view.tag = textSizePx
                                            view.setTextSize(textSizePx)
                                        }
                                    }
                                )
                            }
                        }
                        if (activeBridge != null && activeBridge.inputMode == TerminalInputMode.LOCAL && localImeEnabled) {
                            LocalInputEditor(activeBridge)
                        } else {
                            activeBridge?.localDraftForDisplay()?.let { draft ->
                                Surface(
                                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    val label = stringResource(R.string.remote_machine_terminal_local_input_label) + ": "
                                    val draftScroll = rememberScrollState()
                                    // Wrap long drafts; cap the height and keep the cursor line (the end) in view.
                                    LaunchedEffect(draft) { draftScroll.scrollTo(draftScroll.maxValue) }
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(label) }
                                            append(draft)
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        softWrap = true,
                                        modifier = Modifier
                                            .heightIn(max = 140.dp)
                                            .verticalScroll(draftScroll)
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        if (connecting && secretRequest == null) {
                            Surface(
                                Modifier.align(Alignment.Center),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.remote_machine_terminal_connecting))
                                }
                            }
                        }
                        if (terminalSlots.isEmpty() && !connecting && error == null && secretRequest == null) {
                            Column(
                                Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FluentIcon(
                                    Icons.Outlined.Terminal, null,
                                    tint = TerminalChromeText.copy(alpha = 0.55f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    stringResource(R.string.remote_machine_terminal_no_sessions),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TerminalChromeText
                                )
                                Text(
                                    stringResource(R.string.remote_machine_terminal_no_sessions_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TerminalChromeText.copy(alpha = 0.65f)
                                )
                                FilledTonalButton(onClick = { openTerminal() }) {
                                    FluentIcon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.remote_machine_terminal_add))
                                }
                            }
                        }
                        if (!connecting && error != null && secretRequest == null) {
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
                                    Button(onClick = { openTerminal() }) {
                                        Text(stringResource(R.string.common_retry))
                                    }
                                }
                            }
                        }
                    }
                    if (activeBridge != null) TerminalExtraKeys(activeBridge)
                }
            }
    }

    secretRequest?.let { pending ->
        val request = pending.request
        AppAlertDialog(
            onDismissRequest = {
                pending.answer.complete(null)
                onBack()
            },
            title = {
                Text(
                    stringResource(
                        if (request.passphrase) R.string.remote_machine_terminal_passphrase_title
                        else R.string.remote_machine_terminal_password_title
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.remote_machine_terminal_password_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RemoteTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = when {
                            request.passphrase -> stringResource(R.string.remote_machine_key_passphrase)
                            request.prompt.isBlank() -> stringResource(R.string.remote_machine_password)
                            else -> request.prompt.trim()
                        },
                        visual = if (showPromptPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPromptPassword = !showPromptPassword }) {
                                FluentIcon(
                                    if (showPromptPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (showPromptPassword) R.string.remote_machine_hide_password else R.string.remote_machine_show_password
                                    )
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = passwordInput.isNotEmpty(),
                    onClick = { pending.answer.complete(passwordInput) }
                ) {
                    Text(stringResource(R.string.remote_machine_terminal_connect))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pending.answer.complete(null)
                    onBack()
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

