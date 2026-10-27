package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.terminal.terminalBrowserUrl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = LibraryTestApplication::class)
class RemoteTerminalUrlTest {
    @Test fun selectionOpensWebLinksAndStripsSurroundingPunctuation() {
        assertEquals("https://example.com/help?q=1", terminalBrowserUrl(" https://example.com/help?q=1 "))
        assertEquals("https://example.com", terminalBrowserUrl("https://example.com)."))
        assertEquals("https://www.example.com", terminalBrowserUrl("www.example.com"))
        assertEquals("http://localhost:8080", terminalBrowserUrl("http://localhost:8080"))
    }

    @Test fun selectionRejectsTerminalCommandsAndNonWebSchemes() {
        assertNull(terminalBrowserUrl("ls -la"))
        assertNull(terminalBrowserUrl("javascript:alert(1)"))
        assertNull(terminalBrowserUrl("file:///sdcard/file.txt"))
        assertNull(terminalBrowserUrl(""))
        assertNull(terminalBrowserUrl("https://example.com has spaces"))
    }
}
