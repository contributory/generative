package org.starfall.generative.integrations.modelapis.ollama

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private val Context.ollamaCatalogDataStore by preferencesDataStore(
    name = "ollama_model_catalog",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "ollama-model-catalog")) }
)

/** Last successful discovery is usable immediately, even when an Ollama host is offline. */
internal class OllamaModelCache internal constructor(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context, now: () -> Long = System::currentTimeMillis) :
        this(context.applicationContext.ollamaCatalogDataStore, now)
    @Serializable data class Entry(val models: List<String>, val fetchedAt: Long)

    private fun key(baseUrl: String): String = MessageDigest.getInstance("SHA-256")
        .digest(baseUrl.trim().trimEnd('/').toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun read(baseUrl: String): Entry? = withContext(Dispatchers.IO) {
        try {
            store.data.first()[stringPreferencesKey(key(baseUrl))]?.let { Json.decodeFromString<Entry>(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun isFresh(entry: Entry): Boolean = now() - entry.fetchedAt in 0 until 5 * 60_000L

    suspend fun write(baseUrl: String, models: List<String>) = withContext(Dispatchers.IO) {
        store.edit { it[stringPreferencesKey(key(baseUrl))] = Json.encodeToString(Entry(models.distinct(), now())) }
    }
}
