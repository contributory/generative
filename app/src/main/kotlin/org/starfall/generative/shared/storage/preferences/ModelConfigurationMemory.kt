package org.starfall.generative.shared.storage.preferences

import androidx.core.content.edit
import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import org.starfall.generative.shared.model.language.DiscoveredModel
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.normalizedForTemplate

internal fun rememberedModelName(modelId: String): String =
    modelId.trim().substringAfterLast('/').trim().lowercase(Locale.ROOT)

/** Portable model behavior, independent of provider prefixes and entity-specific icons. */
class ModelConfigurationMemory(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("model-configuration-memory", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(modelId: String): ModelConfiguration? = preferences.getString(rememberedModelName(modelId), null)?.let {
        runCatching { json.decodeFromString<ModelConfiguration>(it) }.getOrNull()?.copy(modelJson = JsonObject(emptyMap()))?.normalizedForTemplate()
    }

    fun remember(modelId: String, configuration: ModelConfiguration, onlyIfMissing: Boolean = false) {
        val key = rememberedModelName(modelId)
        if (key.isBlank() || (onlyIfMissing && preferences.contains(key))) return
        preferences.edit().putString(key, json.encodeToString(configuration.copy(displayName = "", icon = null, modelJson = JsonObject(emptyMap())).normalizedForTemplate())).apply()
    }

    fun configurationFor(model: DiscoveredModel): ModelConfiguration {
        val saved = get(model.id) ?: return model.configuration()
        return saved.copy(displayName = model.displayName,
            contextWindowTokens = model.contextWindowTokens ?: saved.contextWindowTokens,
            modelJson = model.metadata)
    }

    internal fun exportAll(): Map<String, JsonObject> = preferences.all.mapNotNull { (key, value) ->
        val raw = value as? String ?: return@mapNotNull null
        runCatching { key to json.encodeToJsonElement<ModelConfiguration>(json.decodeFromString(raw)).jsonObject }.getOrNull()
    }.toMap()

    internal fun importAll(values: Map<String, JsonObject>) {
        preferences.edit(commit = true) {
            clear()
            values.forEach { (key, value) ->
                val config = json.decodeFromJsonElement<ModelConfiguration>(value)
                putString(key, json.encodeToString(config))
            }
        }
    }
}
