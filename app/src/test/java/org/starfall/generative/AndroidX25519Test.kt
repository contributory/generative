package org.starfall.generative

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.integrations.remote.AndroidSshAlgorithms
import org.starfall.generative.integrations.remote.X25519

/** RFC 7748 section 6.1 Diffie-Hellman vectors. */
class AndroidX25519Test {
    private val alicePrivate = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
    private val alicePublic = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
    private val bobPrivate = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
    private val bobPublic = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
    private val shared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")

    @Test
    fun derivesPublicKeysFromPrivateKeys() {
        assertArrayEquals(alicePublic, X25519.scalarMult(alicePrivate, X25519.BASE_POINT))
        assertArrayEquals(bobPublic, X25519.scalarMult(bobPrivate, X25519.BASE_POINT))
    }

    @Test
    fun bothSidesAgreeOnTheSharedSecret() {
        assertArrayEquals(shared, X25519.scalarMult(alicePrivate, bobPublic))
        assertArrayEquals(shared, X25519.scalarMult(bobPrivate, alicePublic))
    }

    @Test
    fun installAddsLegacyFallbacksOnlyOnceAndAtTheEnd() {
        AndroidSshAlgorithms.install()
        AndroidSshAlgorithms.install()
        val hostKeys = com.jcraft.jsch.JSch.getConfig("server_host_key").split(',')
        assertEquals(1, hostKeys.count { it == "ssh-rsa" })
        assertEquals("ssh-rsa", hostKeys.last())
        assertTrue("ssh-ed25519-cert-v01@openssh.com" in hostKeys)
        assertEquals(AndroidXdhName, com.jcraft.jsch.JSch.getConfig("xdh"))
    }

    private val AndroidXdhName = org.starfall.generative.integrations.remote.AndroidXDH::class.java.name

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
