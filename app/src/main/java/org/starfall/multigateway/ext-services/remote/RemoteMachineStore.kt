package org.starfall.multigateway.extservices.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Independent persistence for the Remote Machines extension.
 *
 * Keeping this store separate means adding/removing the feature never requires a Room migration.
 * The complete JSON payload is encrypted because it contains SSH passwords, key passphrases and
 * OAuth refresh tokens.
 */
class RemoteMachineStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<RemoteMachineState> = _state.asStateFlow()

    suspend fun update(transform: (RemoteMachineState) -> RemoteMachineState) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val updated = transform(_state.value)
                writeState(updated)
                _state.value = updated
            }
        }
    }

    suspend fun clear() {
        update { RemoteMachineState() }
    }

    private fun readState(): RemoteMachineState {
        val stored = preferences.getString(KEY, null) ?: return RemoteMachineState()
        return runCatching {
            val payload = if (stored.startsWith(PREFIX)) decrypt(stored) else stored
            json.decodeFromString<RemoteMachineState>(payload)
        }.getOrDefault(RemoteMachineState())
    }

    private fun writeState(state: RemoteMachineState) {
        val payload = encrypt(json.encodeToString(state))
        preferences.edit(commit = true) { putString(KEY, payload) }
    }

    private fun key(create: Boolean): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return@synchronized it }
        check(create) { "Remote Machines encryption key is unavailable." }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(bytes)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        require(bytes.size > GCM_IV_LENGTH) { "Invalid Remote Machines payload." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(false),
            GCMParameterSpec(128, bytes.copyOfRange(0, GCM_IV_LENGTH))
        )
        return String(cipher.doFinal(bytes, GCM_IV_LENGTH, bytes.size - GCM_IV_LENGTH), Charsets.UTF_8)
    }

    private companion object {
        const val PREFERENCES = "remote_machines"
        const val KEY = "state"
        const val KEY_ALIAS = "multigateway.remote-machines.v1"
        const val PREFIX = "keystore:v1:"
        const val GCM_IV_LENGTH = 12
        val KEY_LOCK = Any()
    }
}
