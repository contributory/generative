package org.starfall.multigateway

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.integrations.languagemodels.discoveredModel
import org.starfall.multigateway.ui.chat.*

class ContextWindowTest {
    @Test fun discoveryKeepsApiMetadataForCatalogDisplay() {
        val metadata = Json.parseToJsonElement("""{"id":"m","display_name":"Model","owned_by":"vendor","created":1700000000,"description":"A model description","limits":{"context_window":32000},"supported_endpoints":["chat","speech"]}""").jsonObject
        val discovered = discoveredModel("m", metadata)
        assertEquals(metadata, discovered.metadata)
        assertEquals("Model", discovered.displayName)
        assertEquals(32000, discovered.contextWindowTokens)
        assertEquals(listOf("chat", "speech"), discovered.metadata["supported_endpoints"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
    @Test fun editedContextWindowBelowDefaultIsEncodedAndSurvivesRoundTrip() {
        val json = Json { encodeDefaults = true }
        for (limit in listOf(1000, 32000, 127999)) {
            val config = ModelConfiguration(contextWindowTokens = limit)
            val stored = json.parseToJsonElement(json.encodeToString(config)).jsonObject
            assertEquals(limit, stored.getValue("contextWindowTokens").jsonPrimitive.int)
            assertEquals(limit, json.decodeFromString<ModelConfiguration>(stored.toString()).contextWindowTokens)
        }
    }

    @Test fun apiContextWindowIsKeptAsIsAndDefaultOnlyWhenMissing() {
        val json = Json { encodeDefaults = true }
        for (limit in listOf(32000, 127999, 128001, 262144)) {
            val config = DiscoveredModel("model", contextWindowTokens = limit).configuration()
            assertEquals(limit, config.contextWindowTokens)
            assertEquals(limit, json.decodeFromString<ModelConfiguration>(json.encodeToString(config)).contextWindowTokens)
        }
        assertEquals(DEFAULT_CONTEXT_WINDOW_TOKENS, DiscoveredModel("model").configuration().contextWindowTokens)
        assertFalse(json.parseToJsonElement(json.encodeToString(DiscoveredModel("model").configuration())).jsonObject.containsKey("contextWindowTokens"))
    }

    @Test fun templateNormalizationCollapsesOnlyContextWindowsAtOrBelowDefault() {
        for (limit in listOf(32000, 127999, 128000))
            assertEquals(DEFAULT_CONTEXT_WINDOW_TOKENS, ModelConfiguration(contextWindowTokens = limit).normalizedForTemplate().contextWindowTokens)
        for (limit in listOf(128001, 262144))
            assertEquals(limit, ModelConfiguration(contextWindowTokens = limit).normalizedForTemplate().contextWindowTokens)
    }

    @Test fun rawApiModelJsonSurvivesConfigurationRoundTrip() {
        val raw = Json.parseToJsonElement("""{"id":"m","context_window":32000,"vendor":{"enabled":true,"options":[1,null,"x"]}}""").jsonObject
        val configuration = discoveredModel("m", raw).configuration()
        val decoded = Json.decodeFromString<ModelConfiguration>(Json.encodeToString(configuration))
        assertEquals(raw, decoded.modelJson)
        assertEquals(32000, decoded.contextWindowTokens)
        assertEquals(JsonObject(emptyMap()), ModelConfiguration().modelJson)
        assertEquals(JsonObject(emptyMap()), Json.decodeFromString<ModelConfiguration>("{}").modelJson)
    }

    private fun message(id: String, text: String) = StoredMessage(id, ChatRole.USER, listOf(MessageVersion(content = text)))

    @Test fun discoveryReadsHelixAndGoogleLimitsAndRejectsInvalidValues() {
        val helix = discoveredModel("qwen", Json.parseToJsonElement("""{"architecture":{"context_window":262144},"display_name":"Qwen"}""").jsonObject)
        assertEquals(262144, helix.configuration().contextWindowTokens)
        assertEquals("Qwen", helix.displayName)
        val google = discoveredModel("gemini", Json.parseToJsonElement("""{"inputTokenLimit":1048576}""").jsonObject)
        assertEquals(1048576, google.contextWindowTokens)
        listOf("{}", """{"context_window":0}""", """{"context_window":-1}""", """{"context_window":999999999999}""").forEach {
            assertEquals(128000, discoveredModel("model", Json.parseToJsonElement(it).jsonObject).configuration().contextWindowTokens)
        }
    }

    @Test fun olderSavedModelsDefaultTo128kWithoutLosingOtherSettings() {
        val config = Json.decodeFromString<ModelConfiguration>("""{"temperature":0.4,"displayName":"Existing"}""")
        assertEquals(128000, config.contextWindowTokens)
        assertEquals("Existing", config.displayName)
        assertEquals(0.4, config.temperature!!, 0.001)
    }

    @Test fun suggestionsStartAt90PercentAndAutomationAt99Percent() {
        assertFalse(ContextWindowStatus(899, 1000).shouldSuggestSummary)
        assertTrue(ContextWindowStatus(900, 1000).shouldSuggestSummary)
        assertFalse(ContextWindowStatus(989, 1000).shouldAutoSummarize)
        assertTrue(ContextWindowStatus(990, 1000).shouldAutoSummarize)
        assertTrue(ContextWindowStatus(1001, 1000).shouldAutoSummarize)
    }

    @Test fun summarizedHistoryAndInactiveVersionsDoNotConsumeTheNewContext() {
        val old = message("old", "a".repeat(5000))
        val tail = message("tail", "Latest")
        val conversation = Conversation("chat", "Test", 0, 0, listOf(old, tail))
        val original = contextWindowStatus(conversation, "System", ModelConfiguration())
        SummaryRole.entries.forEach { role ->
            val compressed = conversation.copy(summary = ConversationSummary("s", "Brief summary", old.id, role))
            assertTrue(contextWindowStatus(compressed, "System", ModelConfiguration()).estimatedTokens < original.estimatedTokens)
        }
        val versions = tail.copy(versions = listOf(MessageVersion(content = "b".repeat(9000)), tail.activeVersion), activeVersionIndex = 1)
        assertEquals(estimateMessageTokens(tail, false), estimateMessageTokens(versions, false))
    }

    @Test fun longTurnsAreSplitIntoBoundedChunksWithoutLosingText() {
        val text = "中文 ".repeat(1500)
        val chunks = chunkSummaryMessages(listOf(message("long", text)), 100)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { chunk -> chunk.sumOf { estimateMessageTokens(it, true) } <= 100 })
        assertEquals(text, chunks.flatten().joinToString("") { it.content })
    }
}
