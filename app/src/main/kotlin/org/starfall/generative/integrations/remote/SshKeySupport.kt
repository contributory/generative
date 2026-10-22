package org.starfall.generative.integrations.remote

import java.util.Base64

/** Validates an OpenSSH public key and removes its optional comment. */
internal fun normalizeOpenSshPublicKey(text: String): String {
    val line = text.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        ?: throw IllegalArgumentException("The public key file is empty.")
    val parts = line.split(Regex("\\s+"))
    val algorithm = parts[0]
    val blob = parts.getOrNull(1)
    require(blob != null && (algorithm.startsWith("ssh-") || algorithm.startsWith("ecdsa-"))) {
        "Not an OpenSSH public key. Expected a single line like \"ssh-rsa AAAA… comment\"."
    }
    val bytes = try {
        Base64.getDecoder().decode(blob)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("The public key data is not valid Base64.")
    }
    val embedded = if (bytes.size >= 4) {
        val length = ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
        if (length in 1..(bytes.size - 4)) String(bytes, 4, length, Charsets.US_ASCII) else null
    } else null
    require(embedded == algorithm) { "The public key data does not match its declared type ($algorithm)." }
    return "$algorithm $blob"
}
