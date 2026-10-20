package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Test
import org.starfall.generative.integrations.remote.ExecutableBinary
import org.starfall.generative.integrations.remote.resolveExecutableCommand

class ExecutableBinaryResolveTest {
    private val binaries = listOf(
        ExecutableBinary("1", "cloudflared", "/data/bin/1-cloudflared"),
        ExecutableBinary("2", "ncat", "/data/bin/2-ncat")
    )

    @Test
    fun replacesWholeWordNamesWithAbsolutePaths() {
        assertEquals(
            "/data/bin/1-cloudflared access ssh --hostname %h",
            resolveExecutableCommand("cloudflared access ssh --hostname %h", binaries)
        )
        assertEquals(
            "/data/bin/2-ncat %h %p",
            resolveExecutableCommand("ncat %h %p", binaries)
        )
    }

    @Test
    fun leavesUnrelatedTextAndPartialWordsAlone() {
        assertEquals("my-ncat-wrapper %h", resolveExecutableCommand("my-ncat-wrapper %h", binaries))
        assertEquals("ssh %h", resolveExecutableCommand("ssh %h", emptyList()))
    }
}

class ExecutableLaunchCommandTest {
    private fun elf(bits: Int) = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), bits.toByte())
    private val allExist: (String) -> Boolean = { true }

    @Test
    fun runsDirectlyWhenExecutionIsNotBlocked() {
        assertEquals("/p/bin", org.starfall.generative.integrations.remote.launchCommandFor("/p/bin", elf(2), false, allExist))
    }

    @Test
    fun elfGoesThroughTheMatchingLinker() {
        assertEquals("/system/bin/linker64 /p/bin", org.starfall.generative.integrations.remote.launchCommandFor("/p/bin", elf(2), true, allExist))
        assertEquals("/system/bin/linker /p/bin", org.starfall.generative.integrations.remote.launchCommandFor("/p/bin", elf(1), true, allExist))
        assertEquals("/p/bin", org.starfall.generative.integrations.remote.launchCommandFor("/p/bin", elf(2), true) { false })
    }

    @Test
    fun scriptsUseTheirInterpreterOrTheSystemShell() {
        val sh = "#!/system/bin/sh\necho hi".toByteArray()
        assertEquals("/system/bin/sh /p/s", org.starfall.generative.integrations.remote.launchCommandFor("/p/s", sh, true, allExist))
        val env = "#!/usr/bin/env python3\n".toByteArray()
        assertEquals("/system/bin/sh /p/s", org.starfall.generative.integrations.remote.launchCommandFor("/p/s", env, true) { it == "/system/bin/sh" })
    }
}
