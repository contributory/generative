package org.starfall.generative.appwiring


import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.starfall.generative.integrations.mcp.McpOAuthService
import org.starfall.generative.integrations.mcp.McpService
import org.starfall.generative.features.providers.shared.logic.ConfigurationViewModel
import org.starfall.generative.features.machines.shared.logic.RemoteMachinesViewModel
import org.starfall.generative.integrations.remote.RemoteMachineService
import org.starfall.generative.features.settings.logic.SettingsViewModel
import org.starfall.generative.integrations.modelapis.AiService
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.logging.DebugLog
import org.starfall.generative.shared.media.SpeechAudioPlayer
import org.starfall.generative.shared.media.SpeechSynthesisService
import org.starfall.generative.shared.media.TtsHelper
import org.starfall.generative.shared.networking.ToolHttp
import org.starfall.generative.shared.storage.ConversationRepository
import org.starfall.generative.shared.storage.McpRepository
import org.starfall.generative.shared.storage.ModelRepository
import org.starfall.generative.shared.storage.ProfileRepository
import org.starfall.generative.shared.storage.SpeechRepository
import org.starfall.generative.shared.storage.database.AppDatabase
import org.starfall.generative.shared.storage.preferences.AppPreferencesRepository
import org.starfall.generative.shared.storage.preferences.ToolSettingsStore
import org.starfall.generative.home.chat.logic.tool.ToolChatViewModel
import org.starfall.generative.home.chat.logic.ChatViewModel

/** Application-scoped dependencies. ViewModels receive only their required dependencies. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val aiService = AiService(appContext)
    val toolFiles = ToolFiles(appContext)
    private val toolHttp = ToolHttp(toolFiles)
    private val mcpOAuth = McpOAuthService(appContext, toolHttp)
    private val mcpService = McpService(toolHttp, mcpOAuth)
    private val conversations = ConversationRepository(database)
    private val profiles = ProfileRepository(database)
    private val icons = IconStore(appContext)
    private val providers = ModelRepository(database, aiService, icons)
    private val mcp = McpRepository(database, mcpService, icons, mcpOAuth)
    private val speech = SpeechRepository(database)
    private val preferences = AppPreferencesRepository(appContext)
    private val toolSettings = ToolSettingsStore(appContext)
    val remoteMachineService = RemoteMachineService(appContext)
    val defaultDataInitializer = DefaultDataInitializer(providers, mcp, speech, preferences, icons)

    init {
        // Keep the debug log's on/off state and scopes in step with App settings, from process start.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            preferences.appPreferencesFlow.collect { DebugLog.configure(it.debugMode, it.debugLogCategories) }
        }
        aiService.providerLoader = providers::resolveProvider
        mcpService.serverLoader = mcp::resolveServer
    }

    val viewModelFactory = viewModelFactory {
        initializer {
            ChatViewModel(
                conversations,
                providers,
                mcp,
                preferences,
                ToolChatViewModel(toolHttp, mcpService, aiService, remoteMachineService),
                toolSettings,
                speech,
                TtsHelper(appContext),
                SpeechSynthesisService(providers::resolveProvider),
                SpeechAudioPlayer(appContext),
                toolFiles
            )
        }
        initializer { ConfigurationViewModel(profiles, providers, mcp, speech, preferences) }
        initializer { SettingsViewModel(preferences) }
        initializer { RemoteMachinesViewModel(remoteMachineService) }
    }
}
