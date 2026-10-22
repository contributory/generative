package org.starfall.generative.integrations.remote

import android.database.MatrixCursor
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.jcraft.jsch.SftpATTRS
import java.io.FileNotFoundException
import java.util.Base64

private const val REMOTE_SFTP_ROOT_PREFIX = "machine:"

internal fun sftpRootId(machineId: String): String =
    REMOTE_SFTP_ROOT_PREFIX + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(machineId.toByteArray(Charsets.UTF_8))

internal fun sftpMachineIdOf(rootId: String): String {
    if (!rootId.startsWith(REMOTE_SFTP_ROOT_PREFIX)) {
        missingSftpDocument("Unknown SFTP root: $rootId")
    }
    val encoded = rootId.removePrefix(REMOTE_SFTP_ROOT_PREFIX)
    return runCatching {
        String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
    }.getOrElse { missingSftpDocument("Unknown SFTP root: $rootId") }
}

internal fun sftpRootIdOfDocument(documentId: String): String {
    val root = documentId.substringBefore('/')
    if (!root.startsWith(REMOTE_SFTP_ROOT_PREFIX)) {
        missingSftpDocument("Unknown SFTP document: $documentId")
    }
    return root
}

internal fun sftpRelativePathOf(rootId: String, documentId: String): String {
    if (documentId == rootId) return ""
    val encoded = documentId.removePrefix("$rootId/")
    if (encoded == documentId || encoded.isBlank()) {
        missingSftpDocument("Unknown SFTP document: $documentId")
    }
    val relative = runCatching {
        String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
    }.getOrElse { missingSftpDocument("Invalid SFTP document: $documentId") }
    if (relative.startsWith("/") ||
        relative.split('/').any { it.isEmpty() || it == "." || it == ".." } ||
        '\u0000' in relative
    ) {
        missingSftpDocument("Invalid SFTP document: $documentId")
    }
    return relative
}

internal fun sftpChildDocumentId(rootId: String, relative: String): String =
    "$rootId/${Base64.getUrlEncoder().withoutPadding().encodeToString(relative.toByteArray(Charsets.UTF_8))}"

internal fun sftpJoinRelative(parent: String, name: String): String =
    if (parent.isBlank()) name else "$parent/$name"

internal fun sftpJoinRemote(parent: String, name: String): String =
    if (parent == "/") "/$name" else "$parent/$name"

internal fun sftpParentRelative(relative: String): String = relative.substringBeforeLast('/', "")

internal fun sanitizeSftpName(displayName: String?): String {
    val name = displayName?.trim()?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: missingSftpDocument("A file name is required.")
    if (name == "." || name == ".." || '\u0000' in name) {
        missingSftpDocument("Invalid file name: $name")
    }
    return name
}

internal fun missingSftpDocument(message: String = "SFTP document not found."): Nothing =
    throw FileNotFoundException(message)

internal fun missingSftpDocument(message: String, cause: Throwable): Nothing =
    throw FileNotFoundException(message).apply { initCause(cause) }

internal fun includeSftpRootDocument(
    cursor: MatrixCursor,
    columns: Array<out String>,
    rootId: String,
    displayName: String
) {
    cursor.newRow().apply {
        addIfPresent(columns, DocumentsContract.Document.COLUMN_DOCUMENT_ID, rootId)
        addIfPresent(columns, DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        addIfPresent(columns, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
        addIfPresent(
            columns,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        )
    }
}

internal fun includeSftpDocument(
    cursor: MatrixCursor,
    columns: Array<out String>,
    documentId: String,
    displayName: String,
    attrs: SftpATTRS
) {
    val directory = attrs.isDir
    cursor.newRow().apply {
        addIfPresent(columns, DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
        addIfPresent(columns, DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        addIfPresent(
            columns,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            if (directory) DocumentsContract.Document.MIME_TYPE_DIR else sftpMimeTypeFor(displayName)
        )
        addIfPresent(
            columns,
            DocumentsContract.Document.COLUMN_FLAGS,
            if (directory) {
                DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            } else {
                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            }
        )
        if (!directory) addIfPresent(columns, DocumentsContract.Document.COLUMN_SIZE, attrs.size)
        addIfPresent(
            columns,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            attrs.mTime.toLong() * 1_000L
        )
    }
}

private fun sftpMimeTypeFor(displayName: String): String {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}

internal fun MatrixCursor.RowBuilder.addIfPresent(
    columns: Array<out String>,
    column: String,
    value: Any?
) {
    if (column in columns) add(column, value)
}
