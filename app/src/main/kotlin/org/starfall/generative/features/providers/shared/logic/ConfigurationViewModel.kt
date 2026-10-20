package org.starfall.generative.features.providers.shared.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.starfall.generative.shared.model.ChatProfile
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.ToolDefinition
import org.starfall.generative.shared.model.auth.CredentialLoadState
import org.starfall.generative.shared.model.language.DiscoveredModel
import org.starfall.generative.shared.model.language.defaultNativeModelTools
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.ThinkingPassback
import org.starfall.generative.shared.model.language.passbackMode
import org.starfall.generative.shared.model.language.withSelectedReasoningEffort
import org.starfall.generative.shared.model.language.withThinkingPassback
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.provider.ProviderPlacement
import org.starfall.generative.shared.model.provider.ProviderRootOrderItem
import org.starfall.generative.shared.storage.LocalWriteErrors
import org.starfall.generative.shared.storage.McpRepository
import org.starfall.generative.shared.storage.ModelRepository
import org.starfall.generative.shared.storage.ProfileRepository
import org.starfall.generative.shared.storage.SpeechRepository
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.storage.preferences.AppPreferencesRepository

class ConfigurationViewModel(
    private val profileRepo: ProfileRepository,
    private val modelRepo: ModelRepository,
    private val mcpRepo: McpRepository,
    private val speechRepo: SpeechRepository,
    private val prefsRepo: AppPreferencesRepository,
) : ViewModel() {
    private val appPreferences = prefsRepo.appPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())
    val speechServices = speechRepo.allServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val providerGroups = modelRepo.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Editors must receive a selected full record, never a metadata-list placeholder. */
    suspend fun loadProviderForEditor(id: String): ModelProvider =
        (modelRepo.getProviderById(id)
            ?: error("This provider no longer exists. Go back to Providers and select an existing provider."))
            .also { requireLoadedCredentials(it.credentialState) }

    suspend fun loadMcpServerForEditor(id: String): McpInfo =
        (mcpRepo.getById(id)
            ?: error("This MCP server no longer exists. Go back to MCP and select an existing server."))
            .also { requireLoadedCredentials(it.credentialState) }

    suspend fun loadProvidersForExport(): List<ModelProvider> =
        modelRepo.loadProvidersForExport().onEach { requireLoadedCredentials(it.credentialState) }

    suspend fun loadServersForExport(): List<McpInfo> =
        mcpRepo.loadServersForExport().onEach { requireLoadedCredentials(it.credentialState) }

    private fun requireLoadedCredentials(state: CredentialLoadState) {
        check(state == CredentialLoadState.LOADED || state == CredentialLoadState.NOT_CONFIGURED) {
            "Saved credentials are not loaded. Retry before editing or exporting this record."
        }
    }

    // Metadata saves retain the encrypted auth row; ordinary model changes must not resolve it.
    private suspend fun providerMetadataById(id: String): ModelProvider? =
        modelRepo.allProviders.first().firstOrNull { it.id == id }

    private val _importedProviderId = MutableStateFlow<String?>(null)
    val importedProviderId = _importedProviderId.asStateFlow()
    private val _providerImportError = MutableStateFlow<String?>(null)
    val providerImportError = _providerImportError.asStateFlow()

    fun importProvider(provider: ModelProvider) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            try {
                // A link that was already imported just opens the existing provider.
                if (!modelRepo.providerExists(provider.id)) modelRepo.saveProvider(provider)
                _importedProviderId.value = provider.id
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _providerImportError.value = "Could not save the imported provider. Please try again."
            }
        }
    }

    suspend fun importProviders(providers: List<ModelProvider>, groups: List<ProviderGroup>?) {
        modelRepo.importProviders(providers, groups)
    }

    fun providerImportOpened(id: String) { _importedProviderId.compareAndSet(id, null) }
    fun clearProviderImportError() { _providerImportError.value = null }

    private val _mcpToolsCache = MutableStateFlow<Map<String, List<ToolDefinition>>>(emptyMap())
    val mcpToolsCache: StateFlow<Map<String, List<ToolDefinition>>> = _mcpToolsCache.asStateFlow()

    private val _mcpToolErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val mcpToolErrors: StateFlow<Map<String, String>> = _mcpToolErrors.asStateFlow()

    private val _mcpToolsLoading = MutableStateFlow<Set<String>>(emptySet())
    val mcpToolsLoading: StateFlow<Set<String>> = _mcpToolsLoading.asStateFlow()

    suspend fun discoverTools(server: McpInfo): List<ToolDefinition> =
        _mcpToolsCache.value[server.id]
            ?: server.cachedTools
            ?: refreshMcpTools(server).getOrThrow()

    suspend fun refreshMcpTools(server: McpInfo): Result<List<ToolDefinition>> {
        _mcpToolsLoading.update { it + server.id }
        return try {
            val tools = mcpRepo.discoverTools(server)
            mcpRepo.saveCachedTools(server.id, tools)
            _mcpToolsCache.update { it + (server.id to tools) }
            _mcpToolErrors.update { it - server.id }
            Result.success(tools)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (error: Throwable) {
            val fullError = error.stackTraceToString().takeIf { it.isNotBlank() }
                ?: error.localizedMessage
                ?: "Unable to load tools"
            _mcpToolErrors.update { it + (server.id to fullError) }
            Result.failure(error)
        } finally {
            _mcpToolsLoading.update { it - server.id }
        }
    }

    fun clearMcpToolsState(serverId: String) {
        _mcpToolsCache.update { it - serverId }
        _mcpToolErrors.update { it - serverId }
        _mcpToolsLoading.update { it - serverId }
    }

    fun saveProfile(profile: ChatProfile) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            profileRepo.saveProfile(profile)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            profileRepo.deleteProfile(profileId)
            if (appPreferences.value.selectedProfileId == profileId) {
                prefsRepo.setSelectedProfileId(null)
            }
        }
    }

    fun saveModelConfiguration(providerId: String, modelId: String, config: ModelConfiguration) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            val provider = providerMetadataById(providerId) ?: return@launch
            modelRepo.saveProvider(provider.copy(config = provider.config.copy(
                modelConfigs = provider.config.modelConfigs + (modelId to config)
            )))
        }
    }

    /** Persists the level picked in the chat's selected-model sheet. Off ("none") only applies to the conversation; the model's on/off switch lives in its configuration. */
    fun setModelReasoningEffort(providerId: String, modelId: String, effort: String?) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            val provider = providerMetadataById(providerId) ?: return@launch
            val config = provider.config.modelConfigs[modelId] ?: return@launch
            val updated = config.withSelectedReasoningEffort(effort)
            if (updated == config) return@launch
            modelRepo.saveProvider(provider.copy(config = provider.config.copy(
                modelConfigs = provider.config.modelConfigs + (modelId to updated)
            )))
        }
    }

    fun setModelTools(providerId: String, modelId: String, enabledTools: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            val provider = providerMetadataById(providerId) ?: return@launch
            val existing = provider.config.modelConfigs[modelId] ?: ModelConfiguration()
            val supportedTools = existing.supportedModelTools.ifEmpty {
                defaultNativeModelTools(provider.type, modelId)
            }
            val enabled = enabledTools.filter { it in supportedTools }.distinct()
            val updated = existing.copy(
                modelTools = existing.modelTools.ifEmpty { supportedTools },
                enabledModelTools = enabled
            )
            if (updated == existing) return@launch
            val modelIds = provider.config.modelIds
            modelRepo.saveProvider(provider.copy(config = provider.config.copy(
                modelIds = if (modelIds != null && modelId !in modelIds) modelIds + modelId else modelIds,
                modelConfigs = provider.config.modelConfigs + (modelId to updated)
            )))
        }
    }

    fun setThinkingPassback(providerId: String, modelId: String, mode: ThinkingPassback) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            val provider = providerMetadataById(providerId) ?: return@launch
            val existing = provider.config.modelConfigs[modelId]
            // A freshly selected model may not have a saved configuration yet (it is written
            // asynchronously on selection); create it here so the choice is never lost.
            val config = existing ?: ModelConfiguration()
            if (existing != null && existing.passbackMode == mode && existing.thinkingPassback == mode) return@launch
            val modelIds = provider.config.modelIds
            modelRepo.saveProvider(provider.copy(config = provider.config.copy(
                modelIds = if (modelIds != null && modelId !in modelIds) modelIds + modelId else modelIds,
                modelConfigs = provider.config.modelConfigs + (modelId to config.withThinkingPassback(mode))
            )))
        }
    }

    fun reorderProviderModels(providerId: String, modelIds: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { modelRepo.reorderProviderModels(providerId, modelIds) }
    }

    fun saveProviderModels(providerId: String, modelConfigs: Map<String, ModelConfiguration>) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            val provider = providerMetadataById(providerId) ?: return@launch
            val modelIds = modelConfigs.keys.toList()
            modelRepo.saveProvider(provider.copy(config = provider.config.copy(
                modelConfigs = modelConfigs,
                modelIds = modelIds
            )))
            val prefs = appPreferences.value
            if (prefs.selectedProviderId == providerId && prefs.selectedModelId !in modelIds) {
                prefsRepo.setSelectedModel(providerId, modelIds.firstOrNull().orEmpty())
            }
        }
    }

    fun saveProvider(provider: ModelProvider) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            modelRepo.saveProvider(provider)
            val prefs = appPreferences.value
            val modelIds = provider.config.modelIds
            if (prefs.selectedProviderId == provider.id && modelIds != null && prefs.selectedModelId !in modelIds) {
                prefsRepo.setSelectedModel(provider.id, modelIds.firstOrNull().orEmpty())
            }
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            modelRepo.deleteProvider(providerId)
        }
    }

    fun saveProviderGroup(group: ProviderGroup) {
        viewModelScope.launch(LocalWriteErrors.handler) { modelRepo.saveGroup(group) }
    }

    fun deleteProviderGroup(groupId: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { modelRepo.deleteGroup(groupId) }
    }

    suspend fun placeProvider(placement: ProviderPlacement): Result<Unit> = try {
        modelRepo.placeProvider(placement)
        Result.success(Unit)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    /** Start a provider placement from the application-scoped ViewModel. The provider screen may
     * be removed immediately after a drop, so the write must not be owned by its composition scope. */
    fun queueProviderPlacement(placement: ProviderPlacement) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            modelRepo.placeProvider(placement)
        }
    }

    fun moveProviderToGroup(providerId: String, groupId: String?) {
        viewModelScope.launch(LocalWriteErrors.handler) { modelRepo.moveProviderToGroup(providerId, groupId) }
    }

    fun reorderProviderGroups(ids: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { modelRepo.reorderGroups(ids) }
    }

    fun reorderProviderRootItems(items: List<ProviderRootOrderItem>) {
        viewModelScope.launch(LocalWriteErrors.handler) { modelRepo.reorderRootItems(items) }
    }

    private val oauthAuthorizations = OAuthAuthorizations(viewModelScope, modelRepo::authorizeProvider)

    suspend fun authorizeProvider(provider: ModelProvider): Result<ModelProvider> =
        oauthAuthorizations.authorize(provider)

    fun cancelProviderAuthorization(provider: ModelProvider) {
        oauthAuthorizations.cancel(provider)
    }

    suspend fun clearOAuthCredentials(provider: ModelProvider): Result<ModelProvider> =
        modelRepo.clearOAuthCredentials(provider)

    suspend fun testConnection(provider: ModelProvider, modelId: String): Result<String> {
        return modelRepo.testModel(provider, modelId)
    }

    suspend fun fetchProviderModels(provider: ModelProvider): List<DiscoveredModel> =
        modelRepo.fetchProviderModelCatalog(provider)

    suspend fun fetchOllamaModels(baseUrl: String): List<String> {
        return modelRepo.fetchOllamaModels(baseUrl)
    }

    suspend fun authorizeMcpOAuth(server: McpInfo): Result<McpInfo> =
        mcpRepo.authorizeOAuth(server)

    suspend fun clearMcpOAuth(server: McpInfo): McpInfo =
        mcpRepo.clearOAuth(server)

    fun saveMcpServer(server: McpInfo) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            mcpRepo.saveServer(server)
            refreshMcpTools(server)
        }
    }

    suspend fun importMcpServers(servers: List<McpInfo>) {
        servers.forEach { mcpRepo.saveServer(it) }
    }

    fun deleteMcpServer(serverId: String) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            mcpRepo.deleteServer(serverId)
            clearMcpToolsState(serverId)
        }
    }
    fun saveSpeechService(service: SpeechService) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            speechRepo.saveService(service)
        }
    }

    suspend fun importSpeechServices(services: List<SpeechService>) {
        services.forEach { speechRepo.saveService(it) }
    }

    fun deleteSpeechService(serviceId: String) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            speechRepo.deleteService(serviceId)
        }
    }

    fun reorderProfiles(ids: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { profileRepo.reorderProfiles(ids) }
    }

    fun reorderProviders(ids: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { modelRepo.reorderProviders(ids) }
    }

    fun reorderMcpServers(ids: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { mcpRepo.reorderServers(ids) }
    }

    fun reorderSpeechServices(ids: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { speechRepo.reorderServices(ids) }
    }

    fun setShowProfilesAsGrid(isGrid: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { prefsRepo.setShowProfilesAsGrid(isGrid) }
    }

    fun setShowProvidersAsGrid(isGrid: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { prefsRepo.setShowProvidersAsGrid(isGrid) }
    }

    fun setShowMcpAsGrid(isGrid: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { prefsRepo.setShowMcpAsGrid(isGrid) }
    }

    fun setShowSpeechAsGrid(isGrid: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { prefsRepo.setShowSpeechAsGrid(isGrid) }
    }
}
