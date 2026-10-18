package org.starfall.multigateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.starfall.multigateway.features.appdata.logic.parseFullAppDataExport

class LegacyAppDataImportTest {
    @Test fun importsLegacyProvidersBackupAndItsGroups() {
        val result = parseFullAppDataExport("""{
            "formatVersion":1,"type":"providers",
            "items":[{"id":"p","name":"Provider","type":"openai","baseUrl":"https://example.com"}],
            "groups":[{"id":"g","name":"Folder","sortOrder":1}]
        }""")
        assertEquals("p", result.providers?.single()?.id)
        assertEquals("g", result.providerGroups?.single()?.id)
        assertNull(result.mcpServers)
        assertNull(result.conversations)
    }

    @Test fun importsEachLegacyCategoryThroughTheSingleImportEntryPoint() {
        val mcp = parseFullAppDataExport("""{"formatVersion":1,"type":"mcp_servers","items":[{"id":"m","name":"MCP","url":"https://example.com/mcp"}]}""")
        val speech = parseFullAppDataExport("""{"formatVersion":1,"type":"speech_services","items":[{"id":"s","name":"Speech"}]}""")
        val chats = parseFullAppDataExport("""{"formatVersion":1,"type":"conversations","items":[{"id":"c","title":"Chat","created_at":1,"updated_at":2}]}""")
        assertEquals("m", mcp.mcpServers?.single()?.id)
        assertEquals("s", speech.speechServices?.single()?.id)
        assertEquals("c", chats.conversations?.single()?.id)
        assertNull(mcp.providers)
        assertNull(speech.mcpServers)
        assertNull(chats.speechServices)
    }

    @Test fun acceptsIntermediateDataKeyAndAliases() {
        val result = parseFullAppDataExport("""{"formatVersion":1,"type":"mcp-servers","data":[{"id":"m","name":"MCP","url":"https://example.com/mcp"}]}""")
        assertEquals("m", result.mcpServers?.single()?.id)
    }

    @Test fun acceptsLegacyEmptyArrayWhenFileNameIdentifiesItsCategory() {
        val result = parseFullAppDataExport("[]", "multigateway-speech-services.json")
        assertEquals(emptyList<Any>(), result.speechServices)
    }

    @Test fun importingPartialBackupDoesNotInventOtherCategories() {
        val result = parseFullAppDataExport("""{"formatVersion":1,"type":"providers","items":[]}""")
        assertEquals(emptyList<Any>(), result.providers)
        assertNull(result.mcpServers)
        assertNull(result.speechServices)
        assertNull(result.conversations)
    }
}
