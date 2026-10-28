package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.providers.editor.ui.defaultEndpoint
import org.starfall.generative.features.providers.editor.ui.validEndpoint
import org.starfall.generative.shared.model.api.ModelApiType

class ProviderEndpointsDialogTest {
    @Test fun geminiVariablesAreOptionalAndCanBeUsedTogether() {
        assertTrue(validEndpoint("https://gateway.test/models/{model}:{method}", "generate"))
        assertTrue(validEndpoint("https://gateway.test/models/{model}:generateContent", "generate"))
        assertTrue(validEndpoint("https://gateway.test/fixed", "generate"))
        assertFalse(validEndpoint("not a URL", "generate"))
    }

    @Test fun labelsReflectBaseUrlAndCustomVideoRoot() {
        assertEquals("https://example.test/models/{model}:{method}",
            defaultEndpoint(ModelApiType.GOOGLE, "https://example.test", "generate", emptyMap()))
        assertEquals("https://example.test/gateway/models", defaultEndpoint(ModelApiType.ANTHROPIC, "https://example.test/gateway", "catalog", emptyMap()))
        assertEquals("https://example.test/v1/messages", defaultEndpoint(ModelApiType.ANTHROPIC, "https://example.test/v1", "messages", emptyMap()))
        assertEquals("https://example.test/api/tags",
            defaultEndpoint(ModelApiType.OLLAMA, "https://example.test/api", "catalog", emptyMap()))
        assertEquals("https://gateway.test/video/{id}/content",
            defaultEndpoint(ModelApiType.OPENAI, "https://example.test/v1", "videoContent", mapOf("video" to "https://gateway.test/video")))
    }
}
