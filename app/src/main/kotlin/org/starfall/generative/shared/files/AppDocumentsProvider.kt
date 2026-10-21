package org.starfall.generative.shared.files

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import org.starfall.generative.R

/**
 * Registers the app's generated-files directory ([ToolFiles.appStorageDirectory]) with Android's
 * DocumentsUI, so the system Files app lists it as a browsable location next to Downloads and
 * device storage. Everything the app creates already lands in that one directory through
 * [ToolFiles]; this provider is only the bridge that makes it reachable from outside the app.
 */
class AppDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    private val baseDirectory: File
        get() = ToolFiles.appStorageDirectory(requireNotNull(context) { "Provider not attached" })

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val appContext = requireNotNull(context)
        val cursor = MatrixCursor(projection ?: ROOT_PROJECTION)
        cursor.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, appContext.getString(R.string.app_name))
            add(DocumentsContract.Root.COLUMN_SUMMARY, appContext.getString(R.string.settings_storage_description))
            add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
            )
            runCatching { baseDirectory.usableSpace }.getOrNull()?.let { add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, it) }
        }
        return cursor
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_PROJECTION)
        includeFile(cursor, fileFor(documentId))
        return cursor
    }

    override fun queryChildDocuments(parentDocumentId: String?, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = fileFor(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a directory: $parentDocumentId")
        val cursor = MatrixCursor(projection ?: DOCUMENT_PROJECTION)
        parent.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".spill") }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { includeFile(cursor, it) }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, DocumentsContract.buildChildDocumentsUri(authority(), parentDocumentId))
        return cursor
    }

    override fun openDocument(documentId: String?, mode: String?, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = fileFor(documentId)
        if (!file.isFile) throw FileNotFoundException("No such file: $documentId")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode ?: "r"))
    }

    override fun createDocument(parentDocumentId: String?, mimeType: String?, displayName: String?): String {
        val parent = fileFor(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a directory: $parentDocumentId")
        val safeName = File(displayName?.takeIf { it.isNotBlank() } ?: "file").name
        var target = File(parent, safeName)
        var suffix = 1
        while (target.exists()) {
            val extension = safeName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
            target = File(parent, "${safeName.removeSuffix(extension)} ($suffix)$extension")
            suffix++
        }
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (!target.mkdirs()) throw FileNotFoundException("Could not create folder: $safeName")
        } else if (!target.createNewFile()) {
            throw FileNotFoundException("Could not create file: $safeName")
        }
        notifyChildrenChanged(parentDocumentId ?: ROOT_ID)
        return target.name
    }

    override fun deleteDocument(documentId: String?) {
        val file = fileFor(documentId)
        if (documentId == ROOT_ID || !file.exists()) throw FileNotFoundException("No such document: $documentId")
        if (!file.deleteRecursively()) throw FileNotFoundException("Could not delete: $documentId")
        notifyChildrenChanged(ROOT_ID)
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean =
        parentDocumentId == ROOT_ID && documentId != ROOT_ID && runCatching { fileFor(documentId) }.isSuccess

    /**
     * Resolves a document id to a file inside [baseDirectory]. Ids are plain file names; anything
     * that would escape the directory (or names of in-progress writes) is rejected.
     */
    private fun fileFor(documentId: String?): File {
        val base = baseDirectory
        if (documentId == null || documentId == ROOT_ID) return base
        if (!documentId.matches(Regex("[a-zA-Z0-9._() -]+")) || documentId.endsWith(".part") || documentId.endsWith(".spill")) {
            throw FileNotFoundException("Invalid document: $documentId")
        }
        val file = File(base, documentId)
        if (file.canonicalFile.parentFile != base.canonicalFile) throw FileNotFoundException("Outside storage: $documentId")
        return file
    }

    private fun includeFile(cursor: MatrixCursor, file: File) {
        val isRoot = file == baseDirectory
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, if (isRoot) ROOT_ID else file.name)
            add(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                if (isRoot) requireNotNull(context).getString(R.string.settings_storage) else file.name
            )
            if (file.isDirectory) {
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                )
            } else {
                add(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                )
                add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                )
                add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            }
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
        }
    }

    private fun notifyChildrenChanged(parentDocumentId: String) {
        val appContext = context ?: return
        appContext.contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority(appContext), parentDocumentId), null)
    }

    private fun authority(): String = authority(requireNotNull(context))

    companion object {
        const val ROOT_ID = "app_files"

        fun authority(context: Context): String = "${context.packageName}.documents"

        private val ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )
        private val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}

/**
 * Opens the system Files app (DocumentsUI) already rooted at this app's generated-files location.
 * Tries the root view first, then falls back to the document picker opened at the same place.
 * Returns false when no file manager on the device handles any of those intents.
 */
fun openAppStorageInDocumentsUi(context: Context): Boolean {
    val authority = AppDocumentsProvider.authority(context)
    val rootUri = DocumentsContract.buildRootUri(authority, AppDocumentsProvider.ROOT_ID)
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(rootUri, DocumentsContract.Root.MIME_TYPE_ITEM)
        },
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildTreeDocumentUri(authority, AppDocumentsProvider.ROOT_ID))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        },
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(authority, AppDocumentsProvider.ROOT_ID))
        }
    )
    for (intent in attempts) {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            // No handler for this form; try the next one.
        }
    }
    return false
}
