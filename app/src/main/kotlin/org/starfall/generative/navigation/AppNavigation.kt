package org.starfall.generative.navigation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.starfall.generative.R
import org.starfall.generative.features.about.ui.AboutScreen
import org.starfall.generative.features.appdata.logic.AppDataTransferService
import org.starfall.generative.shared.model.BackupExportKind
import org.starfall.generative.features.appdata.ui.AppDataScreen
import org.starfall.generative.features.chatoptions.ui.ChatOptionsScreen
import org.starfall.generative.features.icons.ui.IconsScreen
import org.starfall.generative.features.machines.details.ui.MachineDetailsScreen
import org.starfall.generative.features.machines.details.ui.RemoteMachineSettings
import org.starfall.generative.features.machines.list.ui.RemoteMachinesScreen
import org.starfall.generative.features.machines.shared.logic.RemoteMachinesViewModel
import org.starfall.generative.shared.ui.terminal.RemoteTerminalScreen
import org.starfall.generative.features.menu.ui.AppMenuScreen
import org.starfall.generative.features.userprofile.ui.UserProfileScreen
import org.starfall.generative.features.mcp.editor.ui.ServerEditor
import org.starfall.generative.features.mcp.list.ui.ServerListScreen
import org.starfall.generative.features.providers.editor.ui.ProviderEditor
import org.starfall.generative.features.providers.list.ui.ProvidersScreen
import org.starfall.generative.features.providers.shared.logic.ConfigurationViewModel
import org.starfall.generative.features.settings.logic.SettingsViewModel
import org.starfall.generative.features.settings.ui.AppSettings
import org.starfall.generative.features.settings.ui.MainAppbar
import org.starfall.generative.features.speech.ui.SpeechServicesScreen
import org.starfall.generative.features.storage.ui.MediaStorageSheet
import org.starfall.generative.features.storage.ui.StorageScreen
import org.starfall.generative.features.tools.ui.ToolsEditor
import org.starfall.generative.features.tools.ui.MediaRequestChainSettings
import org.starfall.generative.features.tools.ui.MediaRequestProfileEditor
import org.starfall.generative.features.home.chat.logic.ChatViewModel
import org.starfall.generative.features.home.chat.logic.contextWindowStatus
import org.starfall.generative.features.home.chat.ui.ChatScreen
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.features.home.drawer.ui.ConversationSideDrawer
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.model.ConversationListItem
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.media.MediaGenerationMode
import org.starfall.generative.shared.model.media.MediaRequestProfile
import org.starfall.generative.shared.model.media.MediaKind
import org.starfall.generative.shared.model.media.defaultMediaProfile
import org.starfall.generative.shared.model.media.withDefaultMediaProfile
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.theme.modalScrimColor
import org.starfall.generative.shared.ui.InstructionControls
import org.starfall.generative.shared.ui.GenerativeLoadingIndicator
import org.starfall.generative.shared.ui.LocalInstructionControls
import org.starfall.generative.shared.ui.LocalToolControls
import org.starfall.generative.shared.ui.ToolControls
import org.starfall.generative.shared.ui.layout.SlideScreenContent
import org.starfall.generative.shared.ui.media.ChatAttachmentSelection
import org.starfall.generative.shared.ui.media.LocalChatAttachmentSelection

/** Stable route names are persisted by Navigation across Activity/process recreation. */
private data class MachineEditorPage(val machine: RemoteMachine, val isNew: Boolean)
private data class ProviderEditorPage(val provider: ModelProvider, val isNew: Boolean)
private enum class RemoteMachineSettingsPage { SETTINGS }

/** Distinct NavHost destinations, not conditional pages inside Feature Tools. */
internal object MediaProfileRoutes {
    const val LIBRARY = "system_tools/profiles/{kind}"
    const val EDITOR = "system_tools/profiles/{kind}/edit/{profileId}"
    fun library(kind: MediaKind) = "system_tools/profiles/${kind.name.lowercase()}"
    fun editor(kind: MediaKind, profileId: String) = "${library(kind)}/edit/${Uri.encode(profileId)}"
    fun library(video: Boolean) = "system_tools/profiles/${if (video) "video" else "image"}"
    fun editor(video: Boolean, profileId: String) =
        "${library(video)}/edit/${Uri.encode(profileId)}"
}

internal val NullableFilePathSaver = Saver<File?, String>(
    save = { it?.absolutePath },
    restore = { path -> path.takeIf { it.isNotBlank() }?.let(::File) }
)

enum class AppDestination(val route: String) {
    CHAT("chat"),
    PROVIDERS("providers"),
    MCP("mcp"),
    SPEECH("speech"),
    FEATURES("features"),
    USER_PROFILE("user_profile"),
    CHAT_OPTIONS("chat_options"),
    TOOLS_EDITOR("system_tools"),
    STORAGE("storage"),
    REMOTE_MACHINES("remote_machines"),
    MENU("menu"),
    APP_SETTINGS("AppSettings"),
    ICONS("Icons"),
    APP_DATA("AppData"),
    ABOUT("About"),
}

/** The two top-level pages; pages under Menu are navigated inside its own NavHost. */
internal const val CHAT_PAGE = 0
internal const val MENU_PAGE = 1

/** The native Pager owns both page swipes; Chat body filters only leftward drags. */
internal fun homePagerSwipeEnabled(settledPage: Int, menuRoute: String?): Boolean =
    settledPage == CHAT_PAGE || menuRoute == AppDestination.FEATURES.route

/** Route gestures at their start; never switch owners when the finger reverses direction. */
internal fun Modifier.homePagerGestureRouting(
    chatActive: Boolean, appBarBottomPx: Int, drawerEdgePx: Int, onPagerAllowed: (Boolean) -> Unit
): Modifier = pointerInput(chatActive, appBarBottomPx, drawerEdgePx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val onAppBar = chatActive && down.position.y < appBarBottomPx && down.position.x > drawerEdgePx
        if (chatActive) onPagerAllowed(onAppBar)
        try {
            var directionChosen = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val dx = (event.changes.firstOrNull { it.id == down.id }?.position?.x ?: down.position.x) - down.position.x
                if (onAppBar && !directionChosen && abs(dx) > viewConfiguration.touchSlop / 2) {
                    directionChosen = true
                    if (dx > 0f) onPagerAllowed(false) // Drawer owns this entire gesture.
                }
            } while (event.changes.any { it.pressed })
        } finally {
            if (chatActive) onPagerAllowed(true) // Also restore on cancellation/navigation.
        }
    }
}

/** System Back at the Menu root has the same destination and animation as a rightward tab swipe. */
internal fun homePagerBackToChatEnabled(currentPage: Int, menuRoute: String?): Boolean =
    currentPage == MENU_PAGE && menuRoute == AppDestination.FEATURES.route

/** Longest a chat selection waits for the drawer to finish closing before it is applied anyway. */
private const val DRAWER_CLOSE_WAIT_MS = 700L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    viewModel: ChatViewModel,
    configurationViewModel: ConfigurationViewModel,
    settingsViewModel: SettingsViewModel,
    remoteMachinesViewModel: RemoteMachinesViewModel,
    toolFiles: ToolFiles,
    appDataTransfer: AppDataTransferService,
    pendingTerminalMachineId: String? = null,
    onTerminalLaunchHandled: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showMediaSheet by rememberSaveable { mutableStateOf(false) }
    val drawerFocusManager = LocalFocusManager.current
    // Opening the sidebar (button or swipe) takes focus away from the chat input.
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) drawerFocusManager.clearFocus(force = true)
    }
    val coroutineScope = rememberCoroutineScope()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // A built-in HorizontalPager owns both full-screen pages, exactly like tab navigation.
    // Its offscreen page remains composed (and retains scroll, draft and nested NavHost).
    val homePager = rememberPagerState(initialPage = CHAT_PAGE, pageCount = { 2 })
    val chatOnTop = homePager.currentPage == CHAT_PAGE
    var chatPagerGestureAllowed by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val appBarBottomPx = with(density) { 72.dp.roundToPx() } + WindowInsets.statusBars.getTop(density)
    val drawerEdgePx = with(density) { 32.dp.roundToPx() }
    fun showHomePage(page: Int) {
        coroutineScope.launch { homePager.animateScrollToPage(page) }
    }
    fun navigate(destination: AppDestination) {
        if (destination == AppDestination.CHAT || destination == AppDestination.FEATURES) {
            navController.popBackStack(AppDestination.FEATURES.route, inclusive = false)
            showHomePage(if (destination == AppDestination.CHAT) CHAT_PAGE else MENU_PAGE)
        } else {
            navController.navigate(destination.route) { launchSingleTop = true }
            showHomePage(MENU_PAGE)
        }
    }

    fun startNewChat() {
        viewModel.startNewChat()
        navigate(AppDestination.CHAT)
    }

    fun startTemporaryChat() {
        viewModel.startTemporaryChat()
        navigate(AppDestination.CHAT)
    }

    fun openConfiguration(destination: AppDestination) {
        showHomePage(MENU_PAGE)
        navController.navigate(destination.route) { launchSingleTop = true }
    }

    fun openTerminal(machine: RemoteMachine) {
        showHomePage(MENU_PAGE)
        navController.navigate("terminal/${Uri.encode(machine.id)}") { launchSingleTop = true }
    }

    fun openSkillsSettings() {
        showHomePage(MENU_PAGE)
        navController.navigate("${AppDestination.CHAT_OPTIONS.route}?tab=skills") { launchSingleTop = true }
    }

    fun openInstructions(instructionId: String? = null) {
        coroutineScope.launch { drawerState.close() }
        val route = buildString {
            append(AppDestination.CHAT_OPTIONS.route)
            append("?tab=instructions")
            instructionId?.let { append("&instructionId=${Uri.encode(it)}") }
        }
        showHomePage(MENU_PAGE)
        navController.navigate(route) { launchSingleTop = true }
    }

    val conversations: List<ConversationListItem> by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConvState = viewModel.currentConversation.collectAsStateWithLifecycle()
    val loadingConversationId by viewModel.loadingConversationId.collectAsStateWithLifecycle()
    // Streaming republishes the conversation about 20 times a second. Reading it in this function would
    // recompose the drawer, navigation and every CompositionLocal below on each tick, which slows the whole
    // app. Only values that rarely change are derived here; the chat screen reads the conversation itself.
    val currentConvId by remember { derivedStateOf { currentConvState.value?.id } }
    val currentConvModel by remember { derivedStateOf { currentConvState.value?.let { it.providerId to it.modelId } } }
    val currentConvInstructionIds by remember { derivedStateOf { currentConvState.value?.selectedInstructionIds } }
    val chatAttachments = remember { ChatAttachmentSelection() }
    var mcpEditor by remember { mutableStateOf<Pair<McpInfo, Boolean>?>(null) }
    var pendingNewProvider by remember { mutableStateOf<ProviderEditorPage?>(null) }
    LaunchedEffect(currentConvId) { chatAttachments.clear() }
    val isGenerating: Boolean by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generatingConversationIds by viewModel.generatingConversationIds.collectAsStateWithLifecycle()
    val generatingConversationId = currentConvId.takeIf { isGenerating }
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val activeTestSpeechServiceId by viewModel.activeTestSpeechServiceId.collectAsStateWithLifecycle()
    val summaryProgress by viewModel.summaryProgress.collectAsStateWithLifecycle()
    val contextWindowStatus by viewModel.contextWindowStatus.collectAsStateWithLifecycle()
    val appPrefs: AppPreferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val providers: List<ModelProvider> by viewModel.providers.collectAsStateWithLifecycle()
    val providerGroups: List<ProviderGroup> by configurationViewModel.providerGroups.collectAsStateWithLifecycle()
    val mcpServers: List<McpInfo> by viewModel.mcpServers.collectAsStateWithLifecycle()
    val speechServices: List<SpeechService> by configurationViewModel.speechServices.collectAsStateWithLifecycle()
    val mcpToolsCache by configurationViewModel.mcpToolsCache.collectAsStateWithLifecycle()
    val mcpToolErrors by configurationViewModel.mcpToolErrors.collectAsStateWithLifecycle()
    val mcpToolsLoading by configurationViewModel.mcpToolsLoading.collectAsStateWithLifecycle()
    val remoteMachineState by remoteMachinesViewModel.state.collectAsStateWithLifecycle()
    val connectedMachineIds by remoteMachinesViewModel.connectedMachineIds.collectAsStateWithLifecycle()
    val connectedMachines = remoteMachineState.machines.filter { it.id in connectedMachineIds }
    var remoteMachinePage by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(pendingTerminalMachineId, remoteMachineState.machines) {
        val machine = remoteMachineState.machines.firstOrNull { it.id == pendingTerminalMachineId }
        if (machine != null) {
            drawerState.close()
            openTerminal(machine)
            onTerminalLaunchHandled()
        }
    }

    val toolSettings by viewModel.toolSettings.collectAsStateWithLifecycle()

    fun persistMusicProfiles(profiles: Map<String, MediaRequestProfile>, preferredId: String) {
        val latest = toolSettings.system["generate_music"] ?: SystemToolConfig()
        viewModel.setSystemTool("generate_music", latest.withMusicProfiles(profiles, preferredId))
    }

    fun persistMediaProfiles(video: Boolean, updatedProfiles: Map<String, MediaRequestProfile>, preferredId: String) {
        val toolName = if (video) "generate_video" else "generate_image"
        // Read the latest config on every write; an editor must never overwrite model or toggle changes.
        val config = toolSettings.system[toolName] ?: SystemToolConfig()
        val profiles = updatedProfiles.withDefaultMediaProfile(video)
        val selectedId = preferredId.takeIf { it in profiles } ?: defaultMediaProfile(video).id
        val updated = config.withMediaProfiles(video, profiles)
        val next = if (video) updated.copy(
            activeVideoRequestProfileId = selectedId,
            videoGenerationMode = updated.mediaProfile(true, selectedId).mode
        ) else updated.copy(
            activeImageRequestProfileId = selectedId,
            imageGenerationMode = updated.mediaProfile(false, selectedId).mode
        )
        viewModel.setSystemTool(toolName, next)
        // If a profile was deleted, reset only the model references that used it.
        val kind = if (video) ModelType.VIDEO_GENERATION else ModelType.IMAGE_GENERATION
        providers.forEach { source ->
            source.config.modelConfigs.forEach { (id, model) ->
                if (model.modelType == kind && model.mediaRequestProfileId.isNotBlank() &&
                    model.mediaRequestProfileId !in profiles) {
                    val fallback = next.mediaProfile(video, "")
                    configurationViewModel.saveModelConfiguration(source.id, id, model.copy(
                        mediaRequestProfileId = fallback.id,
                        mediaApiType = fallback.mode
                    ))
                }
            }
        }
    }
    val queuedMessages by viewModel.queuedMessages.collectAsStateWithLifecycle()
    val importedProviderId by configurationViewModel.importedProviderId.collectAsStateWithLifecycle()
    val providerImportFailed by configurationViewModel.providerImportFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by rememberSaveable(stateSaver = NullableFilePathSaver) { mutableStateOf<File?>(null) }
    var exportPickerOpen by rememberSaveable { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf(false) }
    val preparingExport by appDataTransfer.preparingExport.collectAsStateWithLifecycle()
    val preparedExport by appDataTransfer.preparedExport.collectAsStateWithLifecycle()
    val importInProgress by appDataTransfer.importInProgress.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val archive = pendingExport
        val wasWaiting = exportPickerOpen
        exportPickerOpen = false
        if (uri == null || !wasWaiting) {
            pendingExport = null
            archive?.delete()
            return@rememberLauncherForActivityResult
        }
        if (archive == null || !archive.isFile) {
            pendingExport = null
            Toast.makeText(context, R.string.export_write_failed, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        appDataTransfer.writeExportToUri(uri, archive)
        pendingExport = null
    }

    LaunchedEffect(preparedExport, exportPickerOpen) {
        if (preparedExport != null && !exportPickerOpen && pendingExport == null) {
            val archive = appDataTransfer.consumePreparedExport() ?: return@LaunchedEffect
            pendingExport = archive
            exportPickerOpen = true
            try {
                exportLauncher.launch(BackupExportKind.FULL_BACKUP.fileName)
            } catch (_: Exception) {
                exportPickerOpen = false
                pendingExport = null
                archive.delete()
                Toast.makeText(context, R.string.export_write_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(exportPickerOpen, pendingExport?.absolutePath, preparedExport, preparingExport) {
        if (!exportPickerOpen && pendingExport == null && preparedExport == null && !preparingExport) {
            withContext(Dispatchers.IO) {
                appDataTransfer.cleanupAbandonedExports()
            }
        }
    }

    fun exportData() {
        if (preparingExport || exportPickerOpen || pendingExport != null || importInProgress) return
        appDataTransfer.prepareExport()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val shouldImport = pendingImport
        pendingImport = false
        if (uri == null || !shouldImport || importInProgress) return@rememberLauncherForActivityResult
        appDataTransfer.importFromUri(
            uri = uri,
            beforeFullRestore = viewModel::prepareForRestore,
            afterFullRestore = viewModel::finishRestore
        )
    }

    fun importData() {
        if (pendingImport || importInProgress || preparingExport || exportPickerOpen) return
        pendingImport = true
        try {
            importLauncher.launch(arrayOf("application/zip", "application/json", "text/*"))
        } catch (_: Exception) {
            pendingImport = false
            Toast.makeText(context, R.string.export_open_file_failed, Toast.LENGTH_LONG).show()
        }
    }
    // The app preference is the default for new chats. Existing chats keep showing
    // the model they last used, independently of that default.
    val conversationModel =
        currentConvModel?.takeIf { (_, modelId) -> appPrefs.persistSelectedModel && modelId.isNotBlank() }
            ?.let { (conversationProviderId, conversationModelId) ->
                providers.firstOrNull { provider ->
                    provider.id == conversationProviderId &&
                            provider.config.modelIds?.contains(conversationModelId) != false &&
                            (provider.config.modelConfigs[conversationModelId]?.modelType
                                ?: ModelType.TEXT_GENERATION) == ModelType.TEXT_GENERATION
                }?.let { conversationProviderId to conversationModelId }
            }
    val chatProviderId = conversationModel?.first ?: appPrefs.selectedModel.providerId
    val chatModelId = conversationModel?.second ?: appPrefs.selectedModel.modelId
    LaunchedEffect(Unit) {
        org.starfall.generative.shared.storage.LocalWriteErrors.errors.collect {
            Toast.makeText(context, R.string.save_change_failed, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(importedProviderId) {
        importedProviderId?.let { id ->
            drawerState.close()
            showHomePage(MENU_PAGE)
            navController.navigate("providers/edit/$id") { launchSingleTop = true }
            configurationViewModel.providerImportOpened(id)
        }
    }
    LaunchedEffect(providerImportFailed) {
        if (providerImportFailed) {
            Toast.makeText(context, context.getString(R.string.provider_import_failed), Toast.LENGTH_LONG).show()
            configurationViewModel.clearProviderImportFailed()
        }
    }
    LaunchedEffect(chatOnTop) {
        if (!chatOnTop) drawerState.close()
    }


    CompositionLocalProvider(
        org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences provides appPrefs,
        org.starfall.generative.shared.ui.layout.LocalItemTransparency provides appPrefs.itemTransparency,
        org.starfall.generative.shared.ui.background.LocalProfileModalImages provides
            org.starfall.generative.shared.ui.background.ProfileModalImages(
                appPrefs.userProfile.sheetBackground, appPrefs.userProfile.dialogBackground,
                appPrefs.userProfile.sheetTransparency, appPrefs.userProfile.dialogTransparency
            ),
        LocalChatAttachmentSelection provides chatAttachments,
        LocalInstructionControls provides InstructionControls(
            library = appPrefs.instructionLibraryForSelection(currentConvInstructionIds),
            setInstructionEnabled = viewModel::setInstructionEnabled,
            openInstructions = { openInstructions() },
            editInstruction = { instructionId -> openInstructions(instructionId) }
        ),
        LocalToolControls provides ToolControls(
            servers = mcpServers,
            machines = remoteMachineState.machines,
            settings = toolSettings,
            providers = providers,
            setSystem = viewModel::setSystemTool,
            setMcp = viewModel::setQuickMcp,
            setMachines = viewModel::setMachinesEnabled,
            setMachine = { machineId, enabled ->
                viewModel.setMachineEnabled(machineId, enabled, remoteMachineState.machines.map { it.id })
            },
            setMachineTool = viewModel::setMachineToolEnabled,
            setSendFile = viewModel::setSendFileEnabled,
            setWebSearch = viewModel::setWebSearchEnabled,
            setSearchTools = viewModel::setSearchToolsEnabled,
            setAgentSkillsSearch = viewModel::setAgentSkillsSearchEnabled
        )
    ) {
        if (showMediaSheet) MediaStorageSheet(toolFiles) { showMediaSheet = false }
        val drawerScrim = modalScrimColor(DrawerDefaults.scrimColor)
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = chatOnTop,
            scrimColor = drawerScrim,
            drawerContent = {
                ConversationSideDrawer(
                    conversations = conversations,
                    providers = providers,
                    currentConversationId = currentConvId,
                    isOpen = drawerState.isOpen,
                    generatingConversationIds = generatingConversationIds,
                    organization = appPrefs.chatRelations.sidebar,
                    backgroundImage = appPrefs.userProfile.drawerBackground,
                    backgroundTransparency = appPrefs.userProfile.drawerTransparency,
                    onUpdateOrganization = settingsViewModel::updateSidebar,
                    onDeleteConversations = { ids ->
                        viewModel.deleteConversations(ids, chatAttachments.attachments.value)
                    },
                    onSelectConversation = { item ->
                        navigate(AppDestination.CHAT)
                        // Switching chats composes the whole history. Doing that while the drawer is still
                        // sliding away makes the close animation drop frames, so the switch waits for the
                        // drawer to settle (bounded, in case it is reopened or never reports closed).
                        coroutineScope.launch {
                            withTimeoutOrNull(DRAWER_CLOSE_WAIT_MS) {
                                snapshotFlow { drawerState.isClosed }.first { it }
                            }
                            viewModel.selectConversation(item.id)
                        }
                    },
                    onNewChat = {
                        startNewChat()
                    },
                    onRenameConversation = { id, newTitle ->
                        viewModel.renameConversation(id, newTitle)
                    },
                    onDeleteConversation = {
                        viewModel.deleteConversations(setOf(it), chatAttachments.attachments.value)
                    },
                    onNavigateToSettings = {
                        navigate(AppDestination.FEATURES)
                    },
                    featuresLogoImage = appPrefs.featuresLogoImage,
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    },
                    onOpenMediaStorage = {
                        coroutineScope.launch {
                            drawerState.close()
                            showMediaSheet = true
                        }
                    },
                    onOpenRemoteMachines = {
                        coroutineScope.launch { drawerState.close() }
                        openConfiguration(AppDestination.REMOTE_MACHINES)
                    },
                    onEditRemoteMachine = { machine ->
                        remoteMachinePage = MachineEditorPage(machine, isNew = false)
                        coroutineScope.launch { drawerState.close() }
                        openConfiguration(AppDestination.REMOTE_MACHINES)
                    },
                    onOpenTerminal = { machine ->
                        coroutineScope.launch { drawerState.close() }
                        openTerminal(machine)
                    },
                    onSaveMachineTools = remoteMachinesViewModel::saveMachineTools
                )
            }
        ) {
            // Pager and Drawer retain their native gestures. Ownership is locked by
            // the initial touch region/direction, never by subsequent finger reversals.
            val focusManager = LocalFocusManager.current
            LaunchedEffect(chatOnTop) {
                if (!chatOnTop) focusManager.clearFocus(force = true)
            }
            HorizontalPager(
                state = homePager,
                modifier = Modifier.fillMaxSize().homePagerGestureRouting(
                    chatOnTop, appBarBottomPx, drawerEdgePx
                ) { chatPagerGestureAllowed = it },
                beyondViewportPageCount = 1,
                userScrollEnabled = homePagerSwipeEnabled(homePager.settledPage, navBackStackEntry?.destination?.route) && chatPagerGestureAllowed,
                key = { if (it == CHAT_PAGE) "chat" else "menu" }
            ) { page ->
                if (page == CHAT_PAGE) {
                    ChatScreen(
                        backgroundImage = appPrefs.userProfile.chatBackground,
                        backgroundTransparency = appPrefs.userProfile.chatTransparency,
                        conversation = currentConvState.value,
                        loadingConversationId = loadingConversationId,
                        isDrawerOpening = drawerState.targetValue == DrawerValue.Open || drawerState.isOpen,
                        activeStream = viewModel.activeStreamForCurrentConversation,
                        isGenerating = isGenerating,
                        generatingConversationId = generatingConversationId,
                        chatError = chatError,
                        providers = providers,
                        providerGroups = providerGroups,
                        modelPickerCollapsedGroups = appPrefs.modelPickerCollapsedGroups,
                        modelPickerCollapsedProviders = appPrefs.modelPickerCollapsedProviders,
                        onModelPickerCollapsedGroupsChange = settingsViewModel::setModelPickerCollapsedGroups,
                        onModelPickerCollapsedProvidersChange = settingsViewModel::setModelPickerCollapsedProviders,
                         selectedProviderId = chatProviderId,
                          selectedModelName = chatModelId,
                         agentSkills = appPrefs.agentSkills.skills,
                         onOpenSkillsSettings = { openSkillsSettings() },
                          featuresLogoImage = appPrefs.featuresLogoImage,
                        autoScroll = appPrefs.autoScroll,
                        onSendMedia = viewModel::sendMedia,
                        onMediaModeChange = viewModel::setComposerMedia,
                        onResendUserMessage = viewModel::resendUserMessage,
                        onSendMessage = { text, files -> viewModel.sendMessage(text, files) },
                        onSendMessageWithSkill = { text, files, requested, skillName ->
                            viewModel.sendMessage(text, files, requested, skillName)
                        },
                        onStopGenerating = {
                            viewModel.stopGeneration()
                        },
                        onOpenDrawer = {
                            coroutineScope.launch { drawerState.open() }
                        },
                        onNewChat = {
                            startNewChat()
                        },
                        onOpenTemporaryChat = { startTemporaryChat() },
                        onOpenSettings = {
                            navigate(AppDestination.FEATURES)
                        },
                        onRegenerate = { id ->
                            viewModel.regenerateMessage(id)
                        },
                        onRetryFromError = { id ->
                            viewModel.retryFromError(id)
                        },
                        onEditMessage = { id, content, files ->
                            viewModel.editMessage(id, content, files)
                        },
                        onDeleteMessage = { id ->
                            viewModel.deleteMessage(id)
                        },
                        onDeleteMessageVersion = { id ->
                            viewModel.deleteMessageVersion(id)
                        },
                        queuedMessages = queuedMessages,
                        onEditQueuedMessage = { id, content, files ->
                            viewModel.editQueuedMessage(id, content, files)
                        },
                        onDeleteQueuedMessage = { id ->
                            viewModel.deleteQueuedMessage(id)
                        },
                        onSwitchVersion = { id, idx ->
                            viewModel.switchMessageVersion(id, idx)
                        },
                        onSelectModel = { provId, modelId ->
                            viewModel.selectModel(provId, modelId)
                        },
                        summaryProgress = summaryProgress,
                        contextWindowStatus = contextWindowStatus,
                        onSetReasoningEffort = viewModel::setConversationReasoningEffort,
                        onSetThinkingPassback = configurationViewModel::setThinkingPassback,
                        onSetModelReasoningEffort = configurationViewModel::setModelReasoningEffort,
                        onSetModelTools = configurationViewModel::setModelTools,
                        onEditModel = { providerId, modelId ->
                            showHomePage(MENU_PAGE)
                            navController.navigate(
                                "providers/edit/${Uri.encode(providerId)}?modelId=${
                                    Uri.encode(
                                        modelId
                                    )
                                }&returnToChat=true"
                            ) {
                                launchSingleTop = true
                            }
                        },
                        onEditProvider = { providerId ->
                            showHomePage(MENU_PAGE)
                            navController.navigate("providers/edit/${Uri.encode(providerId)}?returnToChat=true") {
                                launchSingleTop = true
                            }
                        },
                        onStartConversationSummary = viewModel::startConversationSummary,
                        onCancelConversationSummary = viewModel::cancelConversationSummary,
                        onSummaryRoleChange = viewModel::setSummaryRole,
                        onDeleteSummary = viewModel::deleteConversationSummary,
                        speakingMessageId = speakingMessageId,
                        onReadMessage = { messageId, text ->
                            viewModel.speakText(text, messageId)
                        },
                        onFetchOllamaModels = { url ->
                            configurationViewModel.fetchOllamaModels(url)
                        },
                        connectedMachines = connectedMachines,
                         onOpenTerminal = { machine ->
                             openTerminal(machine)
                         },
                         onOpenFeatureToolsSettings = { openConfiguration(AppDestination.TOOLS_EDITOR) },
                         onOpenMcpConnectorsSettings = { openConfiguration(AppDestination.MCP) }
                     )
                } else {
                    // One opaque photo behind the entire Menu NavHost; nested pages only
                    // slide their transparent content and never reposition this background.
                    Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        org.starfall.generative.shared.ui.background.ProfileBackground(
                            appPrefs.userProfile.menuBackground,
                            transparency = appPrefs.userProfile.menuTransparency
                        )
                    }
                    NavHost(
                    navController = navController,
                    startDestination = AppDestination.FEATURES.route,
                    enterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                    },
                    exitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                    },
                    popEnterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                    },
                    popExitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                    }
                ) {
                    composable(AppDestination.CHAT.route) {
                        // Compatibility for saved back stacks from the former single NavHost.
                        LaunchedEffect(Unit) {
                            showHomePage(CHAT_PAGE)
                            navController.navigate(AppDestination.FEATURES.route) {
                                popUpTo(AppDestination.CHAT.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    composable(
                        "terminal/{machineId}",
                        arguments = listOf(navArgument("machineId") { type = NavType.StringType })
                    ) { entry ->
                        val machineId = entry.arguments?.getString("machineId")
                        val machine = remoteMachineState.machines.firstOrNull { it.id == machineId }
                        if (machine != null) {
                            key(machine.id) {
                                RemoteTerminalScreen(
                                    machine = machine,
                                    openTerminal = { selectedMachine, askSecret ->
                                        remoteMachinesViewModel.openTerminal(selectedMachine, askSecret = askSecret)
                                    },
                                    disconnect = remoteMachinesViewModel::disconnect,
                                    onDismiss = { navController.popBackStack() }
                                )
                            }
                        } else {
                            // Wait for stored machines to load on a cold notification launch.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GenerativeLoadingIndicator()
                            }
                        }
                    }

                    composable("providers/new") {
                        val page = pendingNewProvider
                        if (page == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GenerativeLoadingIndicator()
                            }
                        } else {
                            // Clear the pending page only once this screen has left composition. Clearing it
                            // inside onDismiss made the exiting screen recompose into the `page == null`
                            // branch above, whose popBackStack() ran a second time and also popped Providers.
                            DisposableEffect(Unit) { onDispose { pendingNewProvider = null } }
                            key(page.provider.id) {
                                ProviderEditor(
                                    initialProvider = page.provider,
                                    isNew = page.isNew,
                                    onSave = { saved ->
                                        configurationViewModel.saveProvider(saved)
                                        pendingNewProvider = ProviderEditorPage(saved, false)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.provider_saved),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onDismiss = { navController.popBackStack() },
                                    onSaveModels = configurationViewModel::saveProviderModels,
                                    onReorderModels = configurationViewModel::reorderProviderModels,
                                    onAuthorizeProvider = configurationViewModel::authorizeProvider,
                                    onCancelProviderAuthorization = configurationViewModel::cancelProviderAuthorization,
                                    onClearOAuthCredentials = configurationViewModel::clearOAuthCredentials,
                                    onTestConnection = configurationViewModel::testConnection,
                                    onFetchModels = configurationViewModel::fetchProviderModels,
                                    customImageMediaProfiles = toolSettings.system.values
                                        .flatMap { it.imageRequestProfiles.values }
                                        .associateBy { it.id },
                                    customVideoMediaProfiles = toolSettings.system.values
                                        .flatMap { it.videoRequestProfiles.values }
                                        .associateBy { it.id }
                                )
                            }
                        }
                    }

                    composable(
                        route = "providers/edit/{providerId}?modelId={modelId}&returnToChat={returnToChat}",
                        arguments = listOf(navArgument("modelId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }, navArgument("returnToChat") {
                            type = NavType.BoolType
                            defaultValue = false
                        })
                    ) { entry ->
                        val providerId = entry.arguments?.getString("providerId").orEmpty()
                        val modelId = entry.arguments?.getString("modelId")
                        // Store the origin on this back-stack entry, including across recreation.
                        val returnToChat = entry.arguments?.getBoolean("returnToChat") == true
                        val dismissEditor: () -> Unit = {
                            if (navController.currentBackStackEntry == entry && navController.popBackStack()) {
                                if (returnToChat) showHomePage(CHAT_PAGE)
                            }
                        }
                        TargetedEditorContent(
                            recordId = providerId,
                            title = stringResource(R.string.provider_editor),
                            loadingMessage = stringResource(R.string.loading_provider_editor),
                            onBack = dismissEditor,
                            load = { configurationViewModel.loadProviderForEditor(providerId) }
                        ) { provider ->
                            key(provider.id) {
                                ProviderEditor(
                                    initialProvider = provider,
                                    isNew = false,
                                    initialEditingModelId = modelId,
                                    onSave = { saved ->
                                        configurationViewModel.saveProvider(saved)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.provider_saved),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onDismiss = dismissEditor,
                                    onSaveModels = configurationViewModel::saveProviderModels,
                                    onReorderModels = configurationViewModel::reorderProviderModels,
                                    onAuthorizeProvider = configurationViewModel::authorizeProvider,
                                    onCancelProviderAuthorization = configurationViewModel::cancelProviderAuthorization,
                                    onClearOAuthCredentials = configurationViewModel::clearOAuthCredentials,
                                    onTestConnection = configurationViewModel::testConnection,
                                    onFetchModels = configurationViewModel::fetchProviderModels,
                                    customImageMediaProfiles = toolSettings.system.values
                                        .flatMap { it.imageRequestProfiles.values }
                                        .associateBy { it.id },
                                    customVideoMediaProfiles = toolSettings.system.values
                                        .flatMap { it.videoRequestProfiles.values }
                                        .associateBy { it.id }
                                )
                            }
                        }
                    }

                    composable(AppDestination.PROVIDERS.route) {
                        ProvidersScreen(
                            providers = providers,
                            providerGroups = providerGroups,
                            collapsedSectionsState = appPrefs.providersCollapsedSections,
                            onCollapsedSectionsChange = settingsViewModel::setProvidersCollapsedSections,
                            isGridView = appPrefs.showProvidersAsGrid,
                            onToggleGridView = { configurationViewModel.setShowProvidersAsGrid(it) },
                            onOpenProviderEditor = { provider, isNew ->
                                if (isNew) {
                                    pendingNewProvider = ProviderEditorPage(provider, true)
                                    navController.navigate("providers/new")
                                } else {
                                    navController.navigate("providers/edit/${Uri.encode(provider.id)}")
                                }
                            },
                            onSaveGroup = configurationViewModel::saveProviderGroup,
                            onDeleteGroup = { groupId ->
                                configurationViewModel.deleteProviderGroup(groupId)
                                settingsViewModel.setProvidersCollapsedSections(appPrefs.providersCollapsedSections - groupId)
                                settingsViewModel.setModelPickerCollapsedGroups(appPrefs.modelPickerCollapsedGroups - groupId)
                            },
                            onMoveProviderToGroup = configurationViewModel::moveProviderToGroup,
                            onPlaceProviderImmediately = configurationViewModel::queueProviderPlacement,
                            onDeleteProvider = { providerId ->
                                configurationViewModel.deleteProvider(providerId)
                                settingsViewModel.setModelPickerCollapsedProviders(appPrefs.modelPickerCollapsedProviders - providerId)
                            },
                            onReorderProviders = configurationViewModel::reorderProviders,
                            onReorderRootItems = configurationViewModel::reorderProviderRootItems,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppDestination.MCP.route) {
                        SlideScreenContent(editor = mcpEditor, label = stringResource(R.string.mcp_editor)) { page ->
                            if (page != null) {
                                TargetedEditorContent(
                                    recordId = page.first.id,
                                    title = stringResource(R.string.mcp_editor),
                                    loadingMessage = stringResource(R.string.loading_mcp_server_editor),
                                    onBack = { mcpEditor = null },
                                    load = { configurationViewModel.loadMcpServerForEditor(page.first.id) },
                                    draft = page.first.takeIf { page.second }
                                ) { server ->
                                    key(server.id) {
                                        ServerEditor(
                                            initialServer = server,
                                            isNew = page.second,
                                            onRefreshTools = { configurationViewModel.refreshMcpTools(it) },
                                            onAuthorizeOAuth = { configurationViewModel.authorizeMcpOAuth(it) },
                                            onClearOAuth = { configurationViewModel.clearMcpOAuth(it) },
                                            cachedTools = mcpToolsCache[server.id] ?: server.cachedTools,
                                            cachedError = mcpToolErrors[server.id],
                                            cachedLoading = server.id in mcpToolsLoading,
                                            toolSettings = toolSettings,
                                            onSetToolEnabled = viewModel::setMcpToolEnabled,
                                            onSetToolParameter = viewModel::setMcpToolParameter,
                                            onDismiss = { mcpEditor = null },
                                            onSave = { configurationViewModel.saveMcpServer(it) }
                                        )
                                    }
                                }
                            } else {
                                ServerListScreen(
                                    mcpServers = mcpServers,
                                    isGridView = appPrefs.showMcpAsGrid,
                                    onToggleGridView = { configurationViewModel.setShowMcpAsGrid(it) },
                                    toolsCache = mcpToolsCache,
                                    toolErrors = mcpToolErrors,
                                    toolsLoading = mcpToolsLoading,
                                    toolSettings = toolSettings,
                                    onSetToolEnabled = viewModel::setMcpToolEnabled,
                                    onSetMcpEnabled = viewModel::setQuickMcp,
                                    onDeleteMcpServer = { configurationViewModel.deleteMcpServer(it) },
                                    onReorderMcpServers = configurationViewModel::reorderMcpServers,
                                    onOpenEditor = { server, isNew -> mcpEditor = server to isNew },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    composable(AppDestination.SPEECH.route) {
                        SpeechServicesScreen(
                            speechServices = speechServices,
                            isGridView = appPrefs.showSpeechAsGrid,
                            onToggleGridView = { configurationViewModel.setShowSpeechAsGrid(it) },
                            providers = providers,
                            selectedSpeechServiceId = appPrefs.selectedSpeechServiceId,
                            onSetTtsProvider = viewModel::selectSpeechService,
                            onSaveService = { configurationViewModel.saveSpeechService(it) },
                            onDeleteService = { configurationViewModel.deleteSpeechService(it) },
                            onReorderServices = configurationViewModel::reorderSpeechServices,
                            onTestVoice = viewModel::testVoice,
                            onBack = { navController.popBackStack() },
                            activeTestServiceId = activeTestSpeechServiceId,
                            onStopPlayback = viewModel::stopSpeaking
                        )
                    }

                    composable(
                        route = "${AppDestination.CHAT_OPTIONS.route}?tab={tab}&instructionId={instructionId}",
                        arguments = listOf(
                            navArgument("tab") { type = NavType.StringType; defaultValue = "helpers" },
                            navArgument("instructionId") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { chatOptionsEntry ->
                        ChatOptionsScreen(
                            initialTab = chatOptionsEntry.arguments?.getString("tab") ?: "helpers",
                            initialInstructionId = chatOptionsEntry.arguments?.getString("instructionId").orEmpty(),
                            instructionLibrary = appPrefs.instructionLibrary,
                            onInstructionLibraryChange = viewModel::setInstructionLibrary,
                            agentSkills = appPrefs.agentSkills.skills,
                            onAgentSkillsChange = viewModel::setAgentSkills,
                            providers = providers,
                            settings = toolSettings,
                            mcpServers = mcpServers,
                            onSetWebSearchProvider = viewModel::setWebSearchProvider,
                            onSetWebSearchEnabled = viewModel::setWebSearchEnabled,
                            speechServices = speechServices,
                            selectedSpeechServiceId = appPrefs.selectedSpeechServiceId,
                            onSetTtsProvider = { viewModel.selectSpeechService(it) },
                            appPreferences = appPrefs,
                            onAutoSummaryEnabledChange = settingsViewModel::setAutoSummaryEnabled,
                            onSummarySuggestionEnabledChange = settingsViewModel::setSummarySuggestionEnabled,
                            onAutoSummaryPercentChange = settingsViewModel::setAutoSummaryPercent,
                            providerGroups = providerGroups,
                            collapsedGroupIds = appPrefs.modelPickerCollapsedGroups,
                            collapsedProviderIds = appPrefs.modelPickerCollapsedProviders,
                            onCollapsedGroupIdsChange = settingsViewModel::setModelPickerCollapsedGroups,
                            onCollapsedProviderIdsChange = settingsViewModel::setModelPickerCollapsedProviders,
                            onSave = viewModel::setSystemTool,
                            onEditProvider = { providerId ->
                                navController.navigate("providers/edit/${Uri.encode(providerId)}") {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppDestination.TOOLS_EDITOR.route) {
                        ToolsEditor(
                            providers = providers,
                            settings = toolSettings,
                            onSave = viewModel::setSystemTool,
                            onSetSendFile = viewModel::setSendFileEnabled,
                            onSetSearchTools = viewModel::setSearchToolsEnabled,
                            onSetAgentSkillsSearch = viewModel::setAgentSkillsSearchEnabled,
                            providerGroups = providerGroups,
                            collapsedGroupIds = appPrefs.modelPickerCollapsedGroups,
                            collapsedProviderIds = appPrefs.modelPickerCollapsedProviders,
                            onCollapsedGroupIdsChange = settingsViewModel::setModelPickerCollapsedGroups,
                            onCollapsedProviderIdsChange = settingsViewModel::setModelPickerCollapsedProviders,
                            onSaveModelConfiguration = configurationViewModel::saveModelConfiguration,
                            onEditProvider = { providerId ->
                                navController.navigate("providers/edit/${Uri.encode(providerId)}") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenMusicProfiles = { navController.navigate(MediaProfileRoutes.library(MediaKind.MUSIC)) },
                            onOpenMediaProfiles = { video ->
                                navController.navigate(MediaProfileRoutes.library(video)) { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = MediaProfileRoutes.LIBRARY,
                        arguments = listOf(navArgument("kind") { type = NavType.StringType })
                    ) { entry ->
                        val kind = MediaKind.entries.firstOrNull { it.name.equals(entry.arguments?.getString("kind"), true) } ?: MediaKind.IMAGE
                        val video = kind == MediaKind.VIDEO
                        val music = kind == MediaKind.MUSIC
                        val toolName = kind.toolName
                        val config = toolSettings.system[toolName] ?: SystemToolConfig()
                        MediaRequestChainSettings(
                            provider = if (music) null else providers.firstOrNull { it.id == config.providerId },
                            config = config,
                            onAutoSave = { profiles, activeId -> if (music) persistMusicProfiles(profiles, activeId) else persistMediaProfiles(video, profiles, activeId) },
                            onOpenProfile = { id -> navController.navigate(MediaProfileRoutes.editor(kind, id)) },
                            onAddProfile = { navController.navigate(MediaProfileRoutes.editor(kind, "new")) },
                            onBack = { navController.popBackStack() },
                            video = video, kind = kind
                        )
                    }
                    composable(
                        route = MediaProfileRoutes.EDITOR,
                        arguments = listOf(
                            navArgument("kind") { type = NavType.StringType },
                            navArgument("profileId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val kind = MediaKind.entries.firstOrNull { it.name.equals(entry.arguments?.getString("kind"), true) } ?: MediaKind.IMAGE
                        val video = kind == MediaKind.VIDEO
                        val music = kind == MediaKind.MUSIC
                        val profileId = entry.arguments?.getString("profileId").orEmpty()
                        val toolName = kind.toolName
                        val config = toolSettings.system[toolName] ?: SystemToolConfig()
                        val profile = if (profileId == "new") {
                            val id = rememberSaveable { UUID.randomUUID().toString() }
                            // Empty profiles are not inserted until a valid name can be auto-saved.
                            remember(id) { MediaRequestProfile(id = id, name = "", mode = if (music) MediaGenerationMode.CUSTOM else MediaGenerationMode.DEFAULT) }
                        } else {
                            remember(profileId) { config.profiles(kind)[profileId] }
                        }
                        if (profile == null) {
                            LaunchedEffect(profileId) { navController.popBackStack() }
                        } else {
                            key(profile.id) {
                                MediaRequestProfileEditor(
                                    provider = if (music) null else providers.firstOrNull { it.id == config.providerId },
                                    config = config,
                                    profile = profile,
                                    video = video, kind = kind,
                                    onAutoSave = { updated ->
                                        val latest = toolSettings.system[toolName] ?: SystemToolConfig()
                                        val current = latest.profiles(kind)
                                        val selected = if (music) latest.activeMusicRequestProfileId else if (video) latest.activeVideoRequestProfileId
                                            else latest.activeImageRequestProfileId
                                        if (music) persistMusicProfiles(current + (updated.id to updated), selected)
                                        else persistMediaProfiles(video, current + (updated.id to updated), selected)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                    composable(AppDestination.STORAGE.route) { StorageScreen(toolFiles) { navController.popBackStack() } }
                    composable(AppDestination.REMOTE_MACHINES.route) {
                        // Sub-pages are transient: never restore an editor/settings page on re-entry.
                        DisposableEffect(Unit) {
                            onDispose {
                                remoteMachinePage = null
                            }
                        }
                        SlideScreenContent(
                            editor = remoteMachinePage,
                            label = stringResource(R.string.remote_machines_title)
                        ) { selected ->
                            when {
                                selected is MachineEditorPage -> MachineDetailsScreen(
                                    initial = selected.machine,
                                    state = remoteMachineState,
                                    onSave = {
                                        remoteMachinesViewModel.saveMachine(it)
                                        remoteMachinePage = null
                                    },
                                    onBack = { remoteMachinePage = null },
                                    onRefreshIp = { remoteMachinesViewModel.refreshIp(it) },
                                    onFetchUsername = { remoteMachinesViewModel.fetchGCloudUsername(it) },
                                    onAuthorizeOAuth = { remoteMachinesViewModel.authorize(it) },
                                    onStoreAndroidOAuthToken = { client, token ->
                                        remoteMachinesViewModel.storeAndroidOAuthToken(
                                            client,
                                            token
                                        )
                                    }
                                )

                                selected == RemoteMachineSettingsPage.SETTINGS -> RemoteMachineSettings(
                                    state = remoteMachineState,
                                    viewModel = remoteMachinesViewModel,
                                    onBack = { remoteMachinePage = null }
                                )

                                else -> RemoteMachinesScreen(
                                    viewModel = remoteMachinesViewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenEditor = { machine, isNew ->
                                        remoteMachinePage = MachineEditorPage(machine, isNew)
                                    },
                                    onOpenSettings = { remoteMachinePage = RemoteMachineSettingsPage.SETTINGS },
                                    onOpenTerminal = { machine ->
                                        openTerminal(machine)
                                    }
                                )
                            }
                        }
                    }
                    composable(AppDestination.USER_PROFILE.route) {
                        UserProfileScreen(
                            profile = appPrefs.userProfile,
                            onChange = settingsViewModel::updateUserProfile,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(AppDestination.FEATURES.route) {
                        AppMenuScreen(
                            modifier = Modifier.fillMaxSize(),
                            logoImage = appPrefs.featuresLogoImage,
                            onLogoImageChange = settingsViewModel::setFeaturesLogoImage,
                            onNavigateToUserProfile = { openConfiguration(AppDestination.USER_PROFILE) },
                            onNavigateToChatOptions = { openConfiguration(AppDestination.CHAT_OPTIONS) },
                            onNavigateToToolsEditor = { openConfiguration(AppDestination.TOOLS_EDITOR) },
                            onNavigateToProviders = { openConfiguration(AppDestination.PROVIDERS) },
                            onNavigateToMcp = { openConfiguration(AppDestination.MCP) },
                            onNavigateToSpeech = { openConfiguration(AppDestination.SPEECH) },
                            onNavigateToRemoteMachines = { openConfiguration(AppDestination.REMOTE_MACHINES) },
                            onNavigateToStorage = { openConfiguration(AppDestination.STORAGE) },
                            onNavigateToAppSettings = { openConfiguration(AppDestination.APP_SETTINGS) },
                            onNavigateToIcons = { openConfiguration(AppDestination.ICONS) },
                            onNavigateToAppData = { openConfiguration(AppDestination.APP_DATA) },
                            onNavigateToAbout = { openConfiguration(AppDestination.ABOUT) }
                        )
                    }

                    composable(AppDestination.APP_SETTINGS.route) {
                        Scaffold(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            topBar = {
                                MainAppbar(
                                    title = stringResource(R.string.settings_category_app_settings),
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        ) { padding ->
                            Box(Modifier.fillMaxSize()) {
                                AppSettings(
                                    scaffoldPadding = padding,
                                    appPreferences = appPrefs,
                                    onThemeChange = { mode ->
                                        settingsViewModel.setThemeMode(mode)
                                    },
                                    onAmoledChange = { use ->
                                        settingsViewModel.setUseAmoled(use)
                                    },
                                    onDynamicColorChange = { use ->
                                        settingsViewModel.setUseDynamicColor(use)
                                    },
                                    onColorSchemeChange = { scheme ->
                                        settingsViewModel.setColorSchemeName(scheme)
                                    },
                                    onItemTransparencyChange = settingsViewModel::setItemTransparency,
                                    onHideStatusBarChange = { value ->
                                        settingsViewModel.setHideStatusBar(value)
                                    },
                                    onWordWrapModeChange = settingsViewModel::setWordWrapMode,
                                    onWordWrapColumnChange = settingsViewModel::setWordWrapColumn,
                                    onCodePreviewEnabledChange = settingsViewModel::setCodePreviewEnabled,
                                    onCodeBlockDisplayModeChange = settingsViewModel::setCodeBlockDisplayMode,
                                    onCodeBlockMaxLinesChange = settingsViewModel::setCodeBlockMaxLines,
                                    onCodeBlockIncludeUndefinedChange = settingsViewModel::setCodeBlockIncludeUndefined,
                                    onMessageFontSizeChange = settingsViewModel::setMessageFontSize,
                                    onMessageFontSizeCustomChange = settingsViewModel::setMessageFontSizeCustom,
                                    onMessageFontFamilyChange = settingsViewModel::setMessageFontFamily,
                                    onContinueLastConversationChange = { value ->
                                        settingsViewModel.setContinueLastConversation(value)
                                    },
                                    onPersistSelectedModelChange = { value ->
                                        viewModel.setPersistSelectedModel(value)
                                    },
                                    onAutoScrollChange = settingsViewModel::setAutoScroll,
                                    onEnableVibrationChange = { value ->
                                        settingsViewModel.setEnableVibration(value)
                                    },
                                    onDebugModeChange = { value ->
                                        settingsViewModel.setDebugMode(value)
                                    },
                                    onDebugLogCategoriesChange = settingsViewModel::setDebugLogCategories,
                                    onLatexModeChange = { mode ->
                                        settingsViewModel.setLatexMode(mode)
                                    },
                                    onTtsReadCodeBlocksChange = settingsViewModel::setTtsReadCodeBlocks,
                                    onToolRoundLimitEnabledChange = settingsViewModel::setToolRoundLimitEnabled,
                                    onToolRoundLimitChange = settingsViewModel::setToolRoundLimit,
                                    onToolCallStrategyChange = settingsViewModel::setToolCallStrategy,
                                    onToolCallConcurrentLimitChange = settingsViewModel::setToolCallConcurrentLimit,
                                    onMcpAttachmentsChange = settingsViewModel::setMcpAttachments,
                                    onScreenWakelockModeChange = settingsViewModel::setScreenWakelockMode,
                                    onAppLanguageChange = settingsViewModel::setAppLanguage,
                                 )
                            }
                        }
                    }

                    composable(AppDestination.ICONS.route) {
                        IconsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(AppDestination.APP_DATA.route) {
                        AppDataScreen(
                            conversationCount = conversations.size,
                            providerCount = providers.size,
                            onClearAllConversations = {
                                viewModel.clearAllConversations(chatAttachments.attachments.value)
                            },
                            onResetAllData = {
                                viewModel.deleteAllUserData(chatAttachments.attachments.value)
                            },
                            onExportZip = ::exportData,
                            onImportZip = ::importData,
                            exportInProgress = preparingExport,
                            importInProgress = importInProgress,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppDestination.ABOUT.route) {
                        Scaffold(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            topBar = {
                                MainAppbar(
                                    title = stringResource(R.string.settings_category_about),
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        ) { padding ->
                            AboutScreen(contentPadding = padding)
                        }
                    }

                    // Restore old saved menu routes into the unified settings destination.
                    composable(AppDestination.MENU.route) {
                        LaunchedEffect(Unit) {
                            navController.navigate(AppDestination.FEATURES.route) {
                                popUpTo(AppDestination.MENU.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                }
            }
        }
    }
    }
    // Back from the Menu root is a native Pager transition to Chat, just like a swipe.
    // Nested screens retain their own NavHost Back behavior without switching tabs.
    BackHandler(enabled = homePagerBackToChatEnabled(homePager.currentPage, navBackStackEntry?.destination?.route)) {
        showHomePage(CHAT_PAGE)
    }
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }
}
