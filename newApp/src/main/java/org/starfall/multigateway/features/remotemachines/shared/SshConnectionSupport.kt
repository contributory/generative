package org.starfall.multigateway.features.remotemachines.shared

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream

/**
 * Supplies the saved password to SSH servers that implement password login using
 * keyboard-interactive (usually PAM). Every hidden prompt is answered with the saved password;
 * this matches the behavior users expect from the Password authentication mode for servers that
 * label the challenge differently from "Password:".
 */
internal class PasswordKeyboardInteractiveUserInfo(
    private val username: String,
    private val password: String,
    private val askSecret: RemoteSecretPrompt? = null
) : UserInfo, UIKeyboardInteractive {
    private var enteredPassword: String? = null
    private var enteredPassphrase: String? = null

    override fun getPassphrase(): String? = enteredPassphrase

    override fun getPassword(): String = enteredPassword ?: password

    override fun promptPassword(message: String): Boolean {
        if (password.isNotEmpty()) return true
        enteredPassword = ask(passphrase = false, message = message) ?: return false
        return true
    }

    override fun promptPassphrase(message: String): Boolean {
        enteredPassphrase = ask(passphrase = true, message = message) ?: return false
        return true
    }

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
        val answers = ArrayList<String>(prompt.size)
        for (index in prompt.indices) {
            val text = prompt[index]
            val answer = when {
                !echo[index] || isPasswordPrompt(text) -> password.ifEmpty {
                    ask(passphrase = isPassphrasePrompt(text), message = text)
                }
                isUsernamePrompt(text) -> username
                else -> ""
            } ?: return null
            answers += answer
        }
        return answers.toTypedArray()
    }

    /** SSH invokes UserInfo on its blocking connection thread; the UI prompt runs on Main. */
    private fun ask(passphrase: Boolean, message: String): String? {
        val prompt = askSecret ?: return null
        return runCatching { runBlocking { prompt(RemoteSecretRequest(passphrase, message)) } }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun isPasswordPrompt(prompt: String): Boolean {
        val normalized = prompt.lowercase()
        return listOf("password", "passphrase", "pass phrase", "passcode", "pass code")
            .any(normalized::contains)
    }

    private fun isPassphrasePrompt(prompt: String): Boolean {
        val normalized = prompt.lowercase()
        return "passphrase" in normalized || "pass phrase" in normalized
    }

    private fun isUsernamePrompt(prompt: String): Boolean {
        val normalized = prompt.lowercase()
        return listOf("username", "user name", "login").any(normalized::contains)
    }
}

/** ProxyCommand implementation backed by a local executable selected in Remote Machines settings. */
internal class CommandProxy(private val commandTemplate: String) : Proxy {
    private var process: Process? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val command = commandTemplate.replace("%h", host).replace("%p", port.toString())
        val started = ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
        process = started
        input = started.inputStream
        output = started.outputStream
        discardProcessStream(started.errorStream, "remote-ssh-proxy-stderr")
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

internal fun discardProcessStream(stream: InputStream, threadName: String) {
    Thread({
        runCatching {
            stream.use { input ->
                val buffer = ByteArray(8_192)
                while (input.read(buffer) >= 0) { }
            }
        }
    }, threadName).apply {
        isDaemon = true
        start()
    }
}
