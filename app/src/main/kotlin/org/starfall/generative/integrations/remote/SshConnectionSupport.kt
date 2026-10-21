package org.starfall.generative.integrations.remote

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlinx.coroutines.runBlocking
import org.starfall.generative.shared.model.WebSocketProxyConfig
import java.io.ByteArrayInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

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

/** SSH jump-host proxy backed by a direct-tcpip channel. */
/** JSch Proxy backed by a direct-tcpip channel opened through another SSH machine. */
internal class SshJumpProxy(private val openProxySession: () -> com.jcraft.jsch.Session) : Proxy {
    private var session: com.jcraft.jsch.Session? = null
    private var channel: ChannelDirectTCPIP? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val openedSession = openProxySession()
        try {
            val openedChannel = openedSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            openedChannel.setHost(host)
            openedChannel.setPort(port)
            val openedInput = openedChannel.getInputStream()
            val openedOutput = openedChannel.getOutputStream()
            openedChannel.connect(timeout)
            session = openedSession
            channel = openedChannel
            input = openedInput
            output = openedOutput
        } catch (error: Throwable) {
            runCatching { openedSession.disconnect() }
            throw error
        }
    }

    override fun getInputStream(): InputStream = requireNotNull(input)
    override fun getOutputStream(): OutputStream = requireNotNull(output)
    override fun getSocket(): java.net.Socket? = null

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        input = null
        output = null
        channel = null
        session = null
    }
}

/**
 * JSch Proxy backed by a WebSocket binary stream. The endpoint is expected to forward bytes from
 * the WebSocket to the SSH target selected by its URL/configuration.
 */
internal class WebSocketProxy(private val config: WebSocketProxyConfig) : Proxy {
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var incomingWriter: PipedOutputStream? = null
    private var outgoingReader: PipedInputStream? = null
    private var writerThread: Thread? = null
    private val closeLatch = CountDownLatch(1)

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val incoming = PipedInputStream(64 * 1024)
        val incomingWriter = PipedOutputStream(incoming)
        val outgoing = PipedInputStream(64 * 1024)
        val outgoingWriter = PipedOutputStream(outgoing)
        val opened = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val requestUrl = config.url.replace("%h", host).replace("%p", port.toString())
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout((config.connectTimeoutSeconds + config.handshakeTimeoutSeconds).toLong(), TimeUnit.SECONDS)
        configureTls(builder, config)
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .headers(Headers.Builder().apply {
                config.headers.forEach { (key, value) -> add(key, value) }
                if (config.subProtocols.isNotEmpty()) {
                    add("Sec-WebSocket-Protocol", config.subProtocols.joinToString(", "))
                }
            }.build())
        val openedClient = builder.build()
        client = openedClient
        val socket = openedClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@WebSocketProxy.webSocket = webSocket
                opened.countDown()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                runCatching { incomingWriter.write(bytes.toByteArray()); incomingWriter.flush() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    incomingWriter.write(text.toByteArray(Charsets.UTF_8))
                    incomingWriter.flush()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure.compareAndSet(null, t)
                opened.countDown()
                closeLatch.countDown()
                runCatching { incomingWriter.close() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closeLatch.countDown()
                runCatching { incomingWriter.close() }
            }
        })
        webSocket = socket
        if (!opened.await(config.handshakeTimeoutSeconds.toLong(), TimeUnit.SECONDS)) {
            socket.cancel()
            throw IllegalStateException("WebSocket proxy handshake timed out.")
        }
        failure.get()?.let { throw IllegalStateException("WebSocket proxy connection failed.", it) }
        this.input = incoming
        this.output = outgoingWriter
        this.incomingWriter = incomingWriter
        this.outgoingReader = outgoing
        writerThread = Thread {
            runCatching {
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = outgoing.read(buffer)
                    if (count < 0) break
                    check(socket.send(ByteString.of(*buffer.copyOfRange(0, count)))) { "WebSocket proxy closed." }
                }
            }
        }.apply { isDaemon = true; name = "remote-websocket-proxy-output"; start() }
    }

    override fun getInputStream(): InputStream = requireNotNull(input)
    override fun getOutputStream(): OutputStream = requireNotNull(output)
    override fun getSocket(): java.net.Socket? = null

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { outgoingReader?.close() }
        runCatching { incomingWriter?.close() }
        webSocket?.close(1000, "SSH proxy closed")
        closeLatch.await(config.closeTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        writerThread?.interrupt()
        client?.dispatcher?.executorService?.shutdownNow()
        input = null
        output = null
        incomingWriter = null
        outgoingReader = null
        webSocket = null
        client = null
    }

    private fun configureTls(builder: OkHttpClient.Builder, config: WebSocketProxyConfig) {
        val trustManager = when {
            config.skipCertificateVerification -> TRUST_ALL_MANAGER
            config.caCertificatePem.isBlank() -> null
            else -> parseTrustManager(config.caCertificatePem)
        } ?: return
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), SecureRandom())
        builder.sslSocketFactory(context.socketFactory, trustManager)
        if (config.skipCertificateVerification) builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
    }

    private fun parseTrustManager(pem: String): X509TrustManager {
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII))
        )
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("remote-websocket-ca", certificate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }

    private companion object {
        val TRUST_ALL_MANAGER = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        }
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
