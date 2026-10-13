package org.starfall.multigateway.extservices.remote

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

/**
 * A deliberately self-contained machine record. Secrets are stored together with this record in
 * [RemoteMachineStore], whose payload is encrypted with an Android Keystore key.
 */
@Serializable
data class RemoteMachine(
    val id: String,
    val name: String,
    val type: RemoteMachineType = RemoteMachineType.SSH,
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authentication: RemoteAuthentication = RemoteAuthentication.PRIVATE_KEY,
    val password: String = "",
    /** Use SSH keyboard-interactive/PAM prompts instead of only the plain password method. */
    val keyboardInteractive: Boolean = true,
    val identityFile: String = "",
    val identityPassphrase: String = "",
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

    fun supportedToolNames(): List<String> = when (type) {
        RemoteMachineType.SSH -> listOf(REMOTE_TOOL_EXECUTE)
        RemoteMachineType.GCLOUD_SHELL -> listOf(REMOTE_TOOL_START_MACHINE, REMOTE_TOOL_EXECUTE)
    }

    fun isToolEnabled(toolName: String): Boolean = aiToolsEnabled && aiTools[toolName] != false
}

@Serializable
data class GCloudOAuthClient(
    val id: String,
    val name: String,
    val clientId: String,
    val clientSecret: String = ""
)

@Serializable
data class RemoteOAuthToken(
    val accessToken: String,
    val refreshToken: String = "",
    val tokenType: String = "Bearer",
    val expiresAt: Long? = null
)

@Serializable
data class RemoteBinary(
    val id: String,
    val name: String,
    val path: String
)

/** Everything belonging to the extension is persisted outside of the application's Room schema. */
@Serializable
data class RemoteMachineState(
    val machines: List<RemoteMachine> = emptyList(),
    val sshConfig: String = "",
    val oauthClients: List<GCloudOAuthClient> = emptyList(),
    val oauthTokens: Map<String, RemoteOAuthToken> = emptyMap(),
    val binaries: List<RemoteBinary> = emptyList()
)

const val REMOTE_TOOL_EXECUTE = "execute"
const val REMOTE_TOOL_START_MACHINE = "start_machine"
