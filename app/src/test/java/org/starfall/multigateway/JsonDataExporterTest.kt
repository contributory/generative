package org.starfall.multigateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.auth.AuthMethod
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.SpeechService
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.provider.ProviderGroup
import org.starfall.multigateway.features.appdata.model.JsonExportKind
import org.starfall.multigateway.features.appdata.service.buildJsonExport
import org.starfall.multigateway.features.appdata.service.parseConversationsExport
import org.starfall.multigateway.features.appdata.service.parseMcpExport
import org.starfall.multigateway.features.appdata.service.parseProvidersExport
import org.starfall.multigateway.features.appdata.service.parseSpeechServicesExport

class JsonDataExporterTest {
    @Test fun exportsEachCollectionAsVersionedJsonWithItsType() {
        val provider = ModelProvider(
            id = "provider-1",
            name = "Test provider",
            type = ModelApiType.OPENAI,
            auth = Authorization(AuthMethod.PLATFORM_DEFAULT, value = "api-key"),
            baseUrl = "https://example.com"
        )
        val mcp = McpInfo(id = "mcp-1", name = "Test MCP", url = "https://example.com/mcp")
        val speech = SpeechService(id = "speech-1", name = "Test speech", apiKey = "speech-key")
        val chat = Conversation(id = "chat-1", title = "Test chat", createdAt = 1, updatedAt = 2)

        val outputs = JsonExportKind.entries.map { kind ->
            Json.parseToJsonElement(
                buildJsonExport(kind, listOf(provider), listOf(mcp), listOf(speech), listOf(chat), listOf())
            ).jsonObject
        }

        assertEquals(JsonExportKind.entries.size, outputs.size)
        outputs.forEachIndexed { index, output ->
            assertEquals(1, output.getValue("formatVersion").jsonPrimitive.int)
            assertEquals(JsonExportKind.entries[index].name.lowercase(), output.getValue("type").jsonPrimitive.content)
            assertTrue(output.getValue("items").toString().isNotBlank())
        }
        assertTrue(outputs[0].getValue("items").toString().contains("api-key"))
        assertTrue(outputs[1].getValue("items").toString().contains("Test MCP"))
        assertTrue(outputs[2].getValue("items").toString().contains("speech-key"))
        assertTrue(outputs[3].getValue("items").toString().contains("Test chat"))
    }

    @Test fun exportedCollectionsCanBeParsedBackIntoTheirOriginalModels() {
        val provider = ModelProvider("p", "Provider", ModelApiType.OPENAI, baseUrl = "https://example.com", groupId = "g", sortOrder = 2)
        val groups = listOf(ProviderGroup("g", "Work", 1, "folder.png"), ProviderGroup("empty", "Empty folder", 3))
        val mcp = McpInfo("m", "MCP", url = "https://example.com/mcp")
        val speech = SpeechService("s", "Speech")
        val chat = Conversation("c", "Chat", createdAt = 10, updatedAt = 20)

        val imported = parseProvidersExport(buildJsonExport(
            JsonExportKind.PROVIDERS, listOf(provider), listOf(), listOf(), listOf(), groups
        ))
        assertEquals(provider, imported.providers.single())
        assertEquals(groups, imported.groups)
        assertEquals(mcp, parseMcpExport(buildJsonExport(
            JsonExportKind.MCP_SERVERS, listOf(), listOf(mcp), listOf(), listOf()
        )).single())
        assertEquals(speech, parseSpeechServicesExport(buildJsonExport(
            JsonExportKind.SPEECH_SERVICES, listOf(), listOf(), listOf(speech), listOf()
        )).single())
        assertEquals(chat, parseConversationsExport(buildJsonExport(
            JsonExportKind.CONVERSATIONS, listOf(), listOf(), listOf(), listOf(chat)
        )).single())
    }

    @Test fun legacyProviderExportsCanBeReadWithoutGroupRecords() {
        val imported = parseProvidersExport("""{
            "formatVersion": 1,
            "type": "providers",
            "items": [{"id":"p","name":"Provider","type":"openai","baseUrl":"https://example.com","groupId":"g"}]
        }""")

        assertEquals("g", imported.providers.single().groupId)
        assertNull(imported.groups)
        val current = parseProvidersExport(buildJsonExport(
            JsonExportKind.PROVIDERS, emptyList(), emptyList(), emptyList(), emptyList()
        ))
        assertEquals(emptyList<ProviderGroup>(), current.groups)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun invalidGroupRecordsFailParsingRatherThanSilentlyDroppingGroups() {
        parseProvidersExport("""{"formatVersion":1,"type":"providers","items":[],"groups":[{"id":"g"}]}""")
    }
}
