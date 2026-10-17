package org.starfall.multigateway.navigation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import android.widget.Toast
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import org.starfall.multigateway.features.appdata.model.JsonExportKind
import org.starfall.multigateway.features.appdata.service.buildJsonExport
import org.starfall.multigateway.features.appdata.service.parseConversationsExport
import org.starfall.multigateway.features.appdata.service.parseMcpExport
import org.starfall.multigateway.features.appdata.service.parseProvidersExport
import org.starfall.multigateway.features.appdata.service.parseSpeechServicesExport
import org.starfall.multigateway.features.defaultmodelsandtools.shared.LocalToolControls
import org.starfall.multigateway.features.defaultmodelsandtools.shared.ToolControls
import org.starfall.multigateway.features.defaultmodelsandtools.systemtools.screen.SystemToolsScreen
import org.starfall.multigateway.features.mcp.serverlist.screen.ServerListScreen
import org.starfall.multigateway.features.providers.providereditor.screen.ProviderEditorScreen
import org.starfall.multigateway.features.providers.providers.screen.ProvidersScreen
import org.starfall.multigateway.features.providers.shared.ConfigurationViewModel
import org.starfall.multigateway.features.remotemachines.remotemachines.screen.RemoteMachinesScreen
import org.starfall.multigateway.features.remotemachines.remotemachines.screen.RemoteMachinesViewModel
import org.starfall.multigateway.features.settings.appsettings.screen.SettingsViewModel
import org.starfall.multigateway.features.speechservices.speechservices.screen.SpeechServicesScreen
import org.starfall.multigateway.features.storage.storage.screen.StorageScreen
import org.starfall.multigateway.home.chat_screen.ChatScreen
import org.starfall.multigateway.home.chat_screen.ChatViewModel
import org.starfall.multigateway.home.leftside_drawer.LeftSideDrawer
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.SpeechService
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderGroup
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.shared.theme.modalScrimColor
import org.starfall.multigateway.shared.ui.media.ChatAttachmentSelection
import org.starfall.multigateway.shared.ui.media.LocalChatAttachmentSelection
import androidx.compose.ui.res.stringResource
import org.starfall.multigateway.R
import org.starfall.multigateway.features.about.screen.AboutScreen
import org.starfall.multigateway.features.appdata.screen.AppDataScreen
import org.starfall.multigateway.features.icons.screen.IconsScreen
import org.starfall.multigateway.features.main.screen.FeaturesScreen
import org.starfall.multigateway.features.settings.appsettings.screen.AppSettingsScreen
import org.starfall.multigateway.features.settings.shared.MainAppbar

/** Stable route names are persisted by Navigation across Activity/process recreation. */
enum class AppDestination(val route: String) {
    CHAT("chat"),
    PROVIDERS("providers"),
    MCP("mcp"),
    SPEECH("speech"),
    SETTINGS("settings"),
    SYSTEM_TOOLS("system_tools"),
    STORAGE("storage"),
    REMOTE_MACHINES("remote_machines"),
    MENU("menu"),
    APP_SETTINGS("AppSettings"),
    ICONS("Icons"),
    APP_DATA("AppData"),
    ABOUT("About"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    viewModel: ChatViewModel,
    configurationViewModel: ConfigurationViewModel,
    settingsViewModel: SettingsViewModel,
    remoteMachinesViewModel: RemoteMachinesViewModel,
    toolFiles: ToolFiles,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerFocusManager = LocalFocusManager.current
    // Opening the sidebar (button or swipe) takes focus away from the chat input.
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) drawerFocusManager.clearFocus(force = true)
    }
    val coroutineScope = rememberCoroutineScope()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val chatOnTop = navBackStackEntry?.destination?.route == AppDestination.CHAT.route
    fun navigate(destination: AppDestination) {
        navController.navigate(destination.route) {
            popUpTo(AppDestination.CHAT.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun startNewChat() {
        viewModel.startNewChat()
        navigate(AppDestination.CHAT)
    }
    fun openConfiguration(destination: AppDestination) {
        navController.navigate(destination.route) { launchSingleTop = true }
    }
    val conversations: List<Conversation> by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConv: Conversation? by viewModel.currentConversation.collectAsStateWithLifecycle()
    val chatAttachments = remember { ChatAttachmentSelection() }
    LaunchedEffect(currentConv?.id) { chatAttachments.clear() }
    val isGenerating: Boolean by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generatingConversationIds by viewModel.generatingConversationIds.collectAsStateWithLifecycle()
    val generatingConversationId = currentConv?.id.takeIf { isGenerating }
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

    val toolSettings by viewModel.toolSettings.collectAsStateWithLifecycle()
    val queuedMessages by viewModel.queuedMessages.collectAsStateWithLifecycle()
    val importedProviderId by configurationViewModel.importedProviderId.collectAsStateWithLifecycle()
    val providerImportError by configurationViewModel.providerImportError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<Pair<JsonExportKind, String>?>(null) }
    var pendingImportKind by remember { mutableStateOf<JsonExportKind?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExport?.second
        pendingExport = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { it.write(payload) }
                } ?: error("Could not open the selected file.")
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "JSON file exported", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${error.message.orEmpty()}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    fun exportJson(kind: JsonExportKind) {
        coroutineScope.launch {
            val payload = withContext(Dispatchers.Default) {
                buildJsonExport(kind, providers, mcpServers, speechServices, conversations, providerGroups)
            }
            pendingExport = kind to payload
            exportLauncher.launch(kind.fileName)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val kind = pendingImportKind
        pendingImportKind = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Could not open the selected file.")
                }
                val count = when (kind) {
                    JsonExportKind.PROVIDERS -> withContext(Dispatchers.Default) { parseProvidersExport(raw) }
                        .also {
                            withContext(Dispatchers.IO) {
                                configurationViewModel.importProviders(it.providers, it.groups)
                                if (it.speechServices.isNotEmpty()) {
                                    configurationViewModel.importSpeechServices(it.speechServices)
                                }
                            }
                        }.let { it.providers.size + it.speechServices.size }
                    JsonExportKind.MCP_SERVERS -> withContext(Dispatchers.Default) { parseMcpExport(raw) }
                        .also { withContext(Dispatchers.IO) { configurationViewModel.importMcpServers(it) } }.size
                    JsonExportKind.SPEECH_SERVICES -> withContext(Dispatchers.Default) { parseSpeechServicesExport(raw) }
                        .also { withContext(Dispatchers.IO) { configurationViewModel.importSpeechServices(it) } }.size
                    JsonExportKind.CONVERSATIONS -> withContext(Dispatchers.Default) { parseConversationsExport(raw) }
                        .also { withContext(Dispatchers.IO) { viewModel.importConversations(it) } }.size
                }
                Toast.makeText(context, "$count item(s) imported", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(context, "Import failed: ${error.message.orEmpty()}", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun importJson(kind: JsonExportKind) {
        pendingImportKind = kind
        importLauncher.launch(arrayOf("application/json", "text/*"))
    }
    // The app preference is the default for new chats. Existing chats keep showing
    // the model they last used, independently of that default.
    val conversationModel = currentConv?.takeIf { appPrefs.persistSelectedModel && it.modelId.isNotBlank() }?.let { conversation ->
        providers.firstOrNull { provider ->
            provider.id == conversation.providerId &&
                provider.config.modelIds?.contains(conversation.modelId) != false &&
                (provider.config.modelConfigs[conversation.modelId]?.modelType
                    ?: ModelType.TEXT_GENERATION) == ModelType.TEXT_GENERATION
        }?.let { conversation }
    }
    val chatProviderId = conversationModel?.providerId ?: appPrefs.selectedProviderId
    val chatModelId = conversationModel?.modelId ?: appPrefs.selectedModelId
    LaunchedEffect(Unit) {
        org.starfall.multigateway.shared.storage.LocalWriteErrors.errors.collect {
            Toast.makeText(context, "Could not save the change. Please try again.", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(importedProviderId) {
        importedProviderId?.let { id ->
            drawerState.close()
            navController.navigate("providers/edit/$id") { launchSingleTop = true }
            configurationViewModel.providerImportOpened(id)
        }
    }
    LaunchedEffect(providerImportError) {
        providerImportError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            configurationViewModel.clearProviderImportError()
        }
    }
    LaunchedEffect(chatOnTop) {
        if (!chatOnTop) drawerState.close()
    }


    CompositionLocalProvider(org.starfall.multigateway.home.chat_screen.LocalCodeRenderingPreferences provides appPrefs, LocalChatAttachmentSelection provides chatAttachments, LocalToolControls provides ToolControls(
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
        setSendFile = viewModel::setSendFileEnabled
    )) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = chatOnTop,
            scrimColor = modalScrimColor(DrawerDefaults.scrimColor),
            drawerContent = {
                LeftSideDrawer(
                    conversations = conversations,
                    providers = providers,
                    currentConversationId = currentConv?.id,
                    isOpen = drawerState.isOpen,
                    generatingConversationIds = generatingConversationIds,
                    organization = appPrefs.sidebar,
                    onUpdateOrganization = settingsViewModel::updateSidebar,
                    onDeleteConversations = viewModel::deleteConversations,
                    defaultSystemPrompt = appPrefs.defaultSystemPrompt,
                    promptLibrary = appPrefs.promptLibrary,
                    onPromptLibraryChange = viewModel::setPromptLibrary,
                    onSelectConversation = {
                        viewModel.selectConversation(it)
                        navigate(AppDestination.CHAT)
                    },
                    onNewChat = {
                        startNewChat()
                    },
                    onRenameConversation = { id, newTitle ->
                        viewModel.renameConversation(id, newTitle)
                    },
                    onDeleteConversation = {
                        viewModel.deleteConversation(it)
                    },
                    onUpdateDefaultSystemPrompt = { prompt ->
                        viewModel.setDefaultSystemPrompt(prompt)
                    },
                    onNavigateToSettings = {
                        navigate(AppDestination.SETTINGS)
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            // The chat screen stays composed whatever page is open: NavHost disposes a destination's
            // composition when another page is on top, which used to wipe the scroll position, the typed
            // draft, open sheets and every other piece of chat UI state. Other pages draw over it.
            val focusManager = LocalFocusManager.current
            LaunchedEffect(chatOnTop) {
                if (!chatOnTop) focusManager.clearFocus(force = true)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (chatOnTop) Modifier else Modifier.pointerInput(Unit) {
                                // Hidden underneath another page: swallow touches so nothing reaches it.
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                }
                            }
                        )
                ) {
                        ChatScreen(
                                conversation = currentConv,
                                isGenerating = isGenerating,
                                generatingConversationId = generatingConversationId,
                                chatError = chatError,
                                providers = providers,
                                providerGroups = providerGroups,
                                modelPickerCollapsedGroups = appPrefs.modelPickerCollapsedGroups,
                                modelPickerCollapsedProviders = appPrefs.modelPickerCollapsedProviders,
                                onModelPickerCollapsedGroupsChange = settingsViewModel::setModelPickerCollapsedGroups,
                                onModelPickerCollapsedProvidersChange = settingsViewModel::setModelPickerCollapsedProviders,
                                modelPickerSelectedTab = appPrefs.modelPickerSelectedTab,
                                onModelPickerSelectedTabChange = settingsViewModel::setModelPickerSelectedTab,
                                selectedProviderId = chatProviderId,
                                selectedModelName = chatModelId,
                                autoScroll = appPrefs.autoScroll,
                                onSendMedia = viewModel::sendMedia,
                                onResendUserMessage = viewModel::resendUserMessage,
                                onSendMessage = { text, files ->
                                    viewModel.sendMessage(text, files)
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
                                onOpenSettings = {
                                    navigate(AppDestination.SETTINGS)
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
                                onEditModel = { providerId, modelId ->
                                    navController.navigate("providers/edit/${Uri.encode(providerId)}?modelId=${Uri.encode(modelId)}") {
                                        launchSingleTop = true
                                    }
                                },
                                onEditProvider = { providerId ->
                                    navController.navigate("providers/edit/${Uri.encode(providerId)}") {
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
                                }
                        )
                }
                NavHost(
                    navController = navController,
                    startDestination = AppDestination.CHAT.route,
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
                        // The chat lives outside the NavHost (see above) so it is never disposed.
                        Box(Modifier.fillMaxSize())
                    }

                    composable(
                        route = "providers/edit/{providerId}?modelId={modelId}",
                        arguments = listOf(navArgument("modelId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { entry ->
                        val providerId = entry.arguments?.getString("providerId")
                        val modelId = entry.arguments?.getString("modelId")
                        val provider = providers.firstOrNull { it.id == providerId }
                        if (provider == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            key(provider.id) {
                                ProviderEditorScreen(
                                    initialProvider = provider,
                                    isNew = false,
                                    initialEditingModelId = modelId,
                                    onSave = configurationViewModel::saveProvider,
                                    onDismiss = { navController.popBackStack() },
                                    onSaveModels = configurationViewModel::saveProviderModels,
                                    onReorderModels = configurationViewModel::reorderProviderModels,
                                    onAuthorizeProvider = configurationViewModel::authorizeProvider,
                                    onCancelProviderAuthorization = configurationViewModel::cancelProviderAuthorization,
                                    onClearOAuthCredentials = configurationViewModel::clearOAuthCredentials,
                                    onTestConnection = configurationViewModel::testConnection,
                                    onFetchModels = configurationViewModel::fetchProviderModels
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
                            onSaveProvider = { configurationViewModel.saveProvider(it) },
                            onSaveGroup = configurationViewModel::saveProviderGroup,
                            onDeleteGroup = { groupId ->
                                configurationViewModel.deleteProviderGroup(groupId)
                                settingsViewModel.setProvidersCollapsedSections(appPrefs.providersCollapsedSections - groupId)
                                settingsViewModel.setModelPickerCollapsedGroups(appPrefs.modelPickerCollapsedGroups - groupId)
                            },
                            onMoveProviderToGroup = configurationViewModel::moveProviderToGroup,
                            onPlaceProviderImmediately = configurationViewModel::queueProviderPlacement,
                            onSaveModels = { providerId, models -> configurationViewModel.saveProviderModels(providerId, models) },
                            onReorderModels = configurationViewModel::reorderProviderModels,
                            onDeleteProvider = { providerId ->
                                configurationViewModel.deleteProvider(providerId)
                                settingsViewModel.setModelPickerCollapsedProviders(appPrefs.modelPickerCollapsedProviders - providerId)
                            },
                            onReorderProviders = configurationViewModel::reorderProviders,
                            onReorderRootItems = configurationViewModel::reorderProviderRootItems,
                            onAuthorizeProvider = { provider -> configurationViewModel.authorizeProvider(provider) },
                            onCancelProviderAuthorization = configurationViewModel::cancelProviderAuthorization,
                            onClearOAuthCredentials = { provider -> configurationViewModel.clearOAuthCredentials(provider) },
                            onTestConnection = { prov, modelId -> configurationViewModel.testConnection(prov, modelId) },
                            onFetchModels = { provider -> configurationViewModel.fetchProviderModels(provider) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppDestination.MCP.route) {
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
                            onSaveMcpServer = { configurationViewModel.saveMcpServer(it) },
                            onDeleteMcpServer = { configurationViewModel.deleteMcpServer(it) },
                            onReorderMcpServers = configurationViewModel::reorderMcpServers,
                            onRefreshTools = { configurationViewModel.refreshMcpTools(it) },
                            onAuthorizeOAuth = { configurationViewModel.authorizeMcpOAuth(it) },
                            onClearOAuth = { configurationViewModel.clearMcpOAuth(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppDestination.SPEECH.route) {
                        SpeechServicesScreen(
                            speechServices = speechServices,
                            isGridView = appPrefs.showSpeechAsGrid,
                            onToggleGridView = { configurationViewModel.setShowSpeechAsGrid(it) },
                            providers = providers,
                            selectedSpeechServiceId = appPrefs.selectedSpeechServiceId,
                            onSelectService = viewModel::selectSpeechService,
                            onSaveService = { configurationViewModel.saveSpeechService(it) },
                            onDeleteService = { configurationViewModel.deleteSpeechService(it) },
                            onReorderServices = configurationViewModel::reorderSpeechServices,
                            onTestVoice = viewModel::testVoice,
                            onBack = { navController.popBackStack() },
                            activeTestServiceId = activeTestSpeechServiceId,
                            onStopPlayback = viewModel::stopSpeaking
                        )
                    }

                    composable(AppDestination.SYSTEM_TOOLS.route) {
                        SystemToolsScreen(
                            providers = providers,
                            settings = toolSettings,
                            onSave = viewModel::setSystemTool,
                            onSetSendFile = viewModel::setSendFileEnabled,
                            providerGroups = providerGroups,
                             collapsedGroupIds = appPrefs.modelPickerCollapsedGroups,
                             collapsedProviderIds = appPrefs.modelPickerCollapsedProviders,
                             onCollapsedGroupIdsChange = settingsViewModel::setModelPickerCollapsedGroups,
                             onCollapsedProviderIdsChange = settingsViewModel::setModelPickerCollapsedProviders,
                             selectedTabKey = appPrefs.modelPickerSelectedTab,
                             onSelectedTabKeyChange = settingsViewModel::setModelPickerSelectedTab,
                             onEditProvider = { providerId ->
                                 navController.navigate("providers/edit/${Uri.encode(providerId)}") {
                                     launchSingleTop = true
                                 }
                             },
                             onBack = { navController.popBackStack() }
                        )
                    }
                     composable(AppDestination.STORAGE.route) { StorageScreen(toolFiles) { navController.popBackStack() } }
                     composable(AppDestination.REMOTE_MACHINES.route) {
                         RemoteMachinesScreen(
                             viewModel = remoteMachinesViewModel,
                             onBack = { navController.popBackStack() }
                         )
                     }
                    composable(AppDestination.SETTINGS.route) {
                        FeaturesScreen(
                            onNavigateToDefaultModels = { openConfiguration(AppDestination.SYSTEM_TOOLS) },
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
                            topBar = {
                                MainAppbar(
                                    title = stringResource(R.string.settings_category_app_settings),
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        ) { padding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                AppSettingsScreen(
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
                                    onLatexModeChange = { mode ->
                                        settingsViewModel.setLatexMode(mode)
                                    },
                                    onTtsReadCodeBlocksChange = settingsViewModel::setTtsReadCodeBlocks,
                                    onToolRoundLimitEnabledChange = settingsViewModel::setToolRoundLimitEnabled,
                                    onToolRoundLimitChange = settingsViewModel::setToolRoundLimit,
                                    onToolCallStrategyChange = settingsViewModel::setToolCallStrategy,
                                    onToolCallConcurrentLimitChange = settingsViewModel::setToolCallConcurrentLimit,
                                    onScreenWakelockModeChange = settingsViewModel::setScreenWakelockMode,
                                    onAppLanguageChange = settingsViewModel::setAppLanguage
                                )
                            }
                        }
                    }

                    composable(AppDestination.ICONS.route) {
                        Scaffold(
                            topBar = {
                                MainAppbar(
                                    title = stringResource(R.string.icon_settings_title),
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        ) { padding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                IconsScreen()
                            }
                        }
                    }

                    composable(AppDestination.APP_DATA.route) {
                        AppDataScreen(
                            conversationCount = conversations.size,
                            providerCount = providers.size,
                            onClearAllConversations = {
                                viewModel.clearAllConversations()
                            },
                            onResetAllData = {
                                viewModel.deleteAllUserData()
                            },
                            onExportJson = ::exportJson,
                            onImportJson = ::importJson,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppDestination.ABOUT.route) {
                        Scaffold(
                            topBar = {
                                MainAppbar(
                                    title = stringResource(R.string.settings_category_about),
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        ) { padding ->
                            AboutScreen(
                                modifier = Modifier
                                    .padding(padding)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Restore old saved menu routes into the unified settings destination.
                    composable(AppDestination.MENU.route) {
                        LaunchedEffect(Unit) {
                            navController.navigate(AppDestination.SETTINGS.route) {
                                popUpTo(AppDestination.MENU.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }
    }
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }
}
