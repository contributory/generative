package org.starfall.generative

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.features.appdata.logic.buildJsonExport
import org.starfall.generative.features.appdata.logic.parseFullAppDataExport
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup

class JsonDataExporterTest {
    @Test fun exportsAllCollectionsInOneVersionedBackup() {
        val provider = ModelProvider("provider-1", "Test provider", ModelApiType.OPENAI, baseUrl = "https://example.com")
        val mcp = McpInfo("mcp-1", "Test MCP", url = "https://example.com/mcp")
        val speech = SpeechService("speech-1", "Test speech", apiKey = "speech-key")
        val chat = Conversation("chat-1", "Test chat", createdAt = 1, updatedAt = 2)
        val groups = listOf(ProviderGroup("group-1", "Work", 1))

        val json = Json.parseToJsonElement(buildJsonExport(listOf(provider), listOf(mcp), listOf(speech), listOf(chat), groups)).jsonObject
        assertEquals(1, json.getValue("formatVersion").jsonPrimitive.int)
        assertEquals("full_backup", json.getValue("type").jsonPrimitive.content)
        assertTrue(json.getValue("providers").toString().contains("provider-1"))
        assertTrue(json.getValue("mcpServers").toString().contains("mcp-1"))
        assertTrue(json.getValue("speechServices").toString().contains("speech-1"))
        assertTrue(json.getValue("conversations").toString().contains("chat-1"))
        assertTrue(json.getValue("providerGroups").toString().contains("group-1"))

        val restored = parseFullAppDataExport(json.toString())
        assertEquals(provider, restored.providers?.single())
        assertEquals(mcp, restored.mcpServers?.single())
        assertEquals(speech, restored.speechServices?.single())
        assertEquals(chat, restored.conversations?.single())
        assertEquals(groups, restored.providerGroups)
    }

    @Test fun legacyProviderExportsCanBeReadWithoutGroupRecords() {
        val imported = parseFullAppDataExport("""{
            "formatVersion":1,"type":"providers",
            "items":[{"id":"p","name":"Provider","type":"openai","baseUrl":"https://example.com","groupId":"g"}]
        }""")
        assertEquals("p", imported.providers?.single()?.id)
        assertNull(imported.providerGroups)
        assertNull(imported.mcpServers)
    }
}
