package org.starfall.generative.integrations.security

import android.content.Context

/**
 * Opaque, already-encrypted OAuth records. Integrations never access Room directly.
 * The application provides the database implementation at its composition root.
 */
interface OAuthRecordStorage {
    fun get(namespace: String, id: String): String?
    fun all(namespace: String): Map<String, String>
    fun put(namespace: String, id: String, encrypted: String)
    fun remove(namespace: String, id: String)
    fun replace(namespace: String, encrypted: Map<String, String>)
}

interface OAuthRecordStorageOwner {
    val oauthRecordStorage: OAuthRecordStorage
}

internal fun oauthRecordStorage(context: Context): OAuthRecordStorage =
    (context.applicationContext as? OAuthRecordStorageOwner)?.oauthRecordStorage
        ?: error("OAuth credential storage must be provided by the application.")
