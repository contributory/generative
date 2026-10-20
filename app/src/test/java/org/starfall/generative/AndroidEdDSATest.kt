package org.starfall.generative

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.integrations.remote.AndroidKeyPairGenEdDSA
import org.starfall.generative.integrations.remote.AndroidSignatureEd25519
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** RFC 8032 section 7.1, TEST 1 (empty message). */
class AndroidEdDSATest {
    private val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    private val pub = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
    private val sig = hex(
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
    )

    @Test
    fun derivesThePublicKeyFromThePrivateSeed() {
        val gen = AndroidKeyPairGenEdDSA().apply { init("Ed25519", seed) }
        assertArrayEquals(pub, gen.pub)
        assertArrayEquals(seed, gen.prv)
    }

    @Test
    fun signsLikeTheReferenceVector() {
        val signer = AndroidSignatureEd25519().apply { init(); setPrvKey(seed) }
        assertArrayEquals(sig, signer.sign())
    }

    @Test
    fun verifiesAnSshSignatureBlob() {
        fun verifier(blob: ByteArray) = AndroidSignatureEd25519().apply { init(); setPubKey(pub) }.verify(blob)
        assertTrue(verifier(sshBlob("ssh-ed25519", sig)))
        assertFalse(verifier(sshBlob("ssh-rsa", sig)))
        assertFalse(verifier(sshBlob("ssh-ed25519", sig.copyOf().also { it[0] = (it[0] + 1).toByte() })))
    }

    private fun sshBlob(algorithm: String, signature: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        listOf(algorithm.toByteArray(), signature).forEach {
            out.write(ByteBuffer.allocate(4).putInt(it.size).array())
            out.write(it)
        }
        return out.toByteArray()
    }

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
