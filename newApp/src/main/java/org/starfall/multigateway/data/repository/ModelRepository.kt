package org.starfall.multigateway.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.starfall.multigateway.data.local.db.AppDatabase
import org.starfall.multigateway.data.local.db.SecretCipher
import org.starfall.multigateway.data.local.db.entities.*
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.data.service.IconStore
import org.starfall.multigateway.data.service.AiService

class ModelRepository(private val db: AppDatabase, private val service: AiService, private val icons: IconStore? = null) {
    suspend fun testConnection(provider: ModelProvider) = service.testConnection(provider)
    suspend fun testModel(provider: ModelProvider, modelId: String) = service.testModel(provider, modelId)
    suspend fun fetchProviderModelCatalog(provider: ModelProvider) = service.fetchProviderModelCatalog(provider)
    suspend fun fetchProviderModels(provider: ModelProvider) = service.fetchProviderModels(provider)
    suspend fun cachedOllamaModels(baseUrl: String) = service.cachedOllamaModels(baseUrl)
    suspend fun fetchOllamaModels(baseUrl: String) = service.fetchOllamaModels(baseUrl)
    internal fun rememberExistingModelConfigurations(providers: List<ModelProvider>) {
        providers.flatMap { it.config.modelConfigs.entries }
            .sortedBy { if (it.value.modelType == ModelType.TEXT_GENERATION) 1 else 0 }
            .forEach { (id, config) -> service.modelConfigurationMemory.remember(id, config, onlyIfMissing = true) }
    }

    private val providerDao = db.modelProviderDao()
    private val groupDao = db.providerGroupDao()

    // Rows are re-decoded on every Room invalidation; unchanged rows (same entity) reuse the previous result
    // so we don't pay Keystore decrypt + JSON decode for every provider on each emission.
    // Must be declared before `state`, which starts collecting from its constructor.
    private val decodedProviders = java.util.concurrent.ConcurrentHashMap<String, Pair<ModelProviderEntity, ModelProvider>>()
    private val modelsDao = db.providerModelsDao()

    private val storedProviders: Flow<List<ModelProvider>> = providerDao.getAllProviders().map { entities ->
        entities.map { providerEntityToModel(it) }.also {
            // Drop cached decodes of providers that no longer exist.
            decodedProviders.keys.retainAll(entities.mapTo(hashSetOf()) { e -> e.id })
        }
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
        providerDao.getAllProviders().first().map(::providerEntityToModel),
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

    suspend fun getProviderById(id: String): ModelProvider? {
        state.value?.let { return it.providers.firstOrNull { p -> p.id == id } }
        return withContext(Dispatchers.IO) {
            providerDao.getProviderById(id)?.let(::providerEntityToModel)
        }
    }

    suspend fun saveGroup(group: ProviderGroup) {
        state.mutate({ it.copy(groups = it.groups.upsert(group.copy(name = group.name.trim())) { g -> g.id }.sortedBy { g -> g.sortOrder }) }) {
            groupDao.insertOrUpdate(ProviderGroupEntity(group.id, group.name.trim(), group.sortOrder, group.icon))
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
                multipleApiKeys = authorized.config.multipleApiKeys))
    }

    suspend fun authorizeProvider(provider: ModelProvider): Result<ModelProvider> = runCatching {
        service.withOAuthSession {
            val authorized = service.authorizeProvider(provider).getOrThrow().recordOAuthAccount()
            var saved = authorized
            state.mutate({ catalog ->
                val updated = mergeOAuthAuthorization(catalog.providers.firstOrNull { it.id == provider.id }, authorized)
                catalog.copy(providers = catalog.providers.upsert(updated) { it.id }.sortedWith(providerOrder))
            }) {
                db.withTransaction {
                    val persisted = providerDao.getProviderById(provider.id)?.let(::providerEntityToModel)
                    if (persisted != null && persisted.type != authorized.type) clearAllOAuthCredentials(persisted)
                    saved = mergeOAuthAuthorization(persisted, authorized)
                    providerDao.insertOrUpdate(providerModelToEntity(saved))
                }
            }
            authorized.copy(config = authorized.config.copy(oauthAccounts = saved.config.oauthAccounts))
        }
    }

    suspend fun clearOAuthCredentials(provider: ModelProvider): Result<ModelProvider> = runCatching {
        val accountId = provider.oauthCredentialId
        state.mutate({ catalog -> catalog.copy(providers = catalog.providers.map {
            if (it.id == provider.id && it.type == provider.type) it.removeOAuthAccount(accountId) else it
        }) }) {
            service.clearAccountCredentials(provider.type, accountId)
            db.withTransaction {
                providerDao.getProviderById(provider.id)?.let(::providerEntityToModel)?.let { persisted ->
                    if (persisted.type == provider.type) providerDao.insertOrUpdate(providerModelToEntity(persisted.removeOAuthAccount(accountId)))
                }
            }
        }
        provider.removeOAuthAccount(accountId)
    }

    private fun clearAllOAuthCredentials(provider: ModelProvider) {
        (provider.oauthAccountsWithCurrent().map { it.id } + provider.id + provider.oauthCredentialId).distinct().forEach {
            service.clearAccountCredentials(provider.type, it)
        }
    }

    suspend fun providerExists(id: String): Boolean = providerDao.getProviderById(id) != null

    suspend fun saveProvider(draft: ModelProvider) {
        val provider = draft
        state.mutate({ it.saveProviderDraft(provider) }) {
            val previous = providerDao.getProviderById(provider.id)?.let(::providerEntityToModel)
            if (
                previous != null &&
                (previous.type != provider.type ||
                    (previous.auth.method == AuthMethod.OAUTH && provider.auth.method != AuthMethod.OAUTH))
            ) {
                clearAllOAuthCredentials(previous)
            }
            db.withTransaction {
                val catalog = readCatalog()
                val updated = catalog.saveProviderDraft(provider)
                persistDestinationOrder(updated, provider.groupId)
                providerDao.insertOrUpdate(providerModelToEntity(updated.providers.first { it.id == provider.id }))
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
        state.mutate({ it.mergeImport(importedProviders, importedGroups) }) {
            val changes = db.withTransaction {
                val previous = readCatalog()
                val updated = previous.mergeImport(importedProviders, importedGroups)
                importedGroups.orEmpty().forEach {
                    groupDao.insertOrUpdate(ProviderGroupEntity(it.id, it.name, it.sortOrder, it.icon))
                }
                val previousById = previous.providers.associateBy { it.id }
                updated.providers.filter { it.id in importedIds }.map { provider ->
                    providerDao.insertOrUpdate(providerModelToEntity(provider))
                    previousById[provider.id] to provider
                }
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
                val provider = providerEntityToModel(entity)
                providerDao.insertOrUpdate(providerModelToEntity(
                    provider.copy(config = provider.config.reorderedModels(modelIds))
                ))
            }
        }
    }

    suspend fun deleteProvider(id: String) {
        state.mutate({ it.copy(providers = it.providers.filterNot { p -> p.id == id }) }) {
            providerDao.getProviderById(id)?.let(::providerEntityToModel)?.let {
                clearAllOAuthCredentials(it)
            }
            providerDao.deleteById(id)
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
        val entity = modelsDao.getModelsForProvider(providerId) ?: return@withContext null
        val models: List<AiModel> = try {
            json.decodeFromString(entity.modelsJson)
        } catch (e: Exception) {
            emptyList()
        }
        ProviderModels(id = entity.id, models = models)
    }

    suspend fun saveModelsForProvider(providerModels: ProviderModels) = withContext(Dispatchers.IO) {
        modelsDao.insertOrUpdate(
            ProviderModelsEntity(
                id = providerModels.id,
                modelsJson = json.encodeToString(providerModels.models)
            )
        )
    }

    // (decoded-provider cache lives near the top of the class; it must exist before `state` starts collecting)
    private fun providerEntityToModel(entity: ModelProviderEntity): ModelProvider {
        decodedProviders[entity.id]?.let { (cachedEntity, model) -> if (cachedEntity == entity) return model }
        return decodeProviderEntity(entity).also { decodedProviders[entity.id] = entity to it }
    }

    private fun decodeProviderEntity(entity: ModelProviderEntity): ModelProvider {
        val type = try {
            ProviderType.valueOf(entity.type)
        } catch (e: Exception) {
            ProviderType.OPENAI
        }
        val authJson = SecretCipher.decrypt(entity.authJson)
        val configJson = SecretCipher.decrypt(entity.configJson)
        val auth: Authorization = try {
            json.decodeFromString(authJson)
        } catch (e: Exception) {
            Authorization()
        }
        val config: ProviderConfiguration = try {
            json.decodeFromString(configJson)
        } catch (e: Exception) {
            ProviderConfiguration()
        }
        return ModelProvider(
            id = entity.id,
            name = entity.name,
            type = type,
            auth = auth,
            icon = entity.icon,
            baseUrl = SecretCipher.decrypt(entity.baseUrl),
            config = config,
            sortOrder = entity.sortOrder,
            groupId = entity.groupId
        )
    }

    private fun providerModelToEntity(provider: ModelProvider): ModelProviderEntity {
        return ModelProviderEntity(
            id = provider.id,
            name = provider.name,
            type = provider.type.name,
            baseUrl = SecretCipher.encrypt(provider.baseUrl),
            authJson = SecretCipher.encrypt(json.encodeToString(provider.auth)),
            configJson = SecretCipher.encrypt(json.encodeToString(provider.config)),
            icon = provider.icon,
            sortOrder = provider.sortOrder,
            groupId = provider.groupId
        )
    }
}
