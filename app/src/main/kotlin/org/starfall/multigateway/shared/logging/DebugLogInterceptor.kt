package org.starfall.multigateway.shared.logging

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okio.Buffer
import org.starfall.multigateway.shared.model.logging.DebugLogCategory.ERRORS
import org.starfall.multigateway.shared.model.logging.DebugLogCategory.NETWORK
import org.starfall.multigateway.shared.model.logging.DebugLogCategory.PAYLOAD
import org.starfall.multigateway.shared.model.logging.DebugLogLevel
import java.io.IOException

private const val TAG = "HTTP"
private const val MAX_BODY_BYTES = 256L * 1024

/**
 * Records requests and responses of an OkHttp client. Bodies are captured only when they are small,
 * textual and not streamed, so reading them for the log never delays or consumes a live response.
 */
class DebugLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!DebugLog.isEnabled(NETWORK) && !DebugLog.isEnabled(PAYLOAD) && !DebugLog.isEnabled(ERRORS)) {
            return chain.proceed(request)
        }
        val label = "${request.method} ${DebugLogRedactor.redact(request.url.toString())}"
        DebugLog.log(NETWORK, TAG) { "→ $label" }
        DebugLog.log(PAYLOAD, TAG) {
            buildString {
                append("→ ").append(label).append('\n').append(headerText(request.headers))
                val body = request.body
                append(
                    when {
                        body == null -> ""
                        body.isOneShot() || body.isDuplex() -> "(request body not captured: one-shot)"
                        !isText(body.contentType()) -> "(request body not captured: ${body.contentType() ?: "unknown type"})"
                        body.contentLength() !in 0..MAX_BODY_BYTES -> "(request body not captured: ${body.contentLength()} bytes)"
                        else -> Buffer().also { body.writeTo(it) }.readUtf8()
                    }
                )
            }
        }
        val startNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            DebugLog.error(ERRORS, TAG, "✗ $label after ${elapsedMs(startNs)} ms", e)
            DebugLog.log(NETWORK, TAG, DebugLogLevel.ERROR) { "✗ $label (${e::class.java.simpleName})" }
            throw e
        }
        val ms = elapsedMs(startNs)
        val level = if (response.isSuccessful || response.code in 300..399) DebugLogLevel.INFO else DebugLogLevel.ERROR
        DebugLog.log(NETWORK, TAG, level) { "← ${response.code} $label ($ms ms)" }
        if (level == DebugLogLevel.ERROR) {
            DebugLog.log(ERRORS, TAG, level) { "HTTP ${response.code} ${response.message} for $label" }
        }
        DebugLog.log(PAYLOAD, TAG) {
            buildString {
                append("← ").append(response.code).append(' ').append(label).append('\n')
                append(headerText(response.headers))
                val body = response.body
                val type = body.contentType()
                val length = body.contentLength()
                append(
                    when {
                        !isText(type) || type?.subtype?.contains("stream") == true || type?.subtype?.contains("ndjson") == true ->
                            "(streamed or non-text response body not captured)"
                        length !in 0..MAX_BODY_BYTES -> "(response body not captured: unknown or large size)"
                        else -> response.peekBody(MAX_BODY_BYTES).string()
                    }
                )
            }
        }
        return response
    }

    private fun elapsedMs(startNs: Long) = (System.nanoTime() - startNs) / 1_000_000

    private fun isText(type: MediaType?): Boolean =
        type != null && (type.type == "text" || type.subtype.let { it.contains("json") || it.contains("xml") || it == "x-www-form-urlencoded" })

    private fun headerText(headers: Headers): String = buildString {
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            append(name).append(": ")
            append(if (DebugLogRedactor.isSensitiveHeader(name)) DebugLogRedactor.MASK else headers.value(i)).append('\n')
        }
        if (isNotEmpty()) append('\n')
    }
}
