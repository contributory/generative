package org.starfall.generative

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.starfall.generative.shared.logging.DebugLog
import org.starfall.generative.shared.logging.DebugLogInterceptor
import org.starfall.generative.shared.logging.DebugLogKtorPlugin
import org.starfall.generative.shared.logging.DebugLogRedactor
import org.starfall.generative.shared.model.logging.DebugLogCategory
import org.starfall.generative.shared.model.logging.DebugLogCategory.*
import org.starfall.generative.shared.model.logging.DebugLogLevel

class DebugLogTest {
    @Before fun reset() = DebugLog.configure(false, emptySet())
    @After fun cleanUp() = DebugLog.configure(false, emptySet())

    @Test fun nothingIsRecordedAndMessagesAreNotBuiltWhileDebugIsOff() {
        var built = false
        DebugLog.log(NETWORK, "t") { built = true; "hidden" }
        assertFalse(built)
        assertTrue(DebugLog.entries.value.isEmpty())
    }

    @Test fun onlyCategoriesInScopeAreRecorded() {
        DebugLog.configure(true, setOf(ERRORS, TOOLS))
        DebugLog.log(NETWORK, "t") { "network" }
        DebugLog.log(TOOLS, "t") { "tool" }
        DebugLog.error(ERRORS, "t", "failed", IllegalStateException("boom"))
        val entries = DebugLog.entries.value
        assertEquals(listOf(TOOLS, ERRORS), entries.map { it.category })
        assertEquals(DebugLogLevel.ERROR, entries.last().level)
        assertTrue(entries.last().message.contains("IllegalStateException: boom"))
    }

    @Test fun turningDebugOffDropsCapturedLogs() {
        DebugLog.configure(true, DebugLogCategory.all)
        DebugLog.log(NETWORK, "t") { "kept until disabled" }
        assertEquals(1, DebugLog.entries.value.size)
        DebugLog.configure(false, DebugLogCategory.all)
        assertTrue(DebugLog.entries.value.isEmpty())
        assertFalse(DebugLog.isEnabled(NETWORK))
    }

    @Test fun clearRemovesEveryEntry() {
        DebugLog.configure(true, DebugLogCategory.all)
        repeat(3) { DebugLog.log(TOKENS, "t") { "entry $it" } }
        DebugLog.clear()
        assertTrue(DebugLog.entries.value.isEmpty())
    }

    @Test fun bufferKeepsOnlyTheNewestEntriesAndLongMessagesAreTruncated() {
        DebugLog.configure(true, DebugLogCategory.all)
        repeat(DebugLog.MAX_ENTRIES + 25) { DebugLog.log(NETWORK, "t") { "entry $it" } }
        val entries = DebugLog.entries.value
        assertEquals(DebugLog.MAX_ENTRIES, entries.size)
        assertEquals("entry ${DebugLog.MAX_ENTRIES + 24}", entries.last().message)
        assertEquals("entry 25", entries.first().message)

        DebugLog.clear()
        DebugLog.log(PAYLOAD, "t") { "x".repeat(DebugLog.MAX_MESSAGE_CHARS + 100) }
        val message = DebugLog.entries.value.single().message
        assertTrue(message.startsWith("x".repeat(DebugLog.MAX_MESSAGE_CHARS)))
        assertTrue(message.endsWith("(100 more characters)"))
    }

    @Test fun copyFormatsCarryTimeCategoryLevelTagAndMessage() {
        DebugLog.configure(true, DebugLogCategory.all)
        DebugLog.log(TOOLS, "Tool", DebugLogLevel.WARN) { "line one\nline two" }
        DebugLog.log(TOKENS, "Generation") { "second" }
        val entries = DebugLog.entries.value
        assertTrue(Regex("""\[\d{2}:\d{2}:\d{2}\.\d{3}] \[TOOLS] \[WARN] Tool\nline one\nline two""").matches(entries[0].format()))
        val all = DebugLog.exportAll()
        assertTrue(all.indexOf("line two") < all.indexOf("second"))
        assertTrue(all.contains("\n\n[")) // blocks are separated by a blank line
    }

    @Test fun exportKeepsTheNewestEntriesThatFitTheClipboardLimit() {
        DebugLog.configure(true, DebugLogCategory.all)
        repeat(300) { DebugLog.log(PAYLOAD, "t") { "$it " + "y".repeat(3_000) } }
        val exported = DebugLog.exportAll()
        assertTrue(exported.length <= DebugLog.MAX_EXPORT_CHARS + 100)
        assertTrue(exported.contains("299 yyy"))
        assertFalse(exported.contains("] t\n0 yyy")) // the oldest entry no longer fits
    }

    @Test fun credentialsAreMaskedBeforeTheyReachTheLog() {
        DebugLog.configure(true, DebugLogCategory.all)
        DebugLog.log(PAYLOAD, "t") {
            """{"refresh_token":"abc123secret","max_tokens":5} Authorization: Bearer sk-live-1234567890 """ +
                "https://x.test/v1/models?key=AIzaSecret&alt=sse client_secret=shh&grant_type=refresh_token"
        }
        val message = DebugLog.entries.value.single().message
        listOf("abc123secret", "sk-live-1234567890", "AIzaSecret", "shh").forEach { assertFalse(it, message.contains(it)) }
        assertTrue(message.contains("\"max_tokens\":5"))
        assertTrue(message.contains("alt=sse"))
        assertTrue(message.contains("grant_type=refresh_token"))
        assertTrue(DebugLogRedactor.isSensitiveHeader("X-Api-Key"))
        assertFalse(DebugLogRedactor.isSensitiveHeader("Content-Type"))
    }

    @Test fun storedScopesDefaultToEverythingAndIgnoreUnknownValues() {
        assertEquals(DebugLogCategory.all, DebugLogCategory.fromStored(null))
        assertEquals(emptySet<DebugLogCategory>(), DebugLogCategory.fromStored(emptySet()))
        assertEquals(setOf(NETWORK, ERRORS), DebugLogCategory.fromStored(setOf("network", "errors", "retired")))
    }

    @Test fun interceptorLogsRequestsAndResponsesWithMaskedHeaders() {
        DebugLog.configure(true, DebugLogCategory.all)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"ok":true}"""))
            server.enqueue(MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json").setBody("""{"error":"bad"}"""))
            val client = OkHttpClient.Builder().addInterceptor(DebugLogInterceptor()).build()
            val url = server.url("/v1/chat")
            client.newCall(
                Request.Builder().url(url).header("Authorization", "Bearer top-secret-token")
                    .post("""{"prompt":"hi"}""".toRequestBody("application/json".toMediaType())).build()
            ).execute().use { assertEquals(200, it.code) }
            client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(500, it.code) }
        }
        val entries = DebugLog.entries.value
        assertEquals(2, entries.size)
        assertEquals(DebugLogLevel.INFO, entries[0].level)
        assertEquals(DebugLogLevel.ERROR, entries[1].level)
        assertTrue(entries[0].summary!!.startsWith("← 200 POST"))
        assertTrue(entries[1].summary!!.startsWith("← 500 GET"))
        val first = entries[0].message
        assertTrue(first.contains("""{"prompt":"hi"}"""))
        assertTrue(first.contains("""{"ok":true}"""))
        assertFalse(first.contains("top-secret-token"))
        assertTrue(first.contains("Authorization: ***"))
        assertTrue(entries[1].message.contains("""{"error":"bad"}"""))
    }

    @Test fun concurrentRequestsUpdateTheirOwnEntriesAndClearDoesNotRestoreThem() {
        DebugLog.configure(true, DebugLogCategory.all)
        val first = DebugLog.beginRequest("HTTP", "POST https://example.test/first") { "first body" }
        val second = DebugLog.beginRequest("HTTP", "POST https://example.test/second") { "second body" }
        DebugLog.finishRequest(second, "HTTP", "← 200 second", false) { "second response" }
        DebugLog.finishRequest(first, "HTTP", "← 404 first", true) { "first response" }
        val entries = DebugLog.entries.value
        assertEquals(2, entries.size)
        assertEquals(DebugLogLevel.ERROR, entries[0].level)
        assertTrue(entries[0].message.contains("first response"))
        assertFalse(entries[0].message.contains("second response"))
        assertTrue(entries[1].message.contains("second response"))
        DebugLog.clear()
        DebugLog.finishRequest(first, "HTTP", "← 200 first", false)
        assertTrue(DebugLog.entries.value.isEmpty())
    }

    @Test fun requestPayloadRespectsScopeAndErrorsOnlyRecordsFailures() {
        DebugLog.configure(true, setOf(NETWORK))
        val request = DebugLog.beginRequest("HTTP", "GET https://example.test") { "private body" }
        DebugLog.finishRequest(request, "HTTP", "← 200 GET", false) { "private response" }
        assertFalse(DebugLog.entries.value.single().message.contains("private"))
        DebugLog.clear()
        DebugLog.configure(true, setOf(ERRORS))
        assertNull(DebugLog.beginRequest("HTTP", "GET https://example.test"))
        DebugLog.finishRequest(null, "HTTP", "← 200 GET", false)
        assertTrue(DebugLog.entries.value.isEmpty())
        DebugLog.finishRequest(null, "HTTP", "← 404 GET", true)
        assertEquals(DebugLogLevel.ERROR, DebugLog.entries.value.single().level)
    }

    @Test fun ktorGroupsRequestAndResponseIntoOneEntry() = runBlocking {
        DebugLog.configure(true, DebugLogCategory.all)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            HttpClient(CIO) { install(DebugLogKtorPlugin) }.use { client ->
                assertEquals(404, client.get("http://127.0.0.1:${server.address.port}/").status.value)
            }
            val entry = DebugLog.entries.value.single()
            assertEquals(DebugLogLevel.ERROR, entry.level)
            assertTrue(entry.message.contains("→ GET"))
            assertTrue(entry.message.contains("← 404 GET"))
        } finally {
            server.stop(0)
        }
    }

    @Test fun interceptorDoesNothingWhileDebugIsOff() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("plain"))
            val client = OkHttpClient.Builder().addInterceptor(DebugLogInterceptor()).build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { assertEquals("plain", it.body.string()) }
        }
        assertTrue(DebugLog.entries.value.isEmpty())
    }
}
