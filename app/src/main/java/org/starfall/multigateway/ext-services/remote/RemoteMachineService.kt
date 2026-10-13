package org.starfall.multigateway.extservices.remote

import android.content.Context
import android.net.Uri
import android.system.Os
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.starfall.multigateway.data.adapter.common.AppAuthTransactions
import org.starfall.multigateway.data.adapter.common.OAuthCallbackService
import org.starfall.multigateway.data.adapter.common.OAuthBrowser
import org.starfall.multigateway.data.adapter.common.awaitLoopbackAuthorizationCode
import org.starfall.multigateway.data.tools.ToolHttp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URLEncoder
import java.util.Properties
import java.util.UUID
import kotlin.math.roundToInt

/** Result returned by an SSH command execution. */
data class RemoteCommandResult(
    val output: String,
    val error: String = "",
    val exitCode: Int = 0
) {
    val isError: Boolean get() = exitCode != 0
}

/** A connected interactive shell. The owner must close it when the terminal screen is disposed. */
class RemoteTerminal internal constructor(
    private val session: Session,
    private val channel: ChannelShell,
    val input: InputStream,
    val output: OutputStream,
    private val localCommand: Process?
) : AutoCloseable {
    /** Exit status of the remote shell, or -1 while it is running / if the connection was dropped. */
    val exitStatus: Int get() = channel.exitStatus

    /** Tells the server the terminal size changed (blocking network I/O; call off the main thread). */
    fun resize(columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0 || !channel.isConnected) return
        runCatching { channel.setPtySize(columns, rows, 0, 0) }
    }

    /**
     * Disconnects the channel and SSH session. JSch writes to the socket when disconnecting, which
     * throws NetworkOnMainThreadException on the UI thread (previously swallowed, leaving the
     * session open), so the work always runs on a background thread. Safe to call repeatedly.
     */
    override fun close() {
        Thread {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
            runCatching { localCommand?.destroy() }
        }.apply { isDaemon = true; name = "remote-terminal-disconnect" }.start()
    }
}

/**
 * SSH and Cloud Shell operations for the extension. No UI code is involved here, which keeps the
 * feature usable by both the terminal screen and the model tool runtime.
 */
class RemoteMachineService(
    private val context: Context,
    val store: RemoteMachineStore = RemoteMachineStore(context)
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val authHttp = ToolHttp()

    val state = store.state

    suspend fun saveMachine(machine: RemoteMachine) {
        val normalized = machine.copy(
            name = machine.name.trim(),
            host = machine.host.trim(),
            username = machine.username.trim(),
            port = machine.port.coerceIn(1, 65535),
            connectTimeoutSeconds = machine.connectTimeoutSeconds.coerceIn(1, 300),
            keepAliveSeconds = machine.keepAliveSeconds.coerceIn(0, 3_600)
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
    }

    suspend fun deleteMachine(id: String) {
        store.update { current ->
            val deleted = current.machines.firstOrNull { it.id == id }
            current.copy(
                machines = current.machines.filterNot { it.id == id },
                sshConfig = deleted?.let { SshConfigParser.removeMachine(current.sshConfig, it) } ?: current.sshConfig
            )
        }
        runCatching { File(appContext.filesDir, "remote-machines/keys/$id").delete() }
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
            it.copy(
                sshConfig = text,
                machines = SshConfigParser.syncMachines(it.machines, text)
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

    suspend fun importBinary(uri: Uri): RemoteBinary = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: "binary-${UUID.randomUUID()}"
        val safeName = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "binary-${UUID.randomUUID()}" }
        val directory = File(appContext.filesDir, "remote-machines/bin").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}-$safeName")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected binary.")
        target.setReadable(false, false)
        target.setWritable(true, true)
        target.setExecutable(true, false)
        runCatching { Os.chmod(target.absolutePath, 0b111_000_000) }
        val binary = RemoteBinary(UUID.randomUUID().toString(), displayName, target.absolutePath)
        store.update { it.copy(binaries = it.binaries.filterNot { existing -> existing.name == displayName } + binary) }
        binary
    }

    /** Imports a Google Desktop OAuth client file without persisting it until the user confirms it. */
    suspend fun parseOAuthClientJson(uri: Uri): GCloudOAuthClient = withContext(Dispatchers.IO) {
        val displayName = appContext.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Google Desktop Client"
        val raw = appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Could not read the OAuth client JSON file.")
        val root = json.parseToJsonElement(raw).jsonObject
        val installed = root["installed"]?.jsonObject
            ?: if (root["web"] != null) error("Web OAuth clients are not supported. Choose a Google Desktop app client JSON.")
            else error("The selected file is not a Google OAuth Desktop client JSON.")
        val clientId = installed["client_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(clientId.isNotBlank()) { "The selected OAuth client JSON has no installed.client_id." }
        GCloudOAuthClient(
            id = UUID.randomUUID().toString(),
            name = displayName,
            clientId = clientId,
            clientSecret = installed["client_secret"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        )
    }

    suspend fun deleteBinary(id: String) {
        val binary = state.value.binaries.firstOrNull { it.id == id }
        store.update { it.copy(binaries = it.binaries.filterNot { existing -> existing.id == id }) }
        binary?.let { runCatching { File(it.path).delete() } }
    }

    /** Uploads an SSH private key once; machines reference it through IdentityFile. */
    suspend fun importIdentityKey(uri: Uri): RemoteIdentityKey = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "key-${UUID.randomUUID().toString().take(8)}"
        val id = UUID.randomUUID().toString()
        val directory = File(appContext.filesDir, "remote-machines/identities").apply { mkdirs() }
        val target = File(directory, id)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected identity file.")
        target.setReadable(false, false)
        target.setWritable(true, true)
        runCatching { Os.chmod(target.absolutePath, 0b110_000_000) }
        val key = RemoteIdentityKey(id, displayName, target.absolutePath)
        // Re-uploading a key with the same name replaces it, so existing IdentityFile names keep working.
        val replaced = state.value.identities.filter { it.name == displayName }
        store.update { it.copy(identities = it.identities.filterNot { existing -> existing.name == displayName } + key) }
        replaced.forEach { runCatching { File(it.path).delete() } }
        key
    }

    suspend fun deleteIdentityKey(id: String) {
        val key = state.value.identities.firstOrNull { it.id == id }
        store.update { it.copy(identities = it.identities.filterNot { existing -> existing.id == id }) }
        key?.let { runCatching { File(it.path).delete() } }
    }

    /**
     * Resolves a machine's IdentityFile pointer: a real file path, or the name of an uploaded key
     * (also written as `~/.ssh/<name>`).
     */
    private fun resolveIdentity(pointer: String): File? {
        val trimmed = pointer.trim()
        File(trimmed).takeIf { it.isFile }?.let { return it }
        val name = trimmed.substringAfterLast('/')
        return state.value.identities.firstOrNull { it.name == trimmed || it.name == name }
            ?.let { File(it.path) }?.takeIf { it.isFile }
    }

    suspend fun authorizeGCloud(client: GCloudOAuthClient): Result<Unit> = result {
        require(client.clientId.isNotBlank()) { "Google OAuth client ID is missing." }
        // Google Desktop OAuth clients use the loopback redirect flow. Use a local port just like
        // the reference vscode-remote-services implementation instead of a custom app scheme.
        val callbackPort = ServerSocket(0).use { it.localPort }
        val redirectUri = "http://127.0.0.1:$callbackPort"
        val request = AppAuthTransactions.authorization(
            authorizationEndpoint = GOOGLE_AUTHORIZATION_ENDPOINT,
            tokenEndpoint = GOOGLE_TOKEN_ENDPOINT,
            clientId = client.clientId,
            redirectUri = redirectUri,
            scope = GOOGLE_SCOPE,
            additional = mapOf("access_type" to "offline", "prompt" to "consent")
        )
        val code = OAuthCallbackService.keepAlive(appContext) {
            awaitLoopbackAuthorizationCode(
                redirectUri = redirectUri,
                url = request.toUri().toString(),
                state = request.state ?: error("OAuth state is missing")
            ) { url ->
                OAuthBrowser.open(appContext, url)
            }
        }
        val payload = AppAuthTransactions.exchange(
            appContext,
            authHttp,
            AppAuthTransactions.token(
                GOOGLE_TOKEN_ENDPOINT,
                client.clientId,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "code_verifier" to (request.codeVerifier ?: error("PKCE verifier is missing"))
                )
            ),
            AppAuthTransactions.authentication(
                if (client.clientSecret.isBlank()) "none" else "client_secret_post",
                client.clientSecret.takeIf(String::isNotBlank)
            )
        )
        val token = tokenFromPayload(payload, null)
        store.update { it.copy(oauthTokens = it.oauthTokens + (client.id to token)) }
    }

    /** Gets the current Cloud Shell endpoint and returns an updated machine record. */
    suspend fun refreshGCloudIp(machine: RemoteMachine): Result<RemoteMachine> = result {
        require(machine.type == RemoteMachineType.GCLOUD_SHELL) { "Only Cloud Shell machines have a remote IP lookup." }
        val client = clientFor(machine)
        val token = accessToken(client)
        val api = CloudShellApi(token)
        var environment = api.getEnvironment(machine.environmentName)
        if (!environment.isRunning()) {
            api.startEnvironment(machine.environmentName)
        } else {
            // The reference implementation authorizes an already-running Cloud Shell SSH endpoint
            // with the same Google access token. Starting the environment already performs the
            // corresponding authorization as part of that operation.
            runCatching { api.authorizeEnvironment(machine.environmentName) }
        }
        environment = api.getEnvironment(machine.environmentName)
        val endpoint = environment.endpoint()
        val (host, port) = endpoint ?: error("Google Cloud Shell did not return an SSH endpoint.")
        val username = environment.firstText("username", "user", "sshUsername")
            .orEmpty().ifBlank { machine.gcloudUsername.ifBlank { machine.username } }
        val updated = machine.copy(
            host = host,
            port = port,
            username = username,
            gcloudUsername = username
        )
        saveMachine(updated)
        updated
    }

    suspend fun startGCloudMachine(machine: RemoteMachine): Result<RemoteMachine> = result {
        require(machine.type == RemoteMachineType.GCLOUD_SHELL)
        val client = clientFor(machine)
        CloudShellApi(accessToken(client)).startEnvironment(machine.environmentName)
        refreshGCloudIp(machine).getOrThrow()
    }

    suspend fun execute(machine: RemoteMachine, command: String, timeoutSeconds: Int = 120): RemoteCommandResult {
        require(command.isNotBlank()) { "The command is empty." }
        val actual = if (machine.type == RemoteMachineType.GCLOUD_SHELL && machine.host.isBlank()) {
            refreshGCloudIp(machine).getOrThrow()
        } else machine
        return runSshCommand(actual, command, timeoutSeconds, retryCloudShell = true)
    }

    suspend fun executeTool(machine: RemoteMachine, toolName: String, arguments: JsonObject): JsonObject {
        require(machine.isToolEnabled(toolName)) { "Remote Machine tool is disabled." }
        return when (toolName) {
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

    suspend fun openTerminal(machine: RemoteMachine, passwordOverride: String? = null): RemoteTerminal {
        val actual = if (machine.type == RemoteMachineType.GCLOUD_SHELL && machine.host.isBlank()) {
            refreshGCloudIp(machine).getOrThrow()
        } else machine
        return try {
            openTerminalInternal(actual, passwordOverride)
        } catch (error: Exception) {
            if (error is CancellationException || actual.type != RemoteMachineType.GCLOUD_SHELL) throw error
            val refreshed = refreshGCloudIp(actual).getOrThrow()
            openTerminalInternal(refreshed, passwordOverride)
        }
    }

    private suspend fun openTerminalInternal(machine: RemoteMachine, passwordOverride: String?): RemoteTerminal = withContext(Dispatchers.IO) {
        val session = connect(machine, passwordOverride)
        try {
            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            // JSch only wires the piped streams into the channel thread if they are requested BEFORE
            // connect(). Asking for them afterwards left the channel with no stdin reader (nothing the
            // user typed was ever sent) and could drop the first output bytes.
            val input = channel.getInputStream()
            val output = channel.getOutputStream()
            channel.connect(machine.connectTimeoutSeconds * 1_000)
            val local = startLocalCommand(machine)
            RemoteTerminal(session, channel, input, output, local)
        } catch (error: Throwable) {
            session.disconnect()
            throw error
        }
    }

    private suspend fun runSshCommand(
        machine: RemoteMachine,
        command: String,
        timeoutSeconds: Int,
        retryCloudShell: Boolean
    ): RemoteCommandResult {
        return try {
            withContext(Dispatchers.IO) {
                val session = connect(machine)
                try {
                    val channel = session.openChannel("exec") as ChannelExec
                    val errors = ByteArrayOutputStream()
                    channel.setCommand(command)
                    channel.setInputStream(null)
                    channel.setErrStream(errors)
                    channel.connect(machine.connectTimeoutSeconds * 1_000)
                    val input = channel.getInputStream()
                    val output = withTimeout(timeoutSeconds.coerceIn(1, 600) * 1_000L) {
                        input.readBytes()
                    }.toString(Charsets.UTF_8)
                    // The channel can finish writing stderr just after stdout reaches EOF.
                    delay(20)
                    RemoteCommandResult(output, String(errors.toByteArray(), Charsets.UTF_8), channel.getExitStatus())
                } finally {
                    runCatching { session.disconnect() }
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException || !retryCloudShell || machine.type != RemoteMachineType.GCLOUD_SHELL) throw error
            val refreshed = refreshGCloudIp(machine).getOrThrow()
            runSshCommand(refreshed, command, timeoutSeconds, retryCloudShell = false)
        }
    }

    private fun connect(machine: RemoteMachine, passwordOverride: String? = null): Session {
        require(machine.host.isNotBlank()) { "Remote host is missing." }
        require(machine.username.isNotBlank()) { "Remote user is missing." }
        val jsch = JSch()
        if (machine.identityFile.isNotBlank()) {
            val identity = resolveIdentity(machine.identityFile)
                ?: error("Identity file not found: ${machine.identityFile.substringAfterLast('/')}. Upload it in Remote Machines settings or fix IdentityFile.")
            if (machine.identityPassphrase.isBlank()) jsch.addIdentity(identity.absolutePath)
            else jsch.addIdentity(identity.absolutePath, machine.identityPassphrase)
        }
        val session = jsch.getSession(machine.username, machine.host, machine.port)
        if (machine.authentication == RemoteAuthentication.PASSWORD) {
            val password = passwordOverride ?: machine.password
            val useKeyboardInteractive = machine.keyboardInteractive || machine.password.isEmpty()
            // Some SSH servers expose password login only through keyboard-interactive/PAM. JSch
            // needs a UserInfo implementation to answer those prompts; setting Session.password
            // alone only handles the plain ssh-userauth/password method and cancels KBI prompts.
            if (useKeyboardInteractive) {
                session.setUserInfo(PasswordKeyboardInteractiveUserInfo(machine.username, password))
                if (password.isNotEmpty()) session.setPassword(password)
            } else if (password.isNotEmpty()) {
                session.setPassword(password)
            }
        }
        session.setConfig("StrictHostKeyChecking", if (machine.strictHostKeyChecking) "yes" else "no")
        session.setConfig(
            "PreferredAuthentications",
            if (machine.authentication == RemoteAuthentication.PASSWORD &&
                (machine.keyboardInteractive || machine.password.isEmpty())
            ) {
                // Try PAM/keyboard-interactive first. JSch's password implementation retries the
                // same method internally after a failure, which can exhaust servers that expose
                // password login only as keyboard-interactive before KBI is reached.
                "keyboard-interactive,password"
            } else {
                "publickey,password,keyboard-interactive"
            }
        )
        if (machine.compression) session.setConfig("compression.s2c", "zlib,none")
        if (machine.compression) session.setConfig("compression.c2s", "zlib,none")
        if (machine.keepAliveSeconds > 0) session.setServerAliveInterval(machine.keepAliveSeconds * 1_000)
        session.timeout = machine.connectTimeoutSeconds * 1_000
        machine.proxyCommand.trim().takeIf(String::isNotBlank)?.let { command ->
            session.setProxy(CommandProxy(resolveBinary(command)))
        }
        session.connect(machine.connectTimeoutSeconds * 1_000)
        return session
    }

    private fun startLocalCommand(machine: RemoteMachine): Process? {
        val command = machine.localCommand.trim().takeIf(String::isNotBlank) ?: return null
        return runCatching {
            ProcessBuilder("sh", "-c", resolveBinary(command.replace("%h", machine.host).replace("%p", machine.port.toString())))
                .redirectErrorStream(true)
                .start()
        }.getOrNull()
    }

    private fun resolveBinary(command: String): String {
        var resolved = command
        state.value.binaries.forEach { binary ->
            val pattern = Regex("(^|\\s)${Regex.escape(binary.name)}(?=\\s|$)")
            resolved = pattern.replace(resolved) { match -> "${match.value.substringBefore(binary.name)}${binary.path}" }
        }
        return resolved
    }

    private suspend fun accessToken(client: GCloudOAuthClient): String {
        val existing = state.value.oauthTokens[client.id]
        if (existing != null && existing.accessToken.isNotBlank() &&
            (existing.expiresAt == null || existing.expiresAt > System.currentTimeMillis() + 60_000)
        ) return existing.accessToken
        val refresh = existing?.refreshToken?.takeIf(String::isNotBlank)
            ?: error("Authorize the Google OAuth client before connecting.")
        val payload = AppAuthTransactions.exchange(
            appContext,
            authHttp,
            AppAuthTransactions.token(
                GOOGLE_TOKEN_ENDPOINT,
                client.clientId,
                mapOf("grant_type" to "refresh_token", "refresh_token" to refresh)
            ),
            AppAuthTransactions.authentication(
                if (client.clientSecret.isBlank()) "none" else "client_secret_post",
                client.clientSecret.takeIf(String::isNotBlank)
            )
        )
        val updated = tokenFromPayload(payload, existing)
        store.update { it.copy(oauthTokens = it.oauthTokens + (client.id to updated)) }
        return updated.accessToken
    }

    private fun clientFor(machine: RemoteMachine): GCloudOAuthClient = state.value.oauthClients
        .firstOrNull { it.id == machine.oauthClientId }
        ?: error("Select a Google OAuth client in this machine's settings.")

    private fun tokenFromPayload(payload: JsonObject, previous: RemoteOAuthToken?): RemoteOAuthToken {
        val expiresIn = payload["expires_in"]?.jsonPrimitive?.longOrNull
        return RemoteOAuthToken(
            accessToken = payload["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                .ifBlank { error("Google OAuth did not return an access token.") },
            refreshToken = payload["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank) ?: previous?.refreshToken.orEmpty(),
            tokenType = payload["token_type"]?.jsonPrimitive?.contentOrNull ?: previous?.tokenType ?: "Bearer",
            expiresAt = expiresIn?.let { System.currentTimeMillis() + it * 1_000 }
                ?: previous?.expiresAt
        )
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private inner class CloudShellApi(private val accessToken: String) {
        suspend fun getEnvironment(name: String): JsonObject = request(environmentUrl(name), "GET")

        suspend fun startEnvironment(name: String): JsonObject =
            waitForOperation(request("${environmentUrl(name)}:start", "POST", accessTokenBody()))

        suspend fun authorizeEnvironment(name: String): JsonObject = waitForOperation(
            request("${environmentUrl(name)}:authorize", "POST", accessTokenBody())
        )

        private suspend fun waitForOperation(operation: JsonObject): JsonObject {
            val operationName = operation["name"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank) ?: return operation
            if (operation["done"]?.jsonPrimitive?.booleanOrNull == true) {
                operation["error"]?.let { error("Google Cloud Shell operation failed: $it") }
                return operation
            }
            repeat(80) {
                delay(1_500)
                val current = request(
                    "https://cloudshell.googleapis.com/v1/$operationName",
                    "GET"
                )
                if (current["done"]?.jsonPrimitive?.booleanOrNull == true) {
                    current["error"]?.let { error("Google Cloud Shell operation failed: $it") }
                    return current
                }
            }
            error("Timed out while waiting for the Google Cloud Shell operation.")
        }

        private fun accessTokenBody(): String = buildJsonObject {
            put("accessToken", accessToken)
        }.toString()

        private suspend fun request(url: String, method: String, body: String? = null): JsonObject = withContext(Dispatchers.IO) {
            val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseBody = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (connection.responseCode !in 200..299) {
                    error("Google Cloud Shell API ${connection.responseCode}: ${responseBody.take(500)}")
                }
                if (responseBody.isBlank()) JsonObject(emptyMap())
                else json.parseToJsonElement(responseBody).jsonObject
            } finally {
                connection.disconnect()
            }
        }

        private fun environmentUrl(name: String): String {
            val clean = name.trim().removePrefix("users/me/environments/").ifBlank { "default" }
            val encoded = clean.split('/').joinToString("/") {
                URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
            }
            return "https://cloudshell.googleapis.com/v1/users/me/environments/$encoded"
        }
    }

    private fun JsonObject.firstText(vararg names: String): String? {
        val environment = (this["environment"] as? JsonObject) ?: this
        return names.firstNotNullOfOrNull { name ->
            environment[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        }
    }

    private fun JsonObject.endpoint(): Pair<String, Int>? {
        val host = firstText("host", "sshHost", "ip", "externalIp") ?: return null
        val port = firstText("port", "sshPort")?.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        return host to port
    }

    private fun JsonObject.isRunning(): Boolean =
        firstText("state", "status")?.equals("RUNNING", ignoreCase = true) == true

    private fun Int?.orZero(): Int = this ?: 0

    companion object {
        const val GOOGLE_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val GOOGLE_SCOPE = "openid email profile https://www.googleapis.com/auth/cloud-platform"
    }
}

/**
 * Supplies the saved password to SSH servers that implement password login using
 * keyboard-interactive (usually PAM). Every hidden prompt is answered with the saved password;
 * this matches the behavior users expect from the Password authentication mode for servers that
 * label the challenge differently from "Password:".
 */
internal class PasswordKeyboardInteractiveUserInfo(
    private val username: String,
    private val password: String
) : UserInfo, UIKeyboardInteractive {
    override fun getPassphrase(): String? = null

    override fun getPassword(): String = password

    override fun promptPassword(message: String): Boolean = password.isNotEmpty()

    override fun promptPassphrase(message: String): Boolean = false

    override fun promptYesNo(message: String): Boolean = false

    override fun showMessage(message: String) = Unit

    override fun promptKeyboardInteractive(
        destination: String,
        name: String,
        instruction: String,
        prompt: Array<String>,
        echo: BooleanArray
    ): Array<String>? {
        if (prompt.size != echo.size) return null
        if (password.isEmpty() && echo.any { !it }) return null
        return Array(prompt.size) { index ->
            val text = prompt[index]
            when {
                isPasswordPrompt(text) -> password
                isUsernamePrompt(text) && echo[index] -> username
                !echo[index] -> password
                echo[index] -> ""
                else -> ""
            }
        }
    }

    private fun isPasswordPrompt(prompt: String): Boolean {
        val normalized = prompt.lowercase()
        return listOf("password", "passphrase", "pass phrase", "passcode", "pass code")
            .any(normalized::contains)
    }

    private fun isUsernamePrompt(prompt: String): Boolean {
        val normalized = prompt.lowercase()
        return listOf("username", "user name", "login").any(normalized::contains)
    }
}

/** ProxyCommand implementation backed by a local executable selected in Remote Machines settings. */
private class CommandProxy(private val commandTemplate: String) : Proxy {
    private var process: Process? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val command = commandTemplate.replace("%h", host).replace("%p", port.toString())
        process = ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
        input = process!!.inputStream
        output = process!!.outputStream
    }

    override fun getInputStream(): InputStream = requireNotNull(input)
    override fun getOutputStream(): OutputStream = requireNotNull(output)
    override fun getSocket(): java.net.Socket? = null

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { process?.destroy() }
        process = null
    }
}
