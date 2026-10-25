package org.starfall.generative.features.appdata.logic

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.starfall.generative.features.appdata.model.AppDataBackup
import org.starfall.generative.features.appdata.model.RemoteTerminalPreferencesState
import org.starfall.generative.features.appdata.model.ZipAppDataBackup
import org.starfall.generative.integrations.remote.RemoteMachineService
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.storage.ConversationRepository
import org.starfall.generative.shared.storage.McpRepository
import org.starfall.generative.shared.storage.ModelRepository
import org.starfall.generative.shared.storage.ProfileRepository
import org.starfall.generative.shared.storage.SpeechRepository
import org.starfall.generative.shared.storage.preferences.AppPreferencesRepository
import org.starfall.generative.shared.storage.preferences.ToolSettingsStore
import org.starfall.generative.shared.storage.preferences.REMOTE_TERMINAL_UI_PREFERENCES

data class AppDataImportResult(
    val itemCount: Int,
    val fullRestore: Boolean
)

/** Coordinates a complete, portable application backup rather than exporting individual tables. */
class AppDataTransferService(
    private val context: Context,
    private val modelRepository: ModelRepository,
    private val mcpRepository: McpRepository,
    private val speechRepository: SpeechRepository,
    private val profileRepository: ProfileRepository,
    private val conversationRepository: ConversationRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val toolSettingsStore: ToolSettingsStore,
    private val iconStore: IconStore,
    private val toolFiles: ToolFiles,
    private val remoteMachineService: RemoteMachineService
) {
    suspend fun createZipBackup(): File = withContext(Dispatchers.IO) {
        val storedFiles = toolFiles.list().associateBy { it.name }
        val backup = AppDataBackup(
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()),
            providers = modelRepository.loadProvidersForExport(),
            providerGroups = modelRepository.allGroups.first(),
            mcpServers = mcpRepository.loadServersForExport(),
            speechServices = speechRepository.allServices.first(),
            conversations = conversationRepository.conversationList.first().mapNotNull { conversationRepository.loadById(it.id) },
            storedFileNames = storedFiles.keys.sorted(),
            profiles = profileRepository.allProfiles.first(),
            appPreferences = preferencesRepository.appPreferencesFlow.first(),
            toolSettings = toolSettingsStore.snapshot(),
            remoteMachines = remoteMachineService.state.value,
            remoteTerminalPreferences = exportRemoteTerminalPreferences(),
            icons = iconStore.exportState(),
            accountCredentials = modelRepository.exportAccountCredentials(),
            mcpOAuthTokens = mcpRepository.exportOAuthTokens(),
            modelConfigurationMemory = modelRepository.exportModelConfigurationMemory()
        )
        val iconFiles = backup.icons.files.associateWith { name ->
            iconStore.exportFile(name) ?: error("The icon file $name is missing.")
        }
        val identityFiles = remoteMachineService.exportIdentityFiles()
        val destination = File.createTempFile("generative-backup-", ".zip", context.cacheDir)
        destination.outputStream().use { output -> writeZipBackup(output, backup, iconFiles, storedFiles, identityFiles) }
        destination
    }

    suspend fun import(input: InputStream, fileNameHint: String? = null): AppDataImportResult = withContext(Dispatchers.IO) {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(4)
        val signature = ByteArray(4)
        val read = buffered.read(signature)
        buffered.reset()
        val isZip = read == 4 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte()
        if (isZip || fileNameHint.orEmpty().lowercase(Locale.ROOT).endsWith(".zip")) {
            val staging = File.createTempFile("generative-import-", "", context.cacheDir).apply {
                delete()
                mkdirs()
            }
            try {
                val backup = readZipBackup(buffered, staging)
                restoreFullBackup(backup)
                AppDataImportResult(itemCount = backup.data.providers.size + backup.data.providerGroups.size +
                    backup.data.mcpServers.size + backup.data.speechServices.size + backup.data.conversations.size +
                    backup.data.profiles.size, fullRestore = true)
            } finally {
                staging.deleteRecursively()
            }
        } else {
            val raw = buffered.reader(Charsets.UTF_8).use { it.readText() }
            val backup = parseFullAppDataExport(raw, fileNameHint)
            backup.providers?.let { modelRepository.importProviders(it, backup.providerGroups) }
            backup.mcpServers?.let { it.forEach { server -> mcpRepository.saveServer(server) } }
            backup.speechServices?.let { it.forEach { service -> speechRepository.saveService(service) } }
            backup.conversations?.let { it.forEach { conversation -> conversationRepository.saveConversation(conversation) } }
            AppDataImportResult(
                itemCount = backup.providers.orEmpty().size + backup.providerGroups.orEmpty().size +
                    backup.mcpServers.orEmpty().size + backup.speechServices.orEmpty().size +
                    backup.conversations.orEmpty().size,
                fullRestore = false
            )
        }
    }

    private suspend fun restoreFullBackup(imported: ZipAppDataBackup) {
        val data = imported.data
        require(data.icons.files.toSet() == imported.iconFiles.keys) { "The icon files in the backup do not match its manifest." }
        require(data.remoteMachines.identities.map { it.id }.toSet() == imported.remoteIdentityFiles.keys) {
            "The SSH identity files in the backup do not match its manifest."
        }
        require(data.storedFileNames.toSet() == imported.toolFiles.keys) {
            "The stored files in the backup do not match its manifest."
        }

        modelRepository.replaceAll(data.providers, data.providerGroups)
        mcpRepository.replaceAll(data.mcpServers)
        speechRepository.replaceAll(data.speechServices)
        profileRepository.replaceAll(data.profiles)
        conversationRepository.replaceAll(data.conversations)
        preferencesRepository.replace(data.appPreferences)
        toolSettingsStore.replace(data.toolSettings)
        modelRepository.importAccountCredentials(data.accountCredentials)
        mcpRepository.importOAuthTokens(data.mcpOAuthTokens)
        modelRepository.importModelConfigurationMemory(data.modelConfigurationMemory)
        restoreRemoteTerminalPreferences(data.remoteTerminalPreferences)
        iconStore.restore(data.icons, imported.iconFiles)
        toolFiles.clearAll()
        imported.toolFiles.forEach { (name, file) ->
            FileInputStream(file).use { toolFiles.restore(name, it) }
        }
        remoteMachineService.replaceState(data.remoteMachines, imported.remoteIdentityFiles)
    }

    private fun exportRemoteTerminalPreferences(): RemoteTerminalPreferencesState {
        val values = context.getSharedPreferences(REMOTE_TERMINAL_UI_PREFERENCES, Context.MODE_PRIVATE).all
        return RemoteTerminalPreferencesState(
            ints = values.mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }.toMap(),
            booleans = values.mapNotNull { (key, value) -> (value as? Boolean)?.let { key to it } }.toMap(),
            strings = values.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
        )
    }

    private fun restoreRemoteTerminalPreferences(state: RemoteTerminalPreferencesState) {
        val editor = context.getSharedPreferences(REMOTE_TERMINAL_UI_PREFERENCES, Context.MODE_PRIVATE).edit().clear()
        state.ints.forEach { (key, value) -> editor.putInt(key, value) }
        state.booleans.forEach { (key, value) -> editor.putBoolean(key, value) }
        state.strings.forEach { (key, value) -> editor.putString(key, value) }
        check(editor.commit()) { "Could not restore remote terminal preferences." }
    }
}
