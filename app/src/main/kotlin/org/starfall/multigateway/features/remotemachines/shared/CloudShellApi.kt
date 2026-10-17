package org.starfall.multigateway.features.remotemachines.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URLEncoder

internal class CloudShellApi(private val accessToken: String, private val json: Json) {
    suspend fun getEnvironment(name: String): JsonObject = request(environmentUrl(name), "GET")

    suspend fun startEnvironment(name: String, publicKeys: List<String>): JsonObject =
        waitForOperation(request("${environmentUrl(name)}:start", "POST", accessTokenBody(publicKeys)))

    suspend fun authorizeEnvironment(name: String): JsonObject = waitForOperation(
        request("${environmentUrl(name)}:authorize", "POST", accessTokenBody())
    )

    suspend fun addPublicKey(name: String, key: String): JsonObject = waitForOperation(
        request(
            "${environmentUrl(name)}:addPublicKey",
            "POST",
            buildJsonObject { put("key", JsonPrimitive(key)) }.toString()
        )
    )

    suspend fun waitUntilRunning(name: String): JsonObject {
        repeat(80) {
            delay(1_500)
            val current = getEnvironment(name)
            if (current.isRunning()) return current
        }
        error("Timed out while waiting for Google Cloud Shell to start.")
    }

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

    private fun accessTokenBody(publicKeys: List<String> = emptyList()): String = buildJsonObject {
        put("accessToken", accessToken)
        if (publicKeys.isNotEmpty()) put("publicKeys", JsonArray(publicKeys.map(::JsonPrimitive)))
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


internal fun JsonObject.firstText(vararg names: String): String? {
    val environment = (this["environment"] as? JsonObject) ?: this
    return names.firstNotNullOfOrNull { name ->
        environment[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }
}

internal fun JsonObject.endpoint(): Pair<String, Int>? {
    val host = firstText("sshHost", "host", "ip", "externalIp") ?: return null
    val port = firstText("sshPort", "port")?.toIntOrNull()?.coerceIn(1, 65535) ?: 22
    return host to port
}

internal fun JsonObject.isRunning(): Boolean =
    firstText("state", "status")?.equals("RUNNING", ignoreCase = true) == true

internal fun JsonObject.isPending(): Boolean =
    firstText("state", "status")?.equals("PENDING", ignoreCase = true) == true

internal fun JsonObject.hasPublicKey(key: String): Boolean {
    val expected = key.trim().split(Regex("\\s+")).take(2).joinToString(" ")
    val keys = this["publicKeys"] as? JsonArray ?: return false
    return keys.any { value ->
        value.jsonPrimitive.contentOrNull?.trim()?.split(Regex("\\s+"))?.take(2)?.joinToString(" ") == expected
    }
}

