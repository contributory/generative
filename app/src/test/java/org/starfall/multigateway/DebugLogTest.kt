package org.starfall.multigateway

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.starfall.multigateway.shared.logging.DebugLog
import org.starfall.multigateway.shared.logging.DebugLogInterceptor
import org.starfall.multigateway.shared.logging.DebugLogRedactor
import org.starfall.multigateway.shared.model.logging.DebugLogCategory
import org.starfall.multigateway.shared.model.logging.DebugLogCategory.*
import org.starfall.multigateway.shared.model.logging.DebugLogLevel

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
        assertTrue(entries.any { it.category == NETWORK && it.message.startsWith("→ POST") })
        assertTrue(entries.any { it.category == NETWORK && it.message.startsWith("← 200") })
        assertTrue(entries.any { it.category == ERRORS && it.message.startsWith("HTTP 500") })
        val payloads = entries.filter { it.category == PAYLOAD }.joinToString("\n") { it.message }
        assertTrue(payloads.contains("""{"prompt":"hi"}"""))
        assertTrue(payloads.contains("""{"ok":true}"""))
        assertFalse(payloads.contains("top-secret-token"))
        assertTrue(payloads.contains("Authorization: ***"))
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
