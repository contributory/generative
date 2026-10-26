package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.runtime.staticCompositionLocalOf

internal fun markdownContentFingerprint(content: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 15])
        }
    }
}

/** Shared render identity for descendants of one parsed Markdown document. */
internal val LocalMarkdownContentFingerprint = staticCompositionLocalOf<String?> { null }
