package org.starfall.generative.shared.model.remote

/** A secret requested by the SSH server while connecting. */
data class RemoteSecretRequest(val passphrase: Boolean, val prompt: String)

/** Called only when SSH requests a password or key passphrase that is not already saved. */
typealias RemoteSecretPrompt = suspend (RemoteSecretRequest) -> String?
