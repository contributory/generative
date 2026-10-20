package org.starfall.multigateway.shared.logging

import io.ktor.client.plugins.api.createClientPlugin
import org.starfall.multigateway.shared.model.logging.DebugLogCategory.ERRORS
import org.starfall.multigateway.shared.model.logging.DebugLogCategory.NETWORK
import org.starfall.multigateway.shared.model.logging.DebugLogLevel

private const val TAG = "HTTP (Ktor)"

/** Records request and response summaries of a Ktor client; bodies are never read here. */
val DebugLogKtorPlugin = createClientPlugin("MultiGatewayDebugLog") {
    onRequest { request, _ ->
        DebugLog.log(NETWORK, TAG) { "→ ${request.method.value} ${DebugLogRedactor.redact(request.url.buildString())}" }
    }
    onResponse { response ->
        val call = response.call.request
        val ms = response.responseTime.timestamp - response.requestTime.timestamp
        val ok = response.status.value < 400
        val label = "${call.method.value} ${DebugLogRedactor.redact(call.url.toString())}"
        DebugLog.log(NETWORK, TAG, if (ok) DebugLogLevel.INFO else DebugLogLevel.ERROR) {
            "← ${response.status.value} $label ($ms ms)"
        }
        if (!ok) DebugLog.log(ERRORS, TAG, DebugLogLevel.ERROR) { "HTTP ${response.status} for $label" }
    }
}
