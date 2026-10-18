package org.starfall.multigateway.appwiring


import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.starfall.multigateway.integrations.mcp.McpOAuthService
import org.starfall.multigateway.integrations.mcp.McpService
import org.starfall.multigateway.features.providers.shared.logic.ConfigurationViewModel
import org.starfall.multigateway.features.machines.shared.logic.RemoteMachinesViewModel
import org.starfall.multigateway.integrations.remote.ExecutableBinaryStore
import org.starfall.multigateway.integrations.remote.RemoteMachineService
import org.starfall.multigateway.features.settings.logic.SettingsViewModel
import org.starfall.multigateway.integrations.modelapis.AiService
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.icons.IconStore
import org.starfall.multigateway.shared.media.SpeechAudioPlayer
import org.starfall.multigateway.shared.media.SpeechSynthesisService
import org.starfall.multigateway.shared.media.TtsHelper
import org.starfall.multigateway.shared.networking.ToolHttp
import org.starfall.multigateway.shared.storage.ConversationRepository
import org.starfall.multigateway.shared.storage.McpRepository
import org.starfall.multigateway.shared.storage.ModelRepository
import org.starfall.multigateway.shared.storage.ProfileRepository
import org.starfall.multigateway.shared.storage.SpeechRepository
import org.starfall.multigateway.shared.storage.database.AppDatabase
import org.starfall.multigateway.shared.storage.preferences.AppPreferencesRepository
import org.starfall.multigateway.shared.storage.preferences.ToolSettingsStore
import org.starfall.multigateway.home.chat.logic.tool.ToolChatViewModel
import org.starfall.multigateway.home.chat.logic.ChatViewModel

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
                ToolChatViewModel(toolHttp, mcpService, aiService, remoteMachineService),
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
