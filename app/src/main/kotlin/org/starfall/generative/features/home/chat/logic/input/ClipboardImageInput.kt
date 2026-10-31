package org.starfall.generative.features.home.chat.logic.input

import android.content.ClipData
import android.net.Uri

/** Select only image URI items; plain text must remain available to the normal paste handler. */
internal fun clipboardImageUris(clip: ClipData, mimeTypeFor: (Uri) -> String?): List<Uri> {
    val declaresImage = clip.description.hasMimeType("image/*")
    return (0 until clip.itemCount).mapNotNull { index ->
        val uri = clip.getItemAt(index).uri ?: return@mapNotNull null
        val type = runCatching { mimeTypeFor(uri) }.getOrNull()
        uri.takeIf {
            type?.startsWith("image/", ignoreCase = true) == true ||
                (declaresImage && (type == null || type == "application/octet-stream"))
        }
    }.distinct()
}
