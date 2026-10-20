package org.starfall.generative.integrations.remote

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPairGenEdDSA
import com.jcraft.jsch.SignatureEdDSA
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * Ed25519 for JSch on Android.
 *
 * JSch's built-in `ssh-ed25519` needs the JDK 15+ EdDSA provider (or Bouncy Castle, which clashes
 * with Android's stripped platform copy). Neither exists here, so JSch silently dropped `ssh-ed25519`
 * from its host key proposal and servers that only offer an Ed25519 host key failed with
 * "Algorithm negotiation fail: server_host_key". These classes plug a pure-Java implementation
 * (net.i2p.crypto:eddsa) into JSch's own extension points.
 */
internal object AndroidEdDSA {
    /** Registers the Ed25519 implementations with JSch. Idempotent and cheap. */
    fun install() {
        JSch.setConfig("ssh-ed25519", AndroidSignatureEd25519::class.java.name)
        JSch.setConfig("keypairgen.eddsa", AndroidKeyPairGenEdDSA::class.java.name)
        JSch.setConfig("keypairgen_fromprivate.eddsa", AndroidKeyPairGenEdDSA::class.java.name)
    }

    val spec by lazy { EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519) }
}

class AndroidSignatureEd25519 : SignatureEdDSA {
    private val data = ByteArrayOutputStream()
    private var publicKey: ByteArray? = null
    private var seed: ByteArray? = null

    override fun init() {
        // JSch probes availability by calling init(); fail here if the curve is missing.
        checkNotNull(AndroidEdDSA.spec) { "Ed25519 curve is not available" }
        data.reset()
    }

    override fun setPubKey(y: ByteArray) {
        require(y.size == KEY_SIZE) { "Invalid Ed25519 public key length" }
        publicKey = y.copyOf()
    }

    override fun setPrvKey(bytes: ByteArray) {
        require(bytes.size >= KEY_SIZE) { "Invalid Ed25519 private key length" }
        seed = bytes.copyOf(KEY_SIZE)
    }

    override fun update(foo: ByteArray) {
        data.write(foo, 0, foo.size)
    }

    override fun sign(): ByteArray {
        val spec = AndroidEdDSA.spec
        val key = EdDSAPrivateKey(EdDSAPrivateKeySpec(checkNotNull(seed) { "Private key not set" }, spec))
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initSign(key)
        engine.update(data.toByteArray())
        data.reset()
        return engine.sign()
    }

    /** [sig] is the SSH signature blob: string "ssh-ed25519", string <raw 64-byte signature>. */
    override fun verify(sig: ByteArray): Boolean {
        val message = data.toByteArray()
        data.reset()
        val buffer = ByteBuffer.wrap(sig)
        val algorithm = buffer.readString() ?: return false
        if (String(algorithm, Charsets.UTF_8) != "ssh-ed25519") return false
        val signature = buffer.readString() ?: return false
        return runCatching {
            val spec = AndroidEdDSA.spec
            val key = EdDSAPublicKey(EdDSAPublicKeySpec(checkNotNull(publicKey) { "Public key not set" }, spec))
            val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            engine.initVerify(key)
            engine.update(message)
            engine.verify(signature)
        }.getOrDefault(false)
    }

    private fun ByteBuffer.readString(): ByteArray? {
        if (remaining() < 4) return null
        val length = int
        if (length < 0 || length > remaining()) return null
        return ByteArray(length).also { get(it) }
    }

    private companion object {
        const val KEY_SIZE = 32
    }
}

class AndroidKeyPairGenEdDSA : KeyPairGenEdDSA {
    private var pub = ByteArray(0)
    private var prv = ByteArray(0)

    override fun init(name: String, keylen: Int) {
        val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
        init(name, seed)
    }

    override fun init(name: String, prv: ByteArray) {
        if (name != "Ed25519") throw NoSuchAlgorithmException("invalid curve $name")
        val seed = prv.copyOf(32)
        this.prv = seed
        this.pub = EdDSAPrivateKeySpec(seed, AndroidEdDSA.spec).a.toByteArray()
    }

    override fun getPub(): ByteArray = pub

    override fun getPrv(): ByteArray = prv
}
