package org.starfall.multigateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.multigateway.shared.model.ToolDefinition
import org.starfall.multigateway.shared.model.withUniqueWireNames
import org.starfall.multigateway.integrations.mcp.mcpToolWireName

class McpToolNameTest {
    @Test fun wireNameUsesReadableServerAndToolNames() {
        assertEquals("Context7_get_docs", mcpToolWireName("Context7", "get_docs"))
        assertEquals("My_Server_search_web", mcpToolWireName("My Server", "search/web"))
        assertFalse(mcpToolWireName("Context7", "get_docs").startsWith("mcp_"))
    }

    @Test fun longNamesStayWithinProviderFunctionNameLimit() {
        val name = mcpToolWireName("Server".repeat(20), "tool".repeat(20))

        assertTrue(name.length <= 64)
        assertTrue(name.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test fun duplicateReadableNamesAreDisambiguatedOnlyWhenNeeded() {
        val definitions = listOf(
            ToolDefinition("Context7_get_docs", "", kotlinx.serialization.json.buildJsonObject {}, "one", "get_docs"),
            ToolDefinition("Context7_get_docs", "", kotlinx.serialization.json.buildJsonObject {}, "two", "get_docs")
        ).withUniqueWireNames()

        assertEquals("Context7_get_docs", definitions[0].name)
        assertEquals("Context7_get_docs_2", definitions[1].name)
        assertTrue(definitions.map { it.name }.toSet().size == definitions.size)
    }

    @Test fun wireNamesNeverContainInternalIds() {
        val long = mcpToolWireName("Server".repeat(20), "tool".repeat(20))
        assertEquals(long, mcpToolWireName("Server".repeat(20), "tool".repeat(20)))
        val machines = listOf(
            ToolDefinition("machine_Home_execute", "", kotlinx.serialization.json.buildJsonObject {}, remoteMachineId = "uuid-1", originalName = "execute"),
            ToolDefinition("machine_Home_execute", "", kotlinx.serialization.json.buildJsonObject {}, remoteMachineId = "uuid-2", originalName = "execute")
        ).withUniqueWireNames()
        assertEquals(listOf("machine_Home_execute", "machine_Home_execute_2"), machines.map { it.name })
        assertTrue(machines.none { it.name.contains("uuid") })
    }
}
