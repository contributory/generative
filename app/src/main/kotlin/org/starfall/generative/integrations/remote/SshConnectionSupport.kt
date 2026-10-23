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
import okio.ByteString.Companion.toByteString
import kotlinx.coroutines.runBlocking
import org.starfall.generative.shared.model.WebSocketProxyConfig
import org.starfall.generative.shared.model.remote.RemoteSecretPrompt
import org.starfall.generative.shared.model.remote.RemoteSecretRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
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
    private var input: WebSocketInputStream? = null
    private var output: WebSocketOutputStream? = null
    private val closeLatch = CountDownLatch(1)

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val incoming = WebSocketInputStream()
        val opened = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val requestUrl = config.url.replace("%h", host).replace("%p", port.toString())
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout((config.connectTimeoutSeconds + config.handshakeTimeoutSeconds).toLong(), TimeUnit.SECONDS)
            // JSch has no socket behind this proxy, so it never sends SSH keepalives: WebSocket pings keep
            // idle gateways from closing the tunnel and report a dead one.
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
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
                incoming.push(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                incoming.push(text.toByteArray(Charsets.UTF_8))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure.compareAndSet(null, t)
                opened.countDown()
                closeLatch.countDown()
                incoming.end()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closeLatch.countDown()
                incoming.end()
            }
        })
        webSocket = socket
        if (!opened.await(config.handshakeTimeoutSeconds.toLong(), TimeUnit.SECONDS)) {
            socket.cancel()
            throw IllegalStateException("WebSocket proxy handshake timed out.")
        }
        failure.get()?.let { throw IllegalStateException("WebSocket proxy connection failed.", it) }
        input = incoming
        output = WebSocketOutputStream(socket)
    }

    override fun getInputStream(): InputStream = requireNotNull(input)
    override fun getOutputStream(): OutputStream = requireNotNull(output)
    override fun getSocket(): java.net.Socket? = null

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        webSocket?.close(1000, "SSH proxy closed")
        closeLatch.await(config.closeTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        client?.dispatcher?.executorService?.shutdownNow()
        input = null
        output = null
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
        const val PING_INTERVAL_SECONDS = 20L
        val TRUST_ALL_MANAGER = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        }
    }
}

/**
 * Bytes received from the WebSocket, readable from any thread. This replaces java.io.PipedInputStream,
 * which throws "Write end dead" / "Pipe broken" once the thread that last wrote to it has exited
 * (pool workers do exit when idle) and so killed idle SSH sessions shortly after they were opened.
 */
internal class WebSocketInputStream : InputStream() {
    private val chunks = LinkedBlockingQueue<ByteArray>()
    private var current: ByteArray? = null
    private var offset = 0
    @Volatile private var ended = false
    @Volatile private var closed = false

    fun push(bytes: ByteArray) {
        if (!closed && bytes.isNotEmpty()) chunks.offer(bytes)
    }

    /** The peer closed or failed: pending bytes are still delivered, then reads return -1. */
    fun end() {
        ended = true
        chunks.offer(END)
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var chunk = current
        while (chunk == null || offset >= chunk.size) {
            if (closed) throw java.io.IOException("WebSocket proxy closed.")
            val next = chunks.poll(1, TimeUnit.SECONDS)
            if (next == null) {
                if (ended && chunks.isEmpty()) return -1
                continue
            }
            if (next === END) {
                chunks.offer(END)
                return -1
            }
            current = next
            offset = 0
            chunk = next
        }
        val count = minOf(len, chunk.size - offset)
        System.arraycopy(chunk, offset, buffer, off, count)
        offset += count
        return count
    }

    override fun available(): Int = current?.let { it.size - offset } ?: 0

    override fun close() {
        closed = true
    }

    private companion object {
        val END = ByteArray(0)
    }
}

/** Sends every write straight to the WebSocket, so no helper thread can die and break the tunnel. */
internal class WebSocketOutputStream(private val socket: WebSocket) : OutputStream() {
    @Volatile private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(buffer: ByteArray, off: Int, len: Int) {
        if (closed) throw java.io.IOException("WebSocket proxy closed.")
        if (len == 0) return
        if (!socket.send(buffer.toByteString(off, len))) throw java.io.IOException("WebSocket proxy closed.")
    }

    override fun close() {
        closed = true
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
