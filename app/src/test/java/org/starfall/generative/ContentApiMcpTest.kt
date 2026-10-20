package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.shared.model.CONTENT_API_MCP_ENDPOINT
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.isContentApiName
import org.starfall.generative.shared.model.resolvedUrl

class ContentApiMcpTest {
    @Test
    fun recognizedNamesUseHiddenDefaultEndpoint() {
        listOf("Content API", "contentapi", "CONTENT-API").forEach { name ->
            assertEquals(CONTENT_API_MCP_ENDPOINT, McpInfo("id", name).resolvedUrl())
            assertTrue(name.isContentApiName())
        }
        assertNull(McpInfo("id", "Another server").resolvedUrl())
    }

    @Test
    fun explicitUrlAlwaysWins() {
        assertEquals(
            "https://example.test/custom",
            McpInfo("id", "Content API", url = " https://example.test/custom ").resolvedUrl()
        )
    }
}
