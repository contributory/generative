package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.providers.shared.logic.mergeMcpLink
import org.starfall.generative.shared.model.McpAuthorization
import org.starfall.generative.shared.model.McpAuthMethod
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.McpProtocol

class McpLinkImportTest {
    @Test fun newConnectorUsesDefaultProtocol() {
        val server = mergeMcpLink(null, "Example", "https://example.com/mcp")
        assertEquals("Example", server.name)
        assertEquals("https://example.com/mcp", server.url)
        assertEquals(McpProtocol.STREAMABLE_HTTP, server.protocol)
        assertTrue(server.id.isNotBlank())
    }

    @Test fun sameEndpointKeepsExistingIdentityAndCredentials() {
        val original = McpInfo(id = "existing", name = "Example", url = "https://example.com/mcp",
            auth = McpAuthorization(method = McpAuthMethod.BEARER_TOKEN, value = "secret"),
            headers = mapOf("X-Token" to "secret"), icon = "logo", sortOrder = 3)
        val updated = mergeMcpLink(original, "example", "https://example.com/mcp")
        assertEquals(original.id, updated.id)
        assertEquals("example", updated.name)
        assertEquals(original.auth, updated.auth)
        assertEquals(original.headers, updated.headers)
        assertEquals(original.sortOrder, updated.sortOrder)
        assertEquals(original.icon, updated.icon)
    }

    @Test fun changedEndpointDoesNotReuseSecretsOrTools() {
        val original = McpInfo(id = "existing", name = "Example", protocol = McpProtocol.SSE,
            url = "https://old.example.com/mcp",
            auth = McpAuthorization(method = McpAuthMethod.BEARER_TOKEN, value = "secret"),
            headers = mapOf("Authorization" to "secret"), cachedTools = emptyList())
        val updated = mergeMcpLink(original, "Example", "https://new.example.com/mcp")
        assertEquals("existing", updated.id)
        assertEquals(McpProtocol.SSE, updated.protocol)
        assertNull(updated.headers)
        assertNull(updated.cachedTools)
        assertEquals(McpAuthorization(), updated.auth)
    }
}
