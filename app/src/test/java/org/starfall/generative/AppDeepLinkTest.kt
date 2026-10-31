package org.starfall.generative

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.navigation.AppDeepLink
import org.starfall.generative.navigation.parseAppDeepLink

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDeepLinkTest {
    @Test fun providersLinkOpensOnlyTheProvidersScreen() {
        assertEquals(AppDeepLink.Providers, parseAppDeepLink(Uri.parse("generative://providers")))
    }

    @Test fun mcpLinksDecodeNestedUrls() {
        assertEquals(AppDeepLink.Mcp(), parseAppDeepLink(Uri.parse("generative://mcp")))
        assertEquals(AppDeepLink.Mcp("Connector", "https://example.com/mcp?token=a&mode=b"),
            parseAppDeepLink(Uri.Builder().scheme("generative").authority("mcp")
                .appendQueryParameter("name", "Connector")
                .appendQueryParameter("url", "https://example.com/mcp?token=a&mode=b").build()))
        assertEquals(AppDeepLink.Mcp("abc", "abc"),
            parseAppDeepLink(Uri.parse("generative://mcp?name=abc&url=abc")))
    }

    @Test fun chatLinksCreateNewNormalOrTemporaryMessages() {
        assertEquals(AppDeepLink.Chat("hello", false),
            parseAppDeepLink(Uri.parse("generative://chat?text=hello")))
        assertEquals(AppDeepLink.Chat("hello", false),
            parseAppDeepLink(Uri.parse("generative://chat?text=hello&temp=false")))
        assertEquals(AppDeepLink.Chat("Hello & goodbye?", true),
            parseAppDeepLink(Uri.Builder().scheme("generative").authority("chat")
                .appendQueryParameter("text", "Hello & goodbye?")
                .appendQueryParameter("temp", "true").build()))
    }

    @Test fun invalidLinksDoNotCreateActions() {
        assertNull(parseAppDeepLink(Uri.parse("generative://provider?name=Existing")))
        assertNull(parseAppDeepLink(Uri.parse("mg://mcp?name=a&url=b")))
        listOf(
            "generative://mcp?name=a", "generative://mcp?url=abc",
            "generative://mcp?name=&url=abc",
            "generative://providers/other", "generative://chat?text=",
            "generative://chat", "generative://chat?text=hello&temp=maybe"
        ).forEach { value ->
            assertTrue(value, runCatching { parseAppDeepLink(Uri.parse(value)) }.exceptionOrNull()
                is IllegalArgumentException)
        }
    }
}
