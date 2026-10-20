package org.starfall.generative.integrations.remote

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.identity.Identity
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.starfall.generative.shared.model.GCloudOAuthClient
import org.starfall.generative.shared.model.REMOTE_TOOL_DISCONNECT
import org.starfall.generative.shared.model.REMOTE_TOOL_EXECUTE
import org.starfall.generative.shared.model.REMOTE_TOOL_START_MACHINE
import org.starfall.generative.shared.model.REMOTE_TOOL_UPDATE_IP
import org.starfall.generative.shared.model.RemoteAuthentication
import org.starfall.generative.shared.model.RemoteBinary
import org.starfall.generative.shared.model.RemoteBinaryTestResult
import org.starfall.generative.shared.model.RemoteIdentityKey
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.RemoteMachineType



/**
 * SSH and Cloud Shell operations for the extension. No UI code is involved here, which keeps the
 * feature usable by both the terminal screen and the model tool runtime.
 */
class RemoteMachineService(
    private val context: Context,
    val store: RemoteMachineStore = RemoteMachineStore(context),
    /** Shared executable storage used by Remote Machines settings and command expansion. */
    private val executableBinaries: ExecutableBinaryStore = ExecutableBinaryStore(context)
) {
    private val appContext = context.applicationContext
    private val assets = RemoteMachineAssetsService(context, store, executableBinaries)
    private val connectionManager = RemoteMachineConnectionManager { machine, password, prompt ->
        openSession(machine, password, prompt)
    }
    private val cloudShell = RemoteMachineCloudShellService(context, store, assets)

    val state = store.state
    val binaries: StateFlow<List<ExecutableBinary>> = executableBinaries.binaries

    /** Binaries uploaded in the previous Remote Machines screen, before migration to Settings. */
    fun legacyBinaries(): List<RemoteBinary> = state.value.binaries

    suspend fun clearLegacyBinaries() {
        store.update { it.copy(binaries = emptyList()) }
    }


    /**
     * Machines that currently hold a persistent SSH connection (opened by the connect tool or by
     * execute). A terminal's private connection is not listed.
     */
    val connectedMachineIds: StateFlow<Set<String>> get() = connectionManager.connectedMachineIds

    suspend fun saveMachine(machine: RemoteMachine) {
        val username = machine.username.trim()
        val normalizedPort = if (machine.type == RemoteMachineType.GCLOUD_SHELL) 6_000 else machine.port.coerceIn(1, 65535)
        val passwordReference = if (machine.type == RemoteMachineType.GCLOUD_SHELL) {
            machine.host.trim().takeIf(String::isNotBlank)?.let {
                remotePasswordReference(username.ifBlank { machine.gcloudUsername }, it, normalizedPort)
            } ?: machine.passwordReference
        } else {
            remotePasswordReference(username, machine.host.trim(), normalizedPort)
        }
        val normalized = machine.copy(
            name = machine.name.trim(),
            host = if (machine.type == RemoteMachineType.GCLOUD_SHELL) "" else machine.host.trim(),
            username = username,
            port = normalizedPort,
            passwordReference = passwordReference,
            connectTimeoutSeconds = machine.connectTimeoutSeconds.coerceIn(1, 300),
            keepAliveSeconds = machine.keepAliveSeconds.coerceIn(0, 3_600),
            preferredAuthentications = if (machine.authentication == RemoteAuthentication.PASSWORD) {
                if (machine.keyboardInteractive) "keyboard-interactive,password" else "password"
            } else "publickey"
        )
        require(normalized.name.isNotBlank()) { "Machine name is required." }
        store.update { current ->
            val order = if (current.machines.any { it.id == normalized.id }) normalized.sortOrder
            else current.machines.maxOfOrNull { it.sortOrder }.orZero() + 1
            val saved = normalized.copy(sortOrder = order)
            val previousName = current.machines.firstOrNull { it.id == normalized.id }?.name
            current.copy(
                machines = current.machines
                    .filterNot { it.id == normalized.id }
                    .plus(saved)
                    .sortedWith(compareBy<RemoteMachine> { it.sortOrder }.thenBy { it.name.lowercase() }),
                sshConfig = SshConfigParser.upsertMachine(current.sshConfig, saved, previousName)
            )
        }
        // The next connection must use the saved settings.
        disconnectMachineNow(normalized.id)
    }

    suspend fun deleteMachine(id: String) {
        disconnectMachineNow(id)
        cloudShell.invalidateCache(id)
        store.update { current ->
            val deleted = current.machines.firstOrNull { it.id == id }
            current.copy(
                machines = current.machines.filterNot { it.id == id },
                sshConfig = deleted?.let { SshConfigParser.removeMachine(current.sshConfig, it) } ?: current.sshConfig
            )
        }
        runCatching { File(appContext.filesDir, "remote-machines/keys/$id").delete() }
        runCatching {
            appContext.getSharedPreferences(REMOTE_TERMINAL_UI_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(remoteTerminalInputModePreferenceKey(id))
                .apply()
        }
    }

    suspend fun reorderMachines(ids: List<String>) {
        store.update { current ->
            val positions = ids.withIndex().associate { it.value to it.index }
            current.copy(machines = current.machines
                .map { machine -> machine.copy(sortOrder = positions[machine.id] ?: machine.sortOrder) }
                .sortedWith(compareBy<RemoteMachine> { it.sortOrder }.thenBy { it.name.lowercase() }))
        }
    }

    suspend fun saveSshConfig(text: String) {
        store.update {
            val synced = SshConfigParser.syncMachines(it.machines, text)
            val config = synced.filter { machine -> machine.type == RemoteMachineType.GCLOUD_SHELL }
                .fold(text) { currentConfig, machine -> SshConfigParser.upsertMachine(currentConfig, machine) }
            it.copy(
                sshConfig = config,
                machines = SshConfigParser.syncMachines(synced, config)
            )
        }
    }

    suspend fun saveOAuthClient(client: GCloudOAuthClient) {
        require(client.name.trim().isNotBlank()) { "OAuth client name is required." }
        require(client.clientId.trim().isNotBlank()) { "OAuth client ID is required." }
        store.update { current ->
            current.copy(oauthClients = current.oauthClients.filterNot { it.id == client.id } + client.copy(
                name = client.name.trim(), clientId = client.clientId.trim()
            ))
        }
    }

    suspend fun deleteOAuthClient(id: String) {
        store.update { current ->
            current.copy(
                oauthClients = current.oauthClients.filterNot { it.id == id },
                oauthTokens = current.oauthTokens - id,
                machines = current.machines.map { machine ->
                    if (machine.oauthClientId == id) machine.copy(oauthClientId = "") else machine
                }
            )
        }
    }

    suspend fun importBinary(uri: Uri): ExecutableBinary = assets.importBinary(uri)

    suspend fun testBinary(id: String, arguments: List<String> = emptyList()): RemoteBinaryTestResult =
        assets.testBinary(id, arguments)

    suspend fun parseOAuthClientJson(uri: Uri): GCloudOAuthClient = assets.parseOAuthClientJson(uri)

    suspend fun deleteBinary(id: String) = assets.deleteBinary(id)

    suspend fun importIdentityKey(uri: Uri): RemoteIdentityKey = assets.importIdentityKey(uri)

    suspend fun importPublicKey(keyId: String, uri: Uri): RemoteIdentityKey = assets.importPublicKey(keyId, uri)

    suspend fun deleteIdentityKey(id: String) = assets.deleteIdentityKey(id)

    suspend fun authorizeGCloud(client: GCloudOAuthClient): Result<Unit> = cloudShell.authorizeGCloud(client)

    suspend fun storeAndroidOAuthToken(client: GCloudOAuthClient, accessToken: String) =
        cloudShell.storeAndroidOAuthToken(client, accessToken)

    suspend fun refreshGCloudIp(machine: RemoteMachine, forceRefresh: Boolean = true): Result<RemoteMachine> =
        cloudShell.refreshGCloudIp(machine, forceRefresh)

    suspend fun fetchGCloudUsername(machine: RemoteMachine): Result<String> = cloudShell.fetchGCloudUsername(machine)

    suspend fun startGCloudMachine(machine: RemoteMachine): Result<RemoteMachine> = cloudShell.startGCloudMachine(machine)

    suspend fun execute(machine: RemoteMachine, command: String, timeoutSeconds: Int = 120): RemoteCommandResult {
        require(command.isNotBlank()) { "The command is empty." }
        return connectionManager.execute(machine, command, timeoutSeconds)
    }

    suspend fun executeTool(machine: RemoteMachine, toolName: String, arguments: JsonObject): JsonObject {
        require(machine.isToolEnabled(toolName)) { "Remote Machine tool is disabled." }
        return when (toolName) {
            REMOTE_TOOL_UPDATE_IP -> refreshGCloudIp(machine, forceRefresh = true).fold(
                onSuccess = { updated ->
                    buildJsonObject {
                        put("status", JsonPrimitive("updated"))
                        put("host", updated.host)
                        put("port", updated.port)
                    }
                },
                onFailure = { throw it }
            )
            REMOTE_TOOL_START_MACHINE -> startGCloudMachine(machine).fold(
                onSuccess = { updated ->
                    buildJsonObject {
                        put("status", JsonPrimitive("started"))
                        put("host", updated.host)
                        put("port", updated.port)
                    }
                },
                onFailure = { throw it }
            )
            REMOTE_TOOL_DISCONNECT -> buildJsonObject {
                put("status", JsonPrimitive(if (disconnectMachineNow(machine.id)) "disconnected" else "not_connected"))
            }
            REMOTE_TOOL_EXECUTE -> {
                val command = arguments["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val timeout = arguments["timeout_seconds"]?.jsonPrimitive?.longOrNull
                    ?.toInt()?.coerceIn(1, 600) ?: 120
                val result = execute(machine, command, timeout)
                buildJsonObject {
                    put("output", result.output)
                    put("stderr", result.error)
                    put("exit_code", result.exitCode)
                    put("isError", result.isError)
                }
            }
            else -> error("Unknown Remote Machine tool: $toolName")
        }
    }

    suspend fun openTerminal(
        machine: RemoteMachine,
        passwordOverride: String? = null,
        askSecret: RemoteSecretPrompt? = null
    ): RemoteTerminal = connectionManager.openTerminal(machine, passwordOverride, askSecret)

    fun isConnected(machineId: String): Boolean = connectionManager.isConnected(machineId)
    fun disconnectMachine(machineId: String) = connectionManager.disconnectMachine(machineId)
    fun disconnectAllWhenIdle() = connectionManager.disconnectAllWhenIdle()
    suspend fun disconnectMachineNow(machineId: String): Boolean = connectionManager.disconnectMachineNow(machineId)

    private suspend fun openSession(
        machine: RemoteMachine,
        passwordOverride: String? = null,
        askSecret: RemoteSecretPrompt? = null
    ): Pair<Session, Process?> {
        var actual = if (machine.type == RemoteMachineType.GCLOUD_SHELL) {
            refreshGCloudIp(machine, forceRefresh = false).getOrThrow()
        } else machine
        val session = try {
            connectCancellable(actual, passwordOverride, askSecret)
        } catch (error: Exception) {
            if (error is CancellationException || actual.type != RemoteMachineType.GCLOUD_SHELL) throw error
            actual = refreshGCloudIp(actual).getOrThrow()
            connectCancellable(actual, passwordOverride, askSecret)
        }
        val resolved = actual
        val local = withContext(Dispatchers.IO) { startLocalCommand(resolved) }
        return session to local
    }

    /**
     * JSch's connect call blocks its thread and ignores coroutine cancellation, so it runs in a child
     * coroutine and the half-open session is closed when cancelled (the Stop button).
     */
    private suspend fun connectCancellable(
        machine: RemoteMachine,
        passwordOverride: String?,
        askSecret: RemoteSecretPrompt? = null
    ): Session =
        withContext(Dispatchers.IO) {
            val pending = AtomicReference<Session?>()
            coroutineScope {
                val opening = async(Dispatchers.IO) {
                    connect(machine, passwordOverride, askSecret, onCreated = { pending.set(it) })
                }
                try {
                    opening.await()
                } catch (error: Throwable) {
                    runCatching { pending.get()?.disconnect() }
                    opening.cancel()
                    throw error
                }
            }
        }

    private fun connect(
        machine: RemoteMachine,
        passwordOverride: String? = null,
        askSecret: RemoteSecretPrompt? = null,
        onCreated: (Session) -> Unit = {}
    ): Session {
        require(machine.host.isNotBlank()) { "Remote host is missing." }
        require(machine.username.isNotBlank()) { "Remote user is missing." }
        AndroidEdDSA.install()
        val jsch = JSch()
        if (machine.identityFile.isNotBlank()) {
            val identity = assets.resolveIdentity(machine.identityFile)
                ?: error("Identity file not found: ${machine.identityFile.substringAfterLast('/')}. Upload it in Remote Machines settings or fix IdentityFile.")
            if (machine.identityPassphrase.isBlank()) jsch.addIdentity(identity.absolutePath)
            else jsch.addIdentity(identity.absolutePath, machine.identityPassphrase)
        }
        val session = jsch.getSession(machine.username, machine.host, machine.port)
        val passwordMode = machine.authentication == RemoteAuthentication.PASSWORD
        val password = if (passwordMode) passwordOverride ?: machine.password else ""
        val useKeyboardInteractive = passwordMode && (machine.keyboardInteractive || password.isEmpty())
        val plainSavedPassword = passwordMode && !machine.keyboardInteractive && password.isNotEmpty()
        // UserInfo also handles an encrypted key's passphrase when public-key auth is selected.
        if (!plainSavedPassword) {
            session.setUserInfo(PasswordKeyboardInteractiveUserInfo(machine.username, password, askSecret))
        }
        if (password.isNotEmpty()) session.setPassword(password)
        session.setConfig("StrictHostKeyChecking", if (machine.strictHostKeyChecking) "yes" else "no")
        session.setConfig(
            "PreferredAuthentications",
            machine.preferredAuthentications.ifBlank {
                if (useKeyboardInteractive) {
                // Try PAM/keyboard-interactive first. JSch's password implementation retries the
                // same method internally after a failure, which can exhaust servers that expose
                // password login only as keyboard-interactive before KBI is reached.
                    "keyboard-interactive,password"
                } else if (machine.authentication == RemoteAuthentication.PASSWORD) {
                    "password"
                } else {
                    "publickey"
                }
            }
        )
        if (machine.compression) session.setConfig("compression.s2c", "zlib,none")
        if (machine.compression) session.setConfig("compression.c2s", "zlib,none")
        if (machine.keepAliveSeconds > 0) session.setServerAliveInterval(machine.keepAliveSeconds * 1_000)
        session.timeout = machine.connectTimeoutSeconds * 1_000
        machine.proxyCommand.trim().takeIf(String::isNotBlank)?.let { command ->
            session.setProxy(CommandProxy(assets.resolveBinary(command)))
        }
        onCreated(session)
        try {
            session.connect(machine.connectTimeoutSeconds * 1_000)
        } catch (failure: JSchException) {
            if (askSecret == null && password.isEmpty() && machine.identityFile.isBlank() &&
                failure.isAuthenticationFailure()
            ) {
                throw IllegalStateException(
                    "The server requires authentication, but this machine has no saved password or private key. Add one in the machine settings.",
                    failure
                )
            }
            throw failure
        }
        return session
    }

    private fun JSchException.isAuthenticationFailure(): Boolean {
        val text = message.orEmpty().lowercase()
        return "auth fail" in text || "auth cancel" in text || "userauth fail" in text
    }

    private fun startLocalCommand(machine: RemoteMachine): Process? {
        val command = machine.localCommand.trim().takeIf(String::isNotBlank) ?: return null
        return runCatching {
            ProcessBuilder("sh", "-c", assets.resolveBinary(command.replace("%h", machine.host).replace("%p", machine.port.toString())))
                .redirectErrorStream(true)
                .start()
                .also { process -> discardProcessStream(process.inputStream, "remote-local-command-output") }
        }.getOrNull()
    }

    private fun Int?.orZero(): Int = this ?: 0

    companion object {
        const val BINARY_TEST_TIMEOUT_SECONDS = 10L
        const val MAX_BINARY_TEST_OUTPUT_BYTES = 65_536
        const val CLOUD_ENDPOINT_CACHE_TTL_MILLIS = 15 * 60 * 1_000L
        /** A persistent SSH connection with no command or terminal using it is closed after this. */
        const val IDLE_DISCONNECT_MILLIS = 5 * 60 * 1_000L
        const val CONNECTION_POLL_MILLIS = 5_000L
        const val GOOGLE_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val GOOGLE_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
        const val ANDROID_ACCESS_TOKEN_LIFETIME_MS = 3_300_000L
        val CLOUD_SHELL_SUPPORTED_KEY_ALGORITHMS = setOf(
            "ssh-rsa",
            "ecdsa-sha2-nistp256",
            "ecdsa-sha2-nistp384",
            "ecdsa-sha2-nistp521"
        )
    }
}

