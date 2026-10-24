package org.starfall.generative.shared.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.auth.AuthMethod
import org.starfall.generative.shared.model.auth.Authorization
import org.starfall.generative.shared.model.auth.CredentialLoadState
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.language.ThinkingPassback
import org.starfall.generative.shared.model.media.MediaGenerationMode
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderConfiguration
import org.starfall.generative.shared.storage.database.AppDatabase
import org.starfall.generative.shared.storage.database.SecretCipher
import org.starfall.generative.shared.storage.database.entities.ModelProviderEntity
import org.starfall.generative.shared.storage.database.entities.ProviderAuthEntity
import org.starfall.generative.shared.storage.database.entities.ProviderModelConfigurationEntity
import org.starfall.generative.shared.storage.database.entities.ProviderModelSelectionEntity
import org.starfall.generative.shared.storage.database.entities.ProviderModelValueEntity

/** Callers hold a Room transaction. Existing parents/auth must never pass through INSERT REPLACE. */
internal fun AppDatabase.updateStorageRow(table: String, id: String, vararg columns: Pair<String, Any?>): Boolean {
    check(inTransaction()) { "Storage updates require a transaction." }
    val values = ContentValues().apply {
        columns.forEach { (key, value) ->
            when (value) {
                null -> putNull(key)
                is String -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                is Boolean -> put(key, value)
                else -> error("Unsupported storage column type for $key")
            }
        }
    }
    return openHelper.writableDatabase.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(id)) > 0
}

internal fun AppDatabase.deleteUnreferencedEndpointAuth(table: String, parent: String, id: String) {
    check(inTransaction()) { "Storage deletes require a transaction." }
    openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE id = ? AND NOT EXISTS (SELECT 1 FROM $parent WHERE authId = ?)", arrayOf(id, id))
}

internal class ProviderStorageMapper(private val db: AppDatabase) {
    private val dao = db.modelProviderDao()
    private val structured = db.structuredJsonDao()

    suspend fun metadata(entity: ModelProviderEntity): ModelProvider {
        val selections = dao.getModelSelection(entity.id).sortedBy { it.ordinal }
        val values = dao.getModelValues(entity.id).groupBy { it.modelId to it.listKind }
        val configs = LinkedHashMap<String, ModelConfiguration>()
        dao.getModelConfigurations(entity.id).sortedBy { it.ordinal }.forEach { model ->
            fun list(kind: String) = values[model.modelId to kind].orEmpty().sortedBy { it.ordinal }.map { it.value }
            configs[model.modelId] = ModelConfiguration(
                temperature = model.temperature, topP = model.topP, topK = model.topK, supportStream = model.supportStream,
                displayName = model.displayName, contextWindowTokens = model.contextWindowTokens,
                modelType = runCatching { ModelType.valueOf(model.modelType) }.getOrDefault(ModelType.TEXT_GENERATION),
                supportsVision = model.supportsVision, supportsVideoInput = model.supportsVideoInput,
                supportsAudioInput = model.supportsAudioInput, supportsThinking = model.supportsThinking,
                reasoningEffort = model.reasoningEffort, reasoningEfforts = list("reasoning_efforts"), thinkingLevel = model.thinkingLevel,
                supportsToolCalls = model.supportsToolCalls, modelTools = list("model_tools"), enabledModelTools = list("enabled_model_tools"),
                legacySendThinkingContent = model.legacySendThinkingContent,
                 thinkingPassback = model.thinkingPassback?.let { runCatching { ThinkingPassback.valueOf(it) }.getOrNull() },
                 icon = model.icon,
                 mediaApiType = runCatching { MediaGenerationMode.valueOf(model.mediaApiType) }
                     .getOrDefault(MediaGenerationMode.DEFAULT),
                 mediaRequestProfileId = model.mediaRequestProfileId,
                 modelJson = structured.readDocument("provider_model", "${entity.id}/${model.modelId}", "model_json") as? JsonObject
                    ?: JsonObject(emptyMap())
            )
        }
        val policy = if (entity.authId == null) {
            (structured.readDocument("provider", entity.id, "auth_method") as? JsonPrimitive)?.content
                ?.let { AuthMethod.valueOf(it) } ?: AuthMethod.PLATFORM_DEFAULT
        } else AuthMethod.PLATFORM_DEFAULT
        return ModelProvider(
            id = entity.id, name = entity.name,
            type = runCatching { ModelApiType.valueOf(entity.type) }.getOrDefault(ModelApiType.OPENAI),
            auth = Authorization(policy), icon = entity.icon, baseUrl = EndpointSecrets.split(entity.baseUrl).publicUrl.orEmpty(),
            config = ProviderConfiguration(
                supportStream = entity.supportStream, responsesApi = entity.responsesApi,
                customListModelsUrl = EndpointSecrets.split(entity.customListModelsUrl).publicUrl,
                maxTokens = entity.maxTokens, modelConfigs = configs,
                modelIds = if (entity.modelSelectionMode == "DISCOVERY") null else selections.map { it.modelId },
                multipleApiKeys = entity.multipleApiKeys,
                codexClientVersion = (structured.readDocument("provider", entity.id, "codex_client_version") as? JsonPrimitive)?.content,
                antigravityVersion = (structured.readDocument("provider", entity.id, "antigravity_version") as? JsonPrimitive)?.content,
                antigravityChangelist = (structured.readDocument("provider", entity.id, "antigravity_changelist") as? JsonPrimitive)?.content,
                antigravityProjectIds = (structured.readDocument("provider", entity.id, "antigravity_project_ids") as? JsonObject)
                    ?.mapNotNull { (id, value) -> (value as? JsonPrimitive)?.contentOrNull?.trim()
                        ?.takeIf(String::isNotEmpty)?.let { id to it } }?.toMap().orEmpty()
            ),
            sortOrder = entity.sortOrder, groupId = entity.groupId,
            credentialState = if (entity.authId == null) CredentialLoadState.NOT_CONFIGURED else CredentialLoadState.NOT_REQUESTED
        )
    }

    suspend fun selected(entity: ModelProviderEntity): ModelProvider =
        metadata(entity).let { if (entity.authId == null) it else resolve(it, entity) }

    suspend fun resolve(provider: ModelProvider, entity: ModelProviderEntity): ModelProvider {
        check(provider.type.name == entity.type) { "Provider ${provider.id} changed type. Reload it before resolving credentials." }
        val authId = entity.authId ?: error("Provider ${provider.id} credentials are no longer configured. Reload the provider.")
        val auth = dao.getAuth(authId)
            ?: error("Provider ${provider.id} authentication record is missing. Re-enter credentials; saved metadata has been retained.")
        val payload = decodeEndpointAuth<ProviderAuthPayload>("Provider ${provider.id}", auth.encryptedPayload,
            auth.payloadVersion, auth.cipherVersion, auth.state)
        // v1 migration payloads stored suffixes. v2 always stores the complete original endpoint.
        val privateBaseUrl = payload.privateBaseUrl?.let {
            if (auth.payloadVersion == 1 && (it.startsWith("?") || it.startsWith("#"))) entity.baseUrl + it else it
        } ?: EndpointSecrets.split(entity.baseUrl).privateUrl
        return provider.copy(
            auth = payload.authorization,
            baseUrl = restoreEndpoint(provider.baseUrl, privateBaseUrl).orEmpty(),
            config = provider.config.copy(headers = payload.headers, httpProxy = payload.httpProxy, socksProxy = payload.socksProxy,
                apiKeys = payload.apiKeys, oauthAccounts = payload.oauthAccounts,
                customListModelsUrl = restoreEndpoint(provider.config.customListModelsUrl,
                    payload.privateCustomListModelsUrl ?: entity.customListModelsUrl?.takeIf { EndpointSecrets.split(it).privateUrl != null })),
            credentialState = CredentialLoadState.LOADED
        )
    }

    suspend fun persist(provider: ModelProvider) {
        requireSavableCredentials(provider.credentialState)
        val previous = dao.getProviderById(provider.id)
        val preserve = provider.credentialState == CredentialLoadState.NOT_REQUESTED
        val payload = provider.authPayload()
        if (preserve) {
            check(previous?.authId != null) { "Provider ${provider.id} credentials are no longer available. Reload before saving." }
            check(previous.type == provider.type.name && !payload.hasSecrets()) {
                "Resolve provider ${provider.id} credentials before changing authentication, provider type, or private endpoints."
            }
            check(EndpointSecrets.split(previous.baseUrl).privateUrl == null && EndpointSecrets.split(previous.customListModelsUrl).privateUrl == null) {
                "Provider ${provider.id} has a legacy private endpoint. Open and save the selected provider before editing metadata."
            }
        }
        val authId = if (preserve) previous?.authId else if (payload.hasSecrets()) previous?.authId ?: "provider-auth-${provider.id}" else null
        if (!preserve && authId != null) {
            val auth = ProviderAuthEntity(authId, SecretCipher.encrypt(json.encodeToString(payload)), 2, 1, "READY", System.currentTimeMillis())
            if (!db.updateStorageRow("provider_auth", auth.id, "encryptedPayload" to auth.encryptedPayload,
                    "payloadVersion" to auth.payloadVersion, "cipherVersion" to auth.cipherVersion, "state" to auth.state, "updatedAt" to auth.updatedAt)) {
                dao.insertAuth(auth)
            }
        }
        val entity = ModelProviderEntity(provider.id, provider.name, provider.type.name,
            EndpointSecrets.split(provider.baseUrl).publicUrl.orEmpty(), provider.icon, provider.sortOrder, provider.groupId, authId,
            provider.config.supportStream, provider.config.responsesApi, EndpointSecrets.split(provider.config.customListModelsUrl).publicUrl,
            provider.config.maxTokens, provider.config.multipleApiKeys, if (provider.config.modelIds == null) "DISCOVERY" else "EXPLICIT")
        if (!db.updateStorageRow("model_providers", entity.id, "name" to entity.name, "type" to entity.type,
                "baseUrl" to entity.baseUrl, "icon" to entity.icon, "sortOrder" to entity.sortOrder, "groupId" to entity.groupId,
                "authId" to entity.authId, "supportStream" to entity.supportStream, "responsesApi" to entity.responsesApi,
                "customListModelsUrl" to entity.customListModelsUrl, "maxTokens" to entity.maxTokens,
                "multipleApiKeys" to entity.multipleApiKeys, "modelSelectionMode" to entity.modelSelectionMode)) {
            dao.insertOrUpdate(entity)
        }
        // Detach the reference before deleting the old auth row (SET_NULL must not mask errors).
        previous?.authId?.takeIf { it != authId }?.let { db.deleteUnreferencedEndpointAuth("provider_auth", "model_providers", it) }
        if (!preserve) {
            if (authId == null) structured.replaceDocument(StructuredJsonDocument("provider", provider.id, "auth_method", JsonPrimitive(provider.auth.method.name)))
            else deleteDocument("provider", provider.id, "auth_method")
        }
        suspend fun persistOptionalValue(field: String, value: String?) {
            val normalized = value?.trim()?.ifEmpty { null }
            if (normalized == null) deleteDocument("provider", provider.id, field)
            else structured.replaceDocument(StructuredJsonDocument("provider", provider.id, field, JsonPrimitive(normalized)))
        }
        persistOptionalValue("codex_client_version", provider.config.codexClientVersion)
        persistOptionalValue("antigravity_version", provider.config.antigravityVersion)
        persistOptionalValue("antigravity_changelist", provider.config.antigravityChangelist)
        val projectIds = provider.config.antigravityProjectIds.entries.mapNotNull { (id, projectId) ->
            projectId.trim().takeIf { it.isNotEmpty() && id.isNotBlank() }?.let { id to it }
        }.toMap()
        if (projectIds.isEmpty()) deleteDocument("provider", provider.id, "antigravity_project_ids")
        else structured.replaceDocument(StructuredJsonDocument("provider", provider.id, "antigravity_project_ids",
            JsonObject(projectIds.mapValues { JsonPrimitive(it.value) })))
        val removedModels = dao.getModelConfigurations(provider.id).map { it.modelId } - provider.config.modelConfigs.keys
        removedModels.forEach { deleteDocument("provider_model", "${provider.id}/$it", "model_json") }
        dao.deleteModelSelection(provider.id)
        dao.deleteModelValues(provider.id)
        dao.deleteModelConfigurations(provider.id)
        dao.insertModelSelection(provider.config.modelIds.orEmpty().mapIndexed { ordinal, id -> ProviderModelSelectionEntity(provider.id, ordinal, id) })
        provider.config.modelConfigs.entries.forEachIndexed { ordinal, (modelId, model) ->
            dao.insertModelConfigurations(listOf(ProviderModelConfigurationEntity(
                provider.id, modelId, ordinal, model.temperature, model.topP, model.topK, model.supportStream,
                model.displayName, model.contextWindowTokens, model.modelType.name, model.supportsVision, model.supportsVideoInput,
                model.supportsAudioInput, model.supportsThinking, model.reasoningEffort, model.thinkingLevel, model.supportsToolCalls,
                 model.legacySendThinkingContent, model.thinkingPassback?.name, model.icon,
                 model.mediaApiType.name, model.mediaRequestProfileId
            )))
            dao.insertModelValues(listOf("reasoning_efforts" to model.reasoningEfforts, "model_tools" to model.modelTools,
                "enabled_model_tools" to model.enabledModelTools).flatMap { (kind, values) ->
                values.mapIndexed { index, value -> ProviderModelValueEntity(provider.id, modelId, kind, index, value) }
            })
            if (model.modelJson.isNotEmpty()) structured.replaceDocument(StructuredJsonDocument("provider_model", "${provider.id}/$modelId", "model_json", model.modelJson))
            else deleteDocument("provider_model", "${provider.id}/$modelId", "model_json")
        }
    }

    suspend fun deleteDocuments(providerId: String) {
        dao.getModelConfigurations(providerId).forEach { deleteDocument("provider_model", "$providerId/${it.modelId}", "model_json") }
        deleteDocument("provider", providerId, "auth_method")
        deleteDocument("provider", providerId, "codex_client_version")
        deleteDocument("provider", providerId, "antigravity_version")
        deleteDocument("provider", providerId, "antigravity_changelist")
        deleteDocument("provider", providerId, "antigravity_project_ids")
        deleteDocument("provider_catalog", providerId, "cache_state")
    }

    private suspend fun deleteDocument(type: String, id: String, field: String) {
        structured.getDocument(type, id, field)?.let { structured.deleteDocument(it.id) }
    }
}
