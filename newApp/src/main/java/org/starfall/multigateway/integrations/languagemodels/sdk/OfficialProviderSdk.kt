package org.starfall.multigateway.integrations.languagemodels.sdk

import com.openai.client.OpenAIClientImpl
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.genai.Client
import com.google.genai.types.ClientOptions
import com.google.genai.types.HttpOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.shared.files.AttachmentResolver
import java.time.Duration

internal inline fun <R> com.openai.client.OpenAIClient.useClient(block: (com.openai.client.OpenAIClient) -> R): R =
    try { block(this) } finally { close() }

internal inline fun <R> com.anthropic.client.AnthropicClient.useClient(block: (com.anthropic.client.AnthropicClient) -> R): R =
    try { block(this) } finally { close() }

/** Each operation owns its client and immutable provider snapshot, including credentials.
 * Switching providers cannot redirect an in-flight request or reuse another provider's key.
 */
internal class OfficialProviderSdk(private val attachments: AttachmentResolver) {
    // SDKs wrap an interrupted socket in their own IOException type. Preserve
    // coroutine cancellation instead of displaying it as a generation failure.
    internal suspend fun <T> sdkCall(block: () -> T): T = try {
        runInterruptible(block = block)
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        throw e
    }

    private val content = SdkAttachmentContent(attachments)
    internal fun openAiChatContent(message: StoredMessage) = content.openAiChatContent(message)
    internal fun openAiResponseContent(message: StoredMessage) = content.openAiResponseContent(message)
    internal fun anthropicContent(message: StoredMessage) = content.anthropicContent(message)
    internal suspend fun googleParts(client: Client, message: StoredMessage) = content.googleParts(client, message)

    internal fun baseUrl(provider: ModelProvider): String {
        var base = provider.baseUrl.trim().trimEnd('/')
        when (provider.type) {
            ProviderType.OPENAI, ProviderType.OPENAI_RESPONSES -> for (suffix in listOf("/chat/completions", "/responses", "/models")) {
                if (base.endsWith(suffix)) base = base.removeSuffix(suffix)
            }
            // Anthropic SDK adds /v1/messages itself.
            ProviderType.ANTHROPIC -> {
                base = base.removeSuffix("/messages").removeSuffix("/models").removeSuffix("/v1")
            }
            else -> Unit
        }
        return base
    }

    internal fun headers(provider: ModelProvider): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val auth = provider.auth
        result.putAll(auth.requestHeaders(provider.type))
        result.putAll(provider.config.headers)
        return result
    }

    internal fun openAi(provider: ModelProvider) = OpenAIClientImpl(com.openai.core.ClientOptions.builder().apply {
        val transport = com.openai.client.okhttp.OkHttpClient.builder().timeout(Duration.ofMinutes(2)).build()
        httpClient(object : com.openai.core.http.HttpClient by transport {
            override fun execute(request: com.openai.core.http.HttpRequest,
                                 requestOptions: com.openai.core.RequestOptions): com.openai.core.http.HttpResponse {
                // Interrupting a blocking socket read does not reliably close the socket.
                // The official async transport cancels its OkHttp call when this future is cancelled.
                val future = transport.executeAsync(request, requestOptions)
                try {
                    return future.get()
                } catch (e: InterruptedException) {
                    future.cancel(true)
                    throw e
                } catch (e: java.util.concurrent.ExecutionException) {
                    throw (e.cause ?: e)
                }
            }
        })
        baseUrl(baseUrl(provider))
        timeout(Duration.ofMinutes(2))
        maxRetries(0)
        // The SDK requires an explicit authentication strategy. Gateways can use
        // headers or query parameters rather than its built-in bearer credential.
        if (provider.auth.method == AuthMethod.PLATFORM_DEFAULT && provider.auth.token.isNotBlank()) {
            apiKey(provider.auth.token)
            provider.config.headers.forEach { (key, value) -> putHeader(key, value) }
        } else httpRequestAuthenticator(object : com.openai.core.http.HttpRequestAuthenticator {
            override fun authenticate(request: com.openai.core.http.HttpRequest) = request.toBuilder().apply {
                headers(provider).forEach { (key, value) -> putHeader(key, value) }
                if (provider.auth.method == AuthMethod.QUERY_PARAM && provider.auth.token.isNotBlank()) {
                    putQueryParam(provider.auth.key?.takeIf { it.isNotBlank() } ?: "key", provider.auth.token)
                }
            }.build()
        })
    }.build())

    internal fun anthropic(provider: ModelProvider) = AnthropicOkHttpClient.builder().apply {
        baseUrl(baseUrl(provider))
        timeout(Duration.ofMinutes(2))
        maxRetries(0)
        if (provider.auth.method == AuthMethod.PLATFORM_DEFAULT && provider.auth.token.isNotBlank()) apiKey(provider.auth.token)
        (if (provider.auth.method == AuthMethod.PLATFORM_DEFAULT) provider.config.headers else headers(provider))
            .forEach { (key, value) -> putHeader(key, value) }
        if (provider.auth.method == AuthMethod.QUERY_PARAM && provider.auth.token.isNotBlank()) {
            putQueryParam(provider.auth.key?.takeIf { it.isNotBlank() } ?: "key", provider.auth.token)
        }
    }.build()

    internal fun google(provider: ModelProvider): Client {
        val base = baseUrl(provider).removeSuffix("/models")
        val version = Regex("/(v1(?:beta|alpha)?)$").find(base)?.groupValues?.get(1)
        // Supply auth in the transport so custom headers/query auth work with gateways too.
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request().newBuilder()
            if (provider.auth.method == AuthMethod.PLATFORM_DEFAULT && provider.auth.token.isNotBlank()) {
                provider.config.headers.forEach { (key, value) -> request.header(key, value) }
            } else {
                request.removeHeader("x-goog-api-key")
                headers(provider).forEach { (key, value) -> request.header(key, value) }
            }
            if (provider.auth.method == AuthMethod.QUERY_PARAM && provider.auth.token.isNotBlank()) {
                request.url(chain.request().url.newBuilder()
                    .setQueryParameter(provider.auth.key?.takeIf { it.isNotBlank() } ?: "key", provider.auth.token).build())
            }
            chain.proceed(request.build())
        }.build()
        return Client.builder()
            .apiKey(if (provider.auth.method == AuthMethod.PLATFORM_DEFAULT && provider.auth.token.isNotBlank()) provider.auth.token else "gateway-auth-configured-in-transport")
            .httpOptions(HttpOptions.builder()
                .baseUrl(if (version == null) base else base.removeSuffix("/$version"))
                .apiVersion(version ?: "v1beta").timeout(120_000).build())
            .clientOptions(ClientOptions.builder().customHttpClient(transport).build())
            .build()
    }

    internal suspend fun connectionResult(provider: ModelProvider, operation: suspend () -> Unit): Result<String> = try {
        withContext(Dispatchers.IO) { operation() }
        Result.success("${provider.type.displayName} connection successful!")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Result.failure(e)
    }

}
