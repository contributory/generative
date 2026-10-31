package org.starfall.generative.navigation

import android.content.Intent
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import org.starfall.generative.shared.files.TemporaryChatFiles
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.files.AttachmentResolver

/** One external share is a composer draft, never a request to send a message. */
internal data class ExternalShare(val text: String?, val streams: List<Uri>, val fallbackMime: String?)

internal data class ExternalShareRequest(val id: Long, val content: ExternalShare)

/** Parse both legacy and modern share senders; ClipData may hold the only stream URI. */
@Suppress("DEPRECATION") // Parcelable retrieval also supports devices below API 33.
internal fun parseExternalShare(intent: Intent): ExternalShare? {
    val action = intent.action
    if (action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT)) return null

    val text = (if (action == Intent.ACTION_PROCESS_TEXT)
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
    else intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
        ?.toString()?.take(100_000)?.takeIf { it.isNotEmpty() }

    val streams = if (action == Intent.ACTION_PROCESS_TEXT) emptyList() else buildList {
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
        } else {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(::add)
        }
        intent.data?.let(::add)
    }.filter { it.scheme == "content" || it.scheme == "file" }.distinct().take(20)

    if (text == null && streams.isEmpty()) return null
    return ExternalShare(text, streams, intent.type)
}

/** Copy a transient Android share grant into the app's normal attachment storage. */
internal suspend fun stageSharedAttachment(
    context: Context,
    files: ToolFiles,
    uri: Uri,
    fallbackMime: String?,
    temporaryChatId: String?
): String = withContext(Dispatchers.IO + (temporaryChatId?.let(::TemporaryChatFiles) ?: EmptyCoroutineContext)) {
    // MIME metadata is advisory; some share providers allow reading but reject queries.
    val contentMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val guessedMime = runCatching { AttachmentResolver(context).metadata(uri.toString())?.mimeType }.getOrNull()
    val mime = contentMime?.takeUnless { it == "application/octet-stream" || it == "*/*" }
        ?: guessedMime?.takeUnless { it == "application/octet-stream" }
        ?: fallbackMime
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read shared attachment")
    input.use { source ->
        val name = files.save(source, mime)
        files.resolve(name)?.absolutePath ?: error("Shared attachment was not saved")
    }
}
