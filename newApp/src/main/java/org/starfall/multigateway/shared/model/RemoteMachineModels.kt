package org.starfall.multigateway.shared.model

import kotlinx.serialization.Serializable

/** The connection types currently supported by Remote Machines. */
@Serializable
enum class RemoteMachineType {
    SSH,
    GCLOUD_SHELL
}

@Serializable
enum class RemoteAuthentication {
    PASSWORD,
    PRIVATE_KEY
}

/** Runtime machine view. SSH options are hydrated from the app's OpenSSH config file; the encrypted
 * store keeps only its stable ID, name, address, user and secret. Cloud Shell keeps its own fields.
 */
@Serializable
data class RemoteMachine(
    val id: String,
    val name: String,
    val type: RemoteMachineType = RemoteMachineType.SSH,
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    /** Exact credential lookup key; never a wildcard. Cloud Shell may refresh it with its endpoint. */
    val passwordReference: String = "",
    val authentication: RemoteAuthentication = RemoteAuthentication.PRIVATE_KEY,
    val password: String = "",
    /** Use SSH keyboard-interactive/PAM prompts instead of only the plain password method. */
    val keyboardInteractive: Boolean = true,
    val identityFile: String = "",
    val identityPassphrase: String = "",
    /** Effective SSH config value. Empty means use the app's auth-mode default. */
    val preferredAuthentications: String = "",
    val strictHostKeyChecking: Boolean = false,
    val proxyCommand: String = "",
    val localCommand: String = "",
    val connectTimeoutSeconds: Int = 15,
    val keepAliveSeconds: Int = 30,
    val compression: Boolean = false,
    val oauthClientId: String = "",
    val environmentName: String = "default",
    val gcloudUsername: String = "",
    val aiToolsEnabled: Boolean = true,
    /** Per-tool switches configured on the machine. Missing entries are enabled for compatibility. */
    val aiTools: Map<String, Boolean> = emptyMap(),
    val sortOrder: Int = Int.MAX_VALUE
) {
    val displayHost: String
        get() = host.ifBlank { if (type == RemoteMachineType.GCLOUD_SHELL) "Cloud Shell IP not loaded" else "Host not configured" }

    fun supportedToolNames(): List<String> = remoteMachineToolNames(type)

    fun isToolEnabled(toolName: String): Boolean = aiToolsEnabled &&
        (aiTools[toolName] ?: if (toolName == REMOTE_TOOL_UPDATE_IP) aiTools[REMOTE_TOOL_START_MACHINE] else null) != false
}

@Serializable
enum class GoogleOAuthClientType { DESKTOP, ANDROID }

@Serializable
data class GCloudOAuthClient(
    val id: String,
    val name: String,
    val clientId: String,
    val clientSecret: String = "",
    val type: GoogleOAuthClientType = GoogleOAuthClientType.DESKTOP
)

@Serializable
data class RemoteOAuthToken(
    val accessToken: String,
    val refreshToken: String = "",
    val tokenType: String = "Bearer",
    val expiresAt: Long? = null
)

/** Legacy record: executables are now managed in Settings; kept only to migrate old saved state. */
@Serializable
data class RemoteBinary(
    val id: String,
    val name: String,
    val path: String
)

data class RemoteBinaryTestResult(
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val outputTruncated: Boolean
)

/**
 * An uploaded SSH private key kept once in app storage. Machines never embed key material; they
 * only point at a key through their `IdentityFile` (the key's [path] or its [name]).
 */
@Serializable
data class RemoteIdentityKey(
    val id: String,
    val name: String,
    val path: String,
    /** Uploaded OpenSSH public key (.pub) belonging to this private key; empty when none. */
    val publicKeyPath: String = ""
)

/** Everything belonging to the extension is persisted outside of the application's Room schema. */
@Serializable
data class RemoteMachineState(
    val machines: List<RemoteMachine> = emptyList(),
    val sshConfig: String = "",
    val oauthClients: List<GCloudOAuthClient> = emptyList(),
    val oauthTokens: Map<String, RemoteOAuthToken> = emptyMap(),
    /** Legacy, see [RemoteBinary]. Always empty after the one-time migration. */
    val binaries: List<RemoteBinary> = emptyList(),
    val identities: List<RemoteIdentityKey> = emptyList()
)

/** Tools offered for a machine type. Disconnect and execute exist for every type; execute connects on demand. */
fun remoteMachineToolNames(type: RemoteMachineType): List<String> = when (type) {
    RemoteMachineType.SSH -> listOf(REMOTE_TOOL_DISCONNECT, REMOTE_TOOL_EXECUTE)
    RemoteMachineType.GCLOUD_SHELL ->
        listOf(REMOTE_TOOL_UPDATE_IP, REMOTE_TOOL_DISCONNECT, REMOTE_TOOL_EXECUTE)
}

const val REMOTE_TOOL_EXECUTE = "execute"
const val REMOTE_TOOL_DISCONNECT = "disconnect"
const val REMOTE_TOOL_START_MACHINE = "start_machine"
const val REMOTE_TOOL_UPDATE_IP = "update_ip"
