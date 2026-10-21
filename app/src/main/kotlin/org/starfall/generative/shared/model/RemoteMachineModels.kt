package org.starfall.generative.shared.model

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

/** Settings for a WebSocket endpoint that tunnels the SSH byte stream. */
@Serializable
data class WebSocketProxyConfig(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val connectTimeoutSeconds: Int = 15,
    val skipCertificateVerification: Boolean = false,
    /** Optional PEM-encoded CA certificate used in addition to the platform trust store. */
    val caCertificatePem: String = "",
    val handshakeTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 0,
    val writeTimeoutSeconds: Int = 0,
    val closeTimeoutSeconds: Int = 5,
    val subProtocols: List<String> = emptyList()
) {
    fun normalized(): WebSocketProxyConfig {
        val cleanUrl = url.trim()
        require(cleanUrl.isBlank() || cleanUrl.startsWith("ws://") || cleanUrl.startsWith("wss://")) {
            "WebSocket proxy URL must start with ws:// or wss://."
        }
        return copy(
            url = cleanUrl,
            headers = headers.mapKeys { it.key.trim() }.filterKeys(String::isNotBlank)
                .mapValues { it.value },
            connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(1, 300),
            handshakeTimeoutSeconds = handshakeTimeoutSeconds.coerceIn(1, 300),
            readTimeoutSeconds = readTimeoutSeconds.coerceIn(0, 3_600),
            writeTimeoutSeconds = writeTimeoutSeconds.coerceIn(0, 3_600),
            closeTimeoutSeconds = closeTimeoutSeconds.coerceIn(1, 300),
            subProtocols = subProtocols.map(String::trim).filter(String::isNotBlank).distinct()
        )
    }

    val isConfigured: Boolean get() = url.isNotBlank()
}

/** Runtime machine view. SSH settings are persisted directly in the encrypted Remote Machines store. */
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
    /** Additional JSch session options. Empty by default and only shown after the user adds one. */
    val sshOptions: Map<String, String> = emptyMap(),
    val websocketProxy: WebSocketProxyConfig? = null,
    /** ID of another Remote Machine used as an SSH direct-tcpip jump host. */
    val proxyMachineId: String = "",
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

/** Tools offered for a machine type. Terminal tools never create a connection implicitly. */
fun remoteMachineToolNames(type: RemoteMachineType): List<String> = when (type) {
    RemoteMachineType.SSH -> listOf(
        REMOTE_TOOL_OPEN_TERMINAL,
        REMOTE_TOOL_CLOSE_TERMINAL,
        REMOTE_TOOL_LIST_TERMINAL,
        REMOTE_TOOL_EXECUTE,
        REMOTE_TOOL_DISCONNECT
    )
    RemoteMachineType.GCLOUD_SHELL ->
        listOf(
            REMOTE_TOOL_UPDATE_IP,
            REMOTE_TOOL_OPEN_TERMINAL,
            REMOTE_TOOL_CLOSE_TERMINAL,
            REMOTE_TOOL_LIST_TERMINAL,
            REMOTE_TOOL_EXECUTE,
            REMOTE_TOOL_DISCONNECT
        )
}

const val REMOTE_TOOL_OPEN_TERMINAL = "open_terminal"
const val REMOTE_TOOL_CLOSE_TERMINAL = "close_terminal"
const val REMOTE_TOOL_LIST_TERMINAL = "list_terminal"
const val REMOTE_TOOL_EXECUTE = "execute"
const val REMOTE_TOOL_DISCONNECT = "disconnect"
const val REMOTE_TOOL_START_MACHINE = "start_machine"
const val REMOTE_TOOL_UPDATE_IP = "update_ip"
