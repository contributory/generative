package org.starfall.multigateway.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.starfall.multigateway.data.local.db.AppDatabase
import org.starfall.multigateway.data.local.preferences.AppPreferencesRepository
import org.starfall.multigateway.data.repository.*
import org.starfall.multigateway.data.service.*
import org.starfall.multigateway.data.tools.*
import org.starfall.multigateway.ui.chat.ChatViewModel
import org.starfall.multigateway.ui.configuration.ConfigurationViewModel
import org.starfall.multigateway.ui.settings.SettingsViewModel
import org.starfall.multigateway.data.service.RemoteMachineService
import org.starfall.multigateway.ui.remote.RemoteMachinesViewModel

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
    private val executableBinaries = ExecutableBinaryStore(appContext)
    // Shared executable storage backs ProxyCommand lookup for Remote Machines.
    val remoteMachineService = RemoteMachineService(appContext, executableBinaries = executableBinaries)
    val defaultDataInitializer = DefaultDataInitializer(providers, mcp, speech, preferences, icons)

    init {
        migrateLegacyRemoteBinaries()
    }

    /** Binaries used to be uploaded under Remote Machines; hand them to the shared store once. */
    private fun migrateLegacyRemoteBinaries() {
        val legacy = remoteMachineService.legacyBinaries()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (legacy.isNotEmpty()) {
                legacy.forEach { executableBinaries.adoptLegacy(it.name, File(it.path)) }
                remoteMachineService.clearLegacyBinaries()
            }
            executableBinaries.repairPermissions()
        }
    }

    val viewModelFactory = viewModelFactory {
        initializer {
            ChatViewModel(
                conversations,
                providers,
                mcp,
                preferences,
                ToolChat(toolHttp, mcpService, aiService, remoteMachineService),
                toolSettings,
                speech,
                TtsHelper(appContext),
                SpeechSynthesisService(),
                SpeechAudioPlayer(appContext)
            )
        }
        initializer { ConfigurationViewModel(profiles, providers, mcp, speech, preferences) }
        initializer { SettingsViewModel(preferences) }
        initializer { RemoteMachinesViewModel(remoteMachineService) }
    }
}
