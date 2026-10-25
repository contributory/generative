package org.starfall.generative.shared.storage

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.starfall.generative.integrations.modelapis.AiService
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.model.auth.AuthMethod
import org.starfall.generative.shared.model.auth.CredentialLoadState
import org.starfall.generative.shared.model.language.LanguageModel
import org.starfall.generative.shared.model.language.Capabilities
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.provider.ProviderModels
import org.starfall.generative.shared.model.provider.ProviderPlacement
import org.starfall.generative.shared.model.provider.ProviderRootOrderItem
import org.starfall.generative.shared.model.provider.oauthAccountsWithCurrent
import org.starfall.generative.shared.model.provider.oauthCredentialId
import org.starfall.generative.shared.model.provider.recordOAuthAccount
import org.starfall.generative.shared.model.provider.removeOAuthAccount
import org.starfall.generative.shared.model.provider.reorderedModels
import org.starfall.generative.shared.storage.database.AppDatabase
import org.starfall.generative.shared.storage.database.entities.ProviderGroupEntity
import org.starfall.generative.shared.storage.database.entities.ProviderCatalogModelEntity
import org.starfall.generative.shared.storage.database.entities.ProviderCatalogToolEntity
import kotlinx.serialization.json.JsonObject

class ModelRepository(private val db: AppDatabase, private val service: AiService, private val icons: IconStore? = null) {
    suspend fun testConnection(provider: ModelProvider) = service.testConnection(resolveProvider(provider))
    suspend fun testModel(provider: ModelProvider, modelId: String) = service.testModel(resolveProvider(provider), modelId)
    suspend fun fetchProviderModelCatalog(provider: ModelProvider) = service.fetchProviderModelCatalog(resolveProvider(provider))
    suspend fun fetchProviderModels(provider: ModelProvider) = service.fetchProviderModels(resolveProvider(provider))
    suspend fun fetchOllamaModels(baseUrl: String) = service.fetchOllamaModels(baseUrl)
    internal fun rememberExistingModelConfigurations(providers: List<ModelProvider>) {
        providers.flatMap { it.config.modelConfigs.entries }
            .sortedBy { if (it.value.modelType == ModelType.TEXT_GENERATION) 1 else 0 }
            .forEach { (id, config) -> service.modelConfigurationMemory.remember(id, config, onlyIfMissing = true) }
    }

    private val providerDao = db.modelProviderDao()
    private val groupDao = db.providerGroupDao()

    private val structuredJsonDao = db.structuredJsonDao()
    private val mapper = ProviderStorageMapper(db)

    private val storedProviders: Flow<List<ModelProvider>> = providerDao.getAllProviders().map { entities ->
        db.withTransaction { entities.map { mapper.metadata(it) } }
    }

    private val storedGroups: Flow<List<ProviderGroup>> = groupDao.getAllGroups().map { groups ->
        groups.map { ProviderGroup(it.id, it.name, it.sortOrder, it.icon) }
    }

    private data class Catalog(val providers: List<ModelProvider>, val groups: List<ProviderGroup>)
    private val providerOrder = compareBy<ModelProvider> { it.sortOrder }.thenBy { it.id }

    /** Compact the destination order before appending, including legacy Int.MAX_VALUE ranks. */
    private fun Catalog.appendProvider(provider: ModelProvider): Catalog {
        val destination = buildList {
            providers.filter { it.id != provider.id && it.groupId == provider.groupId }
                .forEach { add(Triple(it.id, false, it.sortOrder)) }
            if (provider.groupId == null) groups.forEach { add(Triple(it.id, true, it.sortOrder)) }
        }.sortedWith(compareBy<Triple<String, Boolean, Int>> { it.third }.thenBy { if (it.second) 0 else 1 }.thenBy { it.first })
        val providerRanks = destination.mapIndexedNotNull { index, item -> if (item.second) null else item.first to index }.toMap()
        val groupRanks = destination.mapIndexedNotNull { index, item -> if (item.second) item.first to index else null }.toMap()
        val placed = provider.copy(sortOrder = destination.size)
        return copy(
            providers = providers.filterNot { it.id == provider.id }.map { p ->
                providerRanks[p.id]?.let { p.copy(sortOrder = it) } ?: p
            }.plus(placed).sortedWith(providerOrder),
            groups = groups.map { g -> groupRanks[g.id]?.let { g.copy(sortOrder = it) } ?: g }.sortedBy { it.sortOrder }
        )
    }

    private fun Catalog.saveProviderDraft(provider: ModelProvider): Catalog {
        val previous = providers.firstOrNull { it.id == provider.id }
        return if ((previous == null && provider.sortOrder == Int.MAX_VALUE) ||
            (previous != null && previous.groupId != provider.groupId)) appendProvider(provider)
        else copy(providers = providers.upsert(provider) { it.id }.sortedWith(providerOrder))
    }

    private suspend fun readCatalog() = Catalog(
        providerDao.getAllProviders().first().map { mapper.metadata(it) },
        groupDao.getAllGroupsOnce().map { ProviderGroup(it.id, it.name, it.sortOrder, it.icon) }
    )

    private suspend fun persistDestinationOrder(catalog: Catalog, groupId: String?) {
        catalog.providers.filter { it.groupId == groupId }.forEach {
            providerDao.updateGroupAndSortOrder(it.id, groupId, it.sortOrder)
        }
        if (groupId == null) catalog.groups.forEach { groupDao.updateSortOrder(it.id, it.sortOrder) }
    }

    private val state = ImmediateState(combine(storedProviders, storedGroups, ::Catalog))
    val allProviders: Flow<List<ModelProvider>> = state.flow.map { it.providers }.distinctUntilChanged()
    val allGroups: Flow<List<ProviderGroup>> = state.flow.map { it.groups }.distinctUntilChanged()

    suspend fun getProviderById(id: String): ModelProvider? = withContext(Dispatchers.IO) {
        db.withTransaction { providerDao.getProviderById(id)?.let { mapper.selected(it) } }
    }

    /** Resolve only this record's secrets, retaining the caller's public configuration edits. */
    suspend fun resolveProvider(provider: ModelProvider): ModelProvider = when (provider.credentialState) {
        CredentialLoadState.LOADED, CredentialLoadState.NOT_CONFIGURED -> provider
        CredentialLoadState.NOT_REQUESTED -> withContext(Dispatchers.IO) {
            db.withTransaction {
                val entity = providerDao.getProviderById(provider.id)
                    ?: error("Provider ${provider.id} no longer exists. Reload the provider list.")
                mapper.resolve(provider, entity)
            }
        }
        CredentialLoadState.ERROR, CredentialLoadState.LOADING -> error("Provider ${provider.id} credentials are ${provider.credentialState}. Reload before use.")
    }

    suspend fun loadProvidersForExport(): List<ModelProvider> = withContext(Dispatchers.IO) {
        db.withTransaction { providerDao.getAllProviders().first().map { mapper.selected(it) } }
    }

    internal fun exportAccountCredentials(): Map<String, Map<String, JsonObject>> = service.exportAccountCredentials()

    internal fun importAccountCredentials(credentials: Map<String, Map<String, JsonObject>>) {
        service.importAccountCredentials(credentials)
    }

    internal fun exportModelConfigurationMemory(): Map<String, JsonObject> = service.exportModelConfigurationMemory()

    internal fun importModelConfigurationMemory(values: Map<String, JsonObject>) {
        service.importModelConfigurationMemory(values)
    }

    suspend fun saveGroup(group: ProviderGroup) {
        state.mutate({ it.copy(groups = it.groups.upsert(group.copy(name = group.name.trim())) { g -> g.id }.sortedBy { g -> g.sortOrder }) }) {
            db.withTransaction { persistGroup(group.copy(name = group.name.trim())) }
        }
    }

    private suspend fun persistGroup(group: ProviderGroup) {
        if (!db.updateStorageRow("provider_groups", group.id, "name" to group.name, "sortOrder" to group.sortOrder, "icon" to group.icon)) {
            groupDao.insertOrUpdate(ProviderGroupEntity(group.id, group.name, group.sortOrder, group.icon))
        }
    }

    suspend fun deleteGroup(id: String) {
        state.mutate({ catalog ->
            val rootMax = maxOf(catalog.groups.filterNot { it.id == id }.maxOfOrNull { it.sortOrder } ?: -1,
                catalog.providers.filter { it.groupId == null }.maxOfOrNull { it.sortOrder } ?: -1)
            val children = catalog.providers.filter { it.groupId == id }.sortedBy { it.sortOrder }.map { it.id }
            catalog.copy(groups = catalog.groups.filterNot { it.id == id },
                providers = catalog.providers.map { p -> if (p.groupId == id) p.copy(groupId = null, sortOrder = rootMax + children.indexOf(p.id) + 1) else p })
        }) {
            db.withTransaction {
                val children = providerDao.getProvidersByGroup(id)
                val rootMax = maxOf(
                    groupDao.getAllGroupsOnce().filterNot { it.id == id }.maxOfOrNull { it.sortOrder } ?: -1,
                    providerDao.getUngroupedProviders().maxOfOrNull { it.sortOrder } ?: -1
                )
                children.forEachIndexed { index, provider ->
                    providerDao.updateGroupAndSortOrder(provider.id, null, rootMax + index + 1)
                }
                groupDao.deleteById(id)
            }
        }
    }

    suspend fun moveProviderToGroup(providerId: String, groupId: String?) {
        state.mutate({ catalog ->
            val provider = catalog.providers.firstOrNull { it.id == providerId }
            if (provider == null || provider.groupId == groupId) catalog
            else catalog.appendProvider(provider.copy(groupId = groupId))
        }) {
            db.withTransaction {
                val catalog = readCatalog()
                val provider = catalog.providers.firstOrNull { it.id == providerId }
                if (provider != null && provider.groupId != groupId) {
                    persistDestinationOrder(catalog.appendProvider(provider.copy(groupId = groupId)), groupId)
                }
            }
        }
    }

    private fun Catalog.placeProvider(placement: ProviderPlacement): Catalog {
        if (providers.none { it.id == placement.providerId } ||
            (placement.groupId != null && groups.none { it.id == placement.groupId })) return this
        var members = providers.map { if (it.id == placement.providerId) it.copy(groupId = placement.groupId) else it }
        val actualRoot = providerRootOrder(members, groups)
        val root = placement.rootOrder.filter { it in actualRoot }.distinct() + actualRoot.filter { it !in placement.rootOrder }
        val rootRanks = root.withIndex().associate { it.value to it.index }
        members = members.map { p ->
            rootRanks[ProviderRootOrderItem(p.id, false)]?.let { p.copy(sortOrder = it) } ?: p
        }
        placement.groupOrders.forEach { (groupId, requested) ->
            val actual = members.filter { it.groupId == groupId }.sortedWith(providerOrder).map { it.id }
            val order = requested.filter { it in actual }.distinct() + actual.filter { it !in requested }
            val ranks = order.withIndex().associate { it.value to it.index }
            members = members.map { p -> if (p.groupId == groupId) p.copy(sortOrder = ranks.getValue(p.id)) else p }
        }
        return copy(providers = members.sortedWith(providerOrder), groups = groups.map { g ->
            g.copy(sortOrder = rootRanks.getValue(ProviderRootOrderItem(g.id, true)))
        }.sortedBy { it.sortOrder })
    }

    private fun providerRootOrder(providers: List<ModelProvider>, groups: List<ProviderGroup>): List<ProviderRootOrderItem> =
        (groups.map { Triple(ProviderRootOrderItem(it.id, true), it.sortOrder, 0) } +
            providers.filter { it.groupId == null }.map { Triple(ProviderRootOrderItem(it.id, false), it.sortOrder, 1) })
            .sortedWith(compareBy<Triple<ProviderRootOrderItem, Int, Int>> { it.second }.thenBy { it.third }.thenBy { it.first.id })
            .map { it.first }

    suspend fun placeProvider(placement: ProviderPlacement) {
        state.mutate({ it.placeProvider(placement) }) {
            db.withTransaction {
                val updated = readCatalog().placeProvider(placement)
                updated.providers.forEach { providerDao.updateGroupAndSortOrder(it.id, it.groupId, it.sortOrder) }
                updated.groups.forEach { groupDao.updateSortOrder(it.id, it.sortOrder) }
            }
        }
    }

    suspend fun reorderRootItems(items: List<ProviderRootOrderItem>) {
        state.mutate({ catalog -> catalog.copy(
            providers = catalog.providers.map { p -> items.indexOfFirst { !it.isGroup && it.id == p.id }.takeIf { it >= 0 }?.let { p.copy(sortOrder = it) } ?: p },
            groups = catalog.groups.map { g -> items.indexOfFirst { it.isGroup && it.id == g.id }.takeIf { it >= 0 }?.let { g.copy(sortOrder = it) } ?: g })
        }) {
            db.withTransaction {
                items.forEachIndexed { index, item ->
                    if (item.isGroup) groupDao.updateSortOrder(item.id, index)
                    else providerDao.updateSortOrder(item.id, index)
                }
            }
        }
    }

    suspend fun reorderGroups(ids: List<String>) {
        state.mutate({ catalog -> catalog.copy(groups = catalog.groups.map { g -> ids.indexOf(g.id).takeIf { it >= 0 }?.let { g.copy(sortOrder = it) } ?: g }.sortedBy { it.sortOrder }) }) {
            db.withTransaction {
                ids.forEachIndexed { index, id -> groupDao.updateSortOrder(id, index) }
            }
        }
    }

    private fun mergeOAuthAuthorization(persisted: ModelProvider?, authorized: ModelProvider): ModelProvider {
        val accounts = (persisted?.takeIf { it.type == authorized.type }?.oauthAccountsWithCurrent().orEmpty() +
            authorized.config.oauthAccounts).associateBy { it.id }.values.toList()
        return (persisted ?: authorized).copy(type = authorized.type, baseUrl = authorized.baseUrl, auth = authorized.auth,
            config = (persisted?.config ?: authorized.config).copy(oauthAccounts = accounts,
                multipleApiKeys = authorized.config.multipleApiKeys,
                codexClientVersion = authorized.config.codexClientVersion,
                antigravityVersion = authorized.config.antigravityVersion,
                antigravityChangelist = authorized.config.antigravityChangelist,
                antigravityProjectIds = authorized.config.antigravityProjectIds),
            credentialState = CredentialLoadState.LOADED)
    }

    suspend fun authorizeProvider(provider: ModelProvider): Result<ModelProvider> = runCatching {
        service.withOAuthSession {
            val authorized = service.authorizeProvider(resolveProvider(provider)).getOrThrow().recordOAuthAccount()
                .copy(credentialState = CredentialLoadState.LOADED)
            var saved = authorized
            state.mutate({ catalog ->
                val updated = authorized.metadata()
                catalog.copy(providers = catalog.providers.upsert(updated) { it.id }.sortedWith(providerOrder))
            }) {
                db.withTransaction {
                    val persisted = providerForCredentialCleanup(provider.id)
                    if (persisted != null && persisted.type != authorized.type) clearAllOAuthCredentials(persisted)
                    saved = mergeOAuthAuthorization(persisted, authorized)
                    mapper.persist(saved)
                }
            }
            authorized.copy(config = authorized.config.copy(oauthAccounts = saved.config.oauthAccounts))
        }
    }

    suspend fun clearOAuthCredentials(provider: ModelProvider): Result<ModelProvider> = runCatching {
        val resolved = resolveProvider(provider)
        val accountId = resolved.oauthCredentialId
        state.mutate({ it }) {
            service.clearAccountCredentials(resolved.type, accountId)
            db.withTransaction {
                providerDao.getProviderById(provider.id)?.let { mapper.selected(it) }?.let { persisted ->
                    if (persisted.type == resolved.type) mapper.persist(persisted.removeOAuthAccount(accountId))
                }
            }
        }
        resolved.removeOAuthAccount(accountId)
    }

    private fun clearAllOAuthCredentials(provider: ModelProvider) {
        (provider.oauthAccountsWithCurrent().map { it.id } + provider.id + provider.oauthCredentialId).distinct().forEach {
            service.clearAccountCredentials(provider.type, it)
        }
    }

    /** Credential replacement/deletion remains possible when the old encrypted record is damaged. */
    private suspend fun providerForCredentialCleanup(id: String): ModelProvider? = providerDao.getProviderById(id)?.let { entity ->
        try { mapper.selected(entity) } catch (e: CancellationException) { throw e }
        catch (_: IllegalStateException) { mapper.metadata(entity) }
    }

    suspend fun providerExists(id: String): Boolean = providerDao.getProviderById(id) != null

    suspend fun saveProvider(draft: ModelProvider) {
        val provider = draft
        requireSavableCredentials(provider.credentialState)
        state.mutate({ it.saveProviderDraft(provider.metadata()) }) {
            val previous = db.withTransaction {
                val catalog = readCatalog()
                val previous = catalog.providers.firstOrNull { it.id == provider.id }
                if (provider.credentialState != CredentialLoadState.NOT_REQUESTED && previous != null) {
                    // Only a credential edit needs the old OAuth account references.
                    providerForCredentialCleanup(provider.id)?.let { old ->
                        if (old.type != provider.type || (old.auth.method == AuthMethod.OAUTH && provider.auth.method != AuthMethod.OAUTH)) {
                            clearAllOAuthCredentials(old)
                        }
                    }
                }
                val updated = catalog.saveProviderDraft(provider)
                persistDestinationOrder(updated, provider.groupId)
                mapper.persist(updated.providers.first { it.id == provider.id })
                previous
            }
            provider.config.modelConfigs.forEach { (id, config) ->
                if (previous?.config?.modelConfigs?.get(id) != config) service.modelConfigurationMemory.remember(id, config)
            }
        }
    }

    /** Restore the exported layout directly; editor saves append providers when their group changes. */
    suspend fun importProviders(providers: List<ModelProvider>, groups: List<ProviderGroup>?) {
        val importedProviders = providers
        val importedGroups = groups?.map { it.copy(name = it.name.trim()) }
        val importedIds = importedProviders.mapTo(hashSetOf()) { it.id }
        importedProviders.forEach { requireSavableCredentials(it.credentialState) }
        state.mutate({ it.mergeImport(importedProviders.map { p -> p.metadata() }, importedGroups) }) {
            val changes = db.withTransaction {
                val previous = readCatalog()
                val updated = previous.mergeImport(importedProviders, importedGroups)
                importedGroups.orEmpty().forEach {
                    persistGroup(it)
                }
                val previousById = previous.providers.associateBy { it.id }
                val importedChanges = mutableListOf<Pair<ModelProvider?, ModelProvider>>()
                updated.providers.filter { it.id in importedIds }.forEach { provider ->
                    val old = previousById[provider.id]?.let {
                        if (provider.credentialState == CredentialLoadState.NOT_REQUESTED) it else providerForCredentialCleanup(provider.id)
                    }
                    mapper.persist(provider)
                    importedChanges += old to provider
                }
                importedChanges
            }
            changes.forEach { (previous, provider) ->
                if (previous != null && (previous.type != provider.type ||
                        (previous.auth.method == AuthMethod.OAUTH && provider.auth.method != AuthMethod.OAUTH))) {
                    clearAllOAuthCredentials(previous)
                }
                provider.config.modelConfigs.forEach { (id, config) ->
                    if (previous?.config?.modelConfigs?.get(id) != config) service.modelConfigurationMemory.remember(id, config)
                }
            }
        }
    }

    /** Replaces the complete provider catalog during a full application restore. */
    suspend fun replaceAll(providers: List<ModelProvider>, groups: List<ProviderGroup>) {
        providerDao.getAllProviders().first().map { it.id }.forEach { deleteProvider(it) }
        groupDao.getAllGroupsOnce().map { it.id }.forEach { deleteGroup(it) }
        importProviders(providers, groups)
    }

    private fun Catalog.mergeImport(importedProviders: List<ModelProvider>, importedGroups: List<ProviderGroup>?): Catalog {
        val mergedGroups = (groups + importedGroups.orEmpty()).associateBy { it.id }.values.toList()
        val groupIds = mergedGroups.mapTo(hashSetOf()) { it.id }
        val previousById = providers.associateBy { it.id }
        val restored = importedProviders.map { provider ->
            val previous = previousById[provider.id]
            when {
                // Old backups cannot restore a complete layout. Preserve the local organization
                // for existing providers rather than overwriting it with missing/stale group IDs.
                importedGroups == null && previous != null -> provider.copy(groupId = previous.groupId, sortOrder = previous.sortOrder)
                provider.groupId != null && provider.groupId !in groupIds -> provider.copy(groupId = null)
                else -> provider
            }
        }
        return Catalog(
            providers = (providers + restored).associateBy { it.id }.values.sortedWith(providerOrder),
            groups = mergedGroups.sortedWith(compareBy<ProviderGroup> { it.sortOrder }.thenBy { it.id })
        )
    }

    suspend fun reorderProviderModels(providerId: String, modelIds: List<String>) {
        state.mutate({ catalog -> catalog.copy(providers = catalog.providers.map { if (it.id == providerId) it.copy(config = it.config.reorderedModels(modelIds)) else it }) }) {
            db.withTransaction {
                val entity = providerDao.getProviderById(providerId) ?: return@withTransaction
                val provider = mapper.metadata(entity)
                mapper.persist(provider.copy(config = provider.config.reorderedModels(modelIds)))
            }
        }
    }

    suspend fun deleteProvider(id: String) {
        state.mutate({ it.copy(providers = it.providers.filterNot { p -> p.id == id }) }) {
            db.withTransaction {
                providerDao.getProviderById(id)?.let { entity ->
                    // An unreadable auth row must not prevent deletion of the selected record.
                    val provider = providerForCredentialCleanup(id) ?: return@let
                    clearAllOAuthCredentials(provider)
                    mapper.deleteDocuments(id)
                    providerDao.deleteById(id)
                    entity.authId?.let { authId -> db.deleteUnreferencedEndpointAuth("provider_auth", "model_providers", authId) }
                }
            }
        }
    }

    suspend fun reorderProviders(ids: List<String>) {
        state.mutate({ catalog -> catalog.copy(providers = catalog.providers.map { p -> ids.indexOf(p.id).takeIf { it >= 0 }?.let { p.copy(sortOrder = it) } ?: p }.sortedBy { it.sortOrder }) }) {
            db.withTransaction {
                ids.forEachIndexed { index, id -> providerDao.updateSortOrder(id, index) }
            }
        }
    }

    suspend fun getModelsForProvider(providerId: String): ProviderModels? = withContext(Dispatchers.IO) {
        val entities = providerDao.getCatalogModels(providerId)
        if (entities.isEmpty() && structuredJsonDao.getDocument("provider_catalog", providerId, "cache_state") == null) return@withContext null
        val tools = providerDao.getCatalogTools(providerId).groupBy { it.modelOrdinal }
        ProviderModels(providerId, entities.sortedBy { it.ordinal }.map { model ->
            LanguageModel(
                id = model.modelId,
                displayName = model.displayName,
                icon = model.icon,
                providerId = model.catalogProviderId,
                inputCapabilities = Capabilities(model.inputText, model.inputImage, model.inputVideo, model.inputEmbed, model.inputAudio, model.inputOthers),
                outputCapabilities = Capabilities(model.outputText, model.outputImage, model.outputVideo, model.outputEmbed, model.outputAudio, model.outputOthers),
                modelTools = tools[model.ordinal].orEmpty().sortedBy { it.ordinal }.map { it.toolName }
            )
        })
    }

    suspend fun saveModelsForProvider(providerModels: ProviderModels) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (providerDao.getProviderById(providerModels.id) == null) return@withTransaction
            providerDao.deleteCatalogTools(providerModels.id)
            providerDao.deleteCatalogModels(providerModels.id)
            providerModels.models.forEachIndexed { ordinal, model ->
                providerDao.insertCatalogModels(listOf(ProviderCatalogModelEntity(
                    providerId = providerModels.id,
                    ordinal = ordinal,
                    modelId = model.id,
                    displayName = model.displayName,
                    icon = model.icon,
                    catalogProviderId = model.providerId,
                    inputText = model.inputCapabilities.text,
                    inputImage = model.inputCapabilities.image,
                    inputVideo = model.inputCapabilities.video,
                    inputEmbed = model.inputCapabilities.embed,
                    inputAudio = model.inputCapabilities.audio,
                    inputOthers = model.inputCapabilities.others,
                    outputText = model.outputCapabilities.text,
                    outputImage = model.outputCapabilities.image,
                    outputVideo = model.outputCapabilities.video,
                    outputEmbed = model.outputCapabilities.embed,
                    outputAudio = model.outputCapabilities.audio,
                    outputOthers = model.outputCapabilities.others
                )))
                providerDao.insertCatalogTools(model.modelTools.mapIndexed { toolOrdinal, tool ->
                    ProviderCatalogToolEntity(providerModels.id, ordinal, toolOrdinal, tool)
                })
            }
            structuredJsonDao.replaceDocument(StructuredJsonDocument("provider_catalog", providerModels.id, "cache_state", JsonPrimitive("READY")))
        }
    }
}
