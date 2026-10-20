package org.starfall.generative.integrations.remote

import java.io.File
import java.util.Base64

/**
 * Launcher prefix for running an imported file on Android 10+ (see RemoteMachineService.binaryInvocation).
 * Empty means the file can be executed directly.
 */
internal fun binaryLauncher(
    header: ByteArray,
    sdkInt: Int,
    targetSdk: Int,
    exists: (String) -> Boolean = { File(it).exists() }
): List<String> {
    if (sdkInt < 29 || targetSdk < 29) return emptyList()
    if (isElf(header)) {
        val linker = if (header[4].toInt() == 2) "/system/bin/linker64" else "/system/bin/linker"
        return if (exists(linker)) listOf(linker) else emptyList()
    }
    if (header.size >= 2 && header[0] == '#'.code.toByte() && header[1] == '!'.code.toByte()) {
        val line = String(header, Charsets.ISO_8859_1).lineSequence().first().drop(2).trim()
        val tokens = line.split(Regex("\\s+")).filter(String::isNotEmpty)
        val interpreter = tokens.firstOrNull()?.takeIf(exists)
        // A script's interpreter path is often from another system (/bin/bash, Termux paths).
        return if (interpreter != null) listOf(interpreter) + tokens.drop(1) else listOf("/system/bin/sh")
    }
    return emptyList()
}

private fun isElf(header: ByteArray): Boolean = header.size >= 5 &&
    header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
    header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()

/** ET_EXEC (type 2): a fixed-address executable, which the Android linker cannot load. */
internal fun isStaticNonPieElf(header: ByteArray): Boolean =
    isElf(header) && header.size >= 18 && header[5].toInt() == 1 &&
        (header[16].toInt() and 0xff) == 2 && header[17].toInt() == 0

/**
 * Validates the text of an OpenSSH public key file and returns it as "<algorithm> <base64>" without
 * the comment. Throws IllegalArgumentException when it is not a usable OpenSSH public key.
 */
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
    // The blob starts with its own algorithm name (length-prefixed); it must match the line's label.
    val embedded = if (bytes.size >= 4) {
        val length = ((bytes[0].toInt() and 0xff) shl 24) or ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or (bytes[3].toInt() and 0xff)
        if (length in 1..(bytes.size - 4)) String(bytes, 4, length, Charsets.US_ASCII) else null
    } else null
    require(embedded == algorithm) { "The public key data does not match its declared type ($algorithm)." }
    return "$algorithm $blob"
}

