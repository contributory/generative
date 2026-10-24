package org.starfall.generative.integrations.remote

import com.jcraft.jsch.JSch
import com.jcraft.jsch.XDH
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * Registers every SSH algorithm JSch can use on Android but does not get from the platform.
 *
 * - Ed25519 signatures / keys (host keys, user keys and their OpenSSH certificates): [AndroidEdDSA].
 * - X25519 key exchange (`curve25519-sha256`): the JDK 11+ `XDH` provider is missing before Android 13,
 *   so [AndroidXDH] falls back to a pure-Java implementation.
 * - Legacy algorithms that JSch 2.x switched off by default (`ssh-rsa`/SHA-1 host and user keys,
 *   SHA-1 key exchange, CBC ciphers). They are appended at the very end of each proposal, so a modern
 *   server never negotiates them and only servers that offer nothing better can select them.
 *
 * Not available on Android and therefore not registered: Ed448/X448 and the post-quantum key
 * exchanges (they need JDK 15+/24+ or Bouncy Castle, which clashes with Android's platform copy).
 */
internal object AndroidSshAlgorithms {
    private val legacy = mapOf(
        "server_host_key" to listOf("ssh-rsa"),
        "PubkeyAcceptedAlgorithms" to listOf("ssh-rsa"),
        "kex" to listOf("diffie-hellman-group14-sha1", "diffie-hellman-group-exchange-sha1"),
        "cipher.s2c" to listOf("aes256-cbc", "aes192-cbc", "aes128-cbc"),
        "cipher.c2s" to listOf("aes256-cbc", "aes192-cbc", "aes128-cbc")
    )

    /** Idempotent and cheap; call before creating a session. */
    fun install() {
        AndroidEdDSA.install()
        JSch.setConfig("xdh", AndroidXDH::class.java.name)
        legacy.forEach { (key, extra) ->
            val current = JSch.getConfig(key).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val missing = extra.filterNot { it in current }
            if (missing.isNotEmpty()) JSch.setConfig(key, (current + missing).joinToString(","))
        }
    }
}

/** JSch `xdh` provider: the platform one when it exists, otherwise a pure-Java X25519. */
class AndroidXDH : XDH {
    private var platform: XDH? = null
    private var privateKey = ByteArray(0)
    private var publicKey = ByteArray(0)

    override fun init(name: String, keylen: Int) {
        val delegate = runCatching { com.jcraft.jsch.jce.XDH().also { it.init(name, keylen) } }.getOrNull()
        if (delegate != null) {
            platform = delegate
            return
        }
        if (name != "X25519") throw NoSuchAlgorithmException("Unsupported XDH curve $name")
        platform = null
        privateKey = ByteArray(X25519.SIZE).also { SecureRandom().nextBytes(it) }
        publicKey = X25519.scalarMult(privateKey, X25519.BASE_POINT)
    }

    override fun getQ(): ByteArray = platform?.q ?: publicKey.copyOf()

    override fun getSecret(otherPublic: ByteArray): ByteArray {
        platform?.let { return it.getSecret(otherPublic) }
        require(otherPublic.size == X25519.SIZE) { "Invalid X25519 public key length" }
        val secret = X25519.scalarMult(privateKey, otherPublic)
        // RFC 7748 section 6.1: reject a low-order peer key (all-zero shared secret).
        if (secret.all { it == 0.toByte() }) throw IllegalArgumentException("Invalid X25519 shared secret")
        return secret
    }

    override fun validate(otherPublic: ByteArray): Boolean =
        platform?.validate(otherPublic) ?: (otherPublic.size == X25519.SIZE)
}

/** RFC 7748 X25519 (TweetNaCl-style constant-time field arithmetic on 16 x 16-bit limbs). */
internal object X25519 {
    const val SIZE = 32
    val BASE_POINT = ByteArray(SIZE).also { it[0] = 9 }
    private val C121665 = longArrayOf(0xDB41, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    fun scalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        require(scalar.size == SIZE && point.size == SIZE) { "X25519 inputs must be 32 bytes" }
        val z = IntArray(SIZE) { scalar[it].toInt() and 0xff }
        z[31] = (z[31] and 127) or 64
        z[0] = z[0] and 248
        val x = unpack(point)
        val a = LongArray(16).also { it[0] = 1 }
        val b = x.copyOf()
        val c = LongArray(16)
        val d = LongArray(16).also { it[0] = 1 }
        val e = LongArray(16)
        val f = LongArray(16)
        for (i in 254 downTo 0) {
            val r = (z[i ushr 3] ushr (i and 7)) and 1
            sel(a, b, r); sel(c, d, r)
            add(e, a, c); sub(a, a, c)
            add(c, b, d); sub(b, b, d)
            mul(d, e, e); mul(f, a, a)
            mul(a, c, a); mul(c, b, e)
            add(e, a, c); sub(a, a, c)
            mul(b, a, a); sub(c, d, f)
            mul(a, c, C121665); add(a, a, d)
            mul(c, c, a); mul(a, d, f)
            mul(d, b, x); mul(b, e, e)
            sel(a, b, r); sel(c, d, r)
        }
        val inverse = invert(c)
        mul(a, a, inverse)
        return pack(a)
    }

    private fun unpack(n: ByteArray): LongArray {
        val o = LongArray(16) { (n[2 * it].toLong() and 0xff) + ((n[2 * it + 1].toLong() and 0xff) shl 8) }
        o[15] = o[15] and 0x7fff
        return o
    }

    private fun car(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            val next = if (i < 15) i + 1 else 0
            o[next] += c - 1 + if (i == 15) 37 * (c - 1) else 0
            o[i] -= c shl 16
        }
    }

    private fun sel(p: LongArray, q: LongArray, bit: Int) {
        val mask = (bit - 1).inv().toLong()
        for (i in 0 until 16) {
            val t = mask and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] + b[i] }

    private fun sub(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] - b[i] }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        car(o); car(o)
    }

    private fun invert(input: LongArray): LongArray {
        val c = input.copyOf()
        for (a in 253 downTo 0) {
            mul(c, c, c)
            if (a != 2 && a != 4) mul(c, c, input)
        }
        return c
    }

    private fun pack(n: LongArray): ByteArray {
        val t = n.copyOf()
        val m = LongArray(16)
        car(t); car(t); car(t)
        repeat(2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt()
            m[14] = m[14] and 0xffff
            sel(t, m, 1 - b)
        }
        return ByteArray(SIZE) { i ->
            val limb = t[i / 2]
            (if (i % 2 == 0) limb and 0xff else limb shr 8).toByte()
        }
    }
}
