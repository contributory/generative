package org.starfall.generative.integrations.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.starfall.generative.R
import org.starfall.generative.shared.model.REMOTE_SFTP_HOME
import org.starfall.generative.shared.model.RemoteMachine

/**
 * Exposes each Remote Machine's configured SFTP directory as a DocumentsUI root. No SSH connection
 * is opened by [queryRoots] or by the Files intent itself; the session starts with the first
 * document operation and is closed again by [RemoteSftpSessionPool] when it goes idle.
 */
class RemoteSftpDocumentsProvider : DocumentsProvider() {
    private val service: RemoteMachineService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val app = requireNotNull(context) { "Provider not attached" }.applicationContext
        (app as? RemoteMachineServiceOwner)?.remoteMachineService ?: RemoteMachineService(app)
    }

    override fun onCreate(): Boolean {
        cleanupOldTempFiles()
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: ROOT_PROJECTION
        val cursor = MatrixCursor(columns)
        val appContext = requireNotNull(context)
        service.state.value.machines.forEach { machine ->
            val id = sftpRootId(machine.id)
            cursor.newRow().apply {
                addIfPresent(columns, DocumentsContract.Root.COLUMN_ROOT_ID, id)
                addIfPresent(columns, DocumentsContract.Root.COLUMN_DOCUMENT_ID, id)
                addIfPresent(
                    columns,
                    DocumentsContract.Root.COLUMN_TITLE,
                    machine.name.ifBlank { appContext.getString(R.string.remote_machine_unnamed) }
                )
                addIfPresent(
                    columns,
                    DocumentsContract.Root.COLUMN_SUMMARY,
                    appContext.getString(
                        R.string.remote_machine_sftp_root_summary,
                        machine.username.ifBlank { "?" },
                        machine.displayHost,
                        machine.sftpDirectory.ifBlank { REMOTE_SFTP_HOME }
                    )
                )
                addIfPresent(columns, DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                addIfPresent(
                    columns,
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                )
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val id = documentId ?: missingSftpDocument()
        val columns = projection ?: DOCUMENT_PROJECTION
        val cursor = MatrixCursor(columns)
        val rootId = sftpRootIdOfDocument(id)
        val machine = machineForRoot(rootId)
        val relative = sftpRelativePathOf(rootId, id)
        if (relative.isBlank()) {
            includeRootDocument(cursor, columns, rootId, machine)
        } else {
            val attrs = withLease(machine) { lease -> lease.channel.stat(lease.absolute(relative)) }
            includeSftpDocument(cursor, columns, id, relative.substringAfterLast('/'), attrs)
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parentId = parentDocumentId ?: missingSftpDocument()
        val columns = projection ?: DOCUMENT_PROJECTION
        val cursor = MatrixCursor(columns)
        val rootId = sftpRootIdOfDocument(parentId)
        val machine = machineForRoot(rootId)
        val relative = sftpRelativePathOf(rootId, parentId)
        val children = withLease(machine) { lease ->
            lease.channel.ls(lease.absolute(relative))
                .asSequence()
                .filter { it.filename != "." && it.filename != ".." }
                .map { entry ->
                    val childRelative = sftpJoinRelative(relative, entry.filename)
                    val attrs = if (entry.attrs.isLink) {
                        runCatching { lease.channel.stat(lease.absolute(childRelative)) }.getOrDefault(entry.attrs)
                    } else entry.attrs
                    RemoteChild(entry.filename, childRelative, attrs)
                }
                .sortedWith(compareByDescending<RemoteChild> { it.attrs.isDir }.thenBy { it.name.lowercase() })
                .toList()
        }
        children.forEach { child ->
            includeSftpDocument(
                cursor,
                columns,
                sftpChildDocumentId(rootId, child.relativePath),
                child.name,
                child.attrs
            )
        }
        val appContext = requireNotNull(context)
        cursor.setNotificationUri(
            appContext.contentResolver,
            DocumentsContract.buildChildDocumentsUri(authority(appContext), parentId)
        )
        return cursor
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val id = documentId ?: missingSftpDocument()
        val rootId = sftpRootIdOfDocument(id)
        val machine = machineForRoot(rootId)
        val relative = sftpRelativePathOf(rootId, id)
        if (relative.isBlank()) missingSftpDocument("The SFTP root cannot be opened as a file.")
        val parsedMode = ParcelFileDescriptor.parseMode(mode ?: "r")
        val access = parsedMode and ParcelFileDescriptor.MODE_READ_WRITE
        val wantsRead = access == ParcelFileDescriptor.MODE_READ_ONLY ||
            access == ParcelFileDescriptor.MODE_READ_WRITE
        val wantsWrite = access == ParcelFileDescriptor.MODE_WRITE_ONLY ||
            access == ParcelFileDescriptor.MODE_READ_WRITE
        return try {
            if (wantsWrite) {
                openWritableDocument(machine, rootId, relative, parsedMode, wantsRead)
            } else {
                openReadableDocument(machine, relative, parsedMode)
            }
        } catch (error: FileNotFoundException) {
            throw error
        } catch (error: Exception) {
            throw missingSftpDocument(error.message ?: "Could not open the SFTP file.", error)
        }
    }

    override fun createDocument(
        parentDocumentId: String?,
        mimeType: String?,
        displayName: String?
    ): String {
        val parentId = parentDocumentId ?: missingSftpDocument()
        val rootId = sftpRootIdOfDocument(parentId)
        val machine = machineForRoot(rootId)
        val parentRelative = sftpRelativePathOf(rootId, parentId)
        val name = sanitizeSftpName(displayName)
        val relative = sftpJoinRelative(parentRelative, name)
        withLease(machine) { lease ->
            val absolute = lease.absolute(relative)
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                lease.channel.mkdir(absolute)
            } else {
                lease.channel.put(absolute).close()
            }
        }
        notifyChildren(rootId, parentRelative)
        return sftpChildDocumentId(rootId, relative)
    }

    override fun deleteDocument(documentId: String?) {
        val id = documentId ?: missingSftpDocument()
        val rootId = sftpRootIdOfDocument(id)
        val machine = machineForRoot(rootId)
        val relative = sftpRelativePathOf(rootId, id)
        if (relative.isBlank()) missingSftpDocument("The SFTP root cannot be deleted.")
        withLease(machine) { lease ->
            val absolute = lease.absolute(relative)
            val attrs = lease.channel.lstat(absolute)
            if (attrs.isDir && !attrs.isLink) {
                deleteDirectory(lease.channel, absolute)
            } else {
                lease.channel.rm(absolute)
            }
        }
        notifyChildren(rootId, sftpParentRelative(relative))
    }

    override fun renameDocument(documentId: String?, displayName: String?): String {
        val id = documentId ?: missingSftpDocument()
        val rootId = sftpRootIdOfDocument(id)
        val machine = machineForRoot(rootId)
        val relative = sftpRelativePathOf(rootId, id)
        if (relative.isBlank()) missingSftpDocument("The SFTP root cannot be renamed.")
        val name = sanitizeSftpName(displayName)
        val parentRelative = sftpParentRelative(relative)
        val newRelative = sftpJoinRelative(parentRelative, name)
        withLease(machine) { lease ->
            lease.channel.rename(lease.absolute(relative), lease.absolute(newRelative))
        }
        notifyChildren(rootId, parentRelative)
        return sftpChildDocumentId(rootId, newRelative)
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        if (parentDocumentId == null || documentId == null) return false
        return try {
            val parentRoot = sftpRootIdOfDocument(parentDocumentId)
            if (parentRoot != sftpRootIdOfDocument(documentId)) {
                false
            } else {
                val parentRelative = sftpRelativePathOf(parentRoot, parentDocumentId)
                val childRelative = sftpRelativePathOf(parentRoot, documentId)
                parentRelative.isBlank() || childRelative == parentRelative ||
                    childRelative.startsWith("$parentRelative/")
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun querySearchDocuments(
        rootId: String?,
        query: String?,
        projection: Array<out String>?
    ): Cursor = MatrixCursor(projection ?: DOCUMENT_PROJECTION)

    override fun queryRecentDocuments(rootId: String?, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_PROJECTION)

    override fun ejectRoot(rootId: String?) {
        val id = rootId ?: return
        runCatching { service.invalidateSftp(sftpMachineIdOf(id)) }
    }

    private fun openReadableDocument(
        machine: RemoteMachine,
        relative: String,
        parsedMode: Int
    ): ParcelFileDescriptor {
        val lease = acquireLease(machine)
        val input: InputStream
        val size: Long
        try {
            val attrs = lease.channel.stat(lease.absolute(relative))
            if (attrs.isDir) missingSftpDocument("Not a file: $relative")
            size = attrs.size
            input = lease.channel.get(lease.absolute(relative))
        } catch (error: Throwable) {
            runCatching { lease.close() }
            throw error
        }
        val thread = HandlerThread("remote-sftp-read").apply { start() }
        val callback = RemoteSftpReadCallback(lease, relative, size, input, thread)
        return try {
            storageManager().openProxyFileDescriptor(parsedMode, callback, Handler(thread.looper))
        } catch (error: Throwable) {
            callback.onRelease()
            throw error
        }
    }

    private fun openWritableDocument(
        machine: RemoteMachine,
        rootId: String,
        relative: String,
        parsedMode: Int,
        wantsRead: Boolean
    ): ParcelFileDescriptor {
        val temp = File.createTempFile("sftp-", ".tmp", tempDirectory())
        val append = parsedMode and ParcelFileDescriptor.MODE_APPEND != 0
        val truncated = parsedMode and ParcelFileDescriptor.MODE_TRUNCATE != 0
        val preserve = append || (wantsRead && !truncated)
        if (preserve) {
            try {
                downloadForEditing(machine, relative, temp)
            } catch (error: Throwable) {
                temp.delete()
                throw error
            }
        }
        val thread = HandlerThread("remote-sftp-write").apply { start() }
        val callback = try {
            RemoteSftpWriteCallback(
                service = service,
                machineId = machine.id,
                relativePath = relative,
                tempFile = temp,
                thread = thread,
                onUploaded = { notifyChildren(rootId, sftpParentRelative(relative)) }
            )
        } catch (error: Throwable) {
            thread.quitSafely()
            temp.delete()
            throw error
        }
        return try {
            storageManager().openProxyFileDescriptor(parsedMode, callback, Handler(thread.looper))
        } catch (error: Throwable) {
            callback.abort()
            throw error
        }
    }

    private fun downloadForEditing(machine: RemoteMachine, relative: String, target: File) {
        val lease = acquireLease(machine)
        try {
            val absolute = lease.absolute(relative)
            try {
                val attrs = lease.channel.stat(absolute)
                if (attrs.isDir) missingSftpDocument("Not a file: $relative")
                lease.channel.get(absolute, target.absolutePath)
            } catch (error: SftpException) {
                if (error.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw error
            }
        } finally {
            runCatching { lease.close() }
        }
    }

    private fun acquireLease(machine: RemoteMachine): RemoteSftpLease = try {
        runBlocking { service.acquireSftpLease(machine) }
    } catch (error: Exception) {
        throw missingSftpDocument(error.message ?: "Could not open the SFTP session.", error)
    }

    private fun <T> withLease(machine: RemoteMachine, block: (RemoteSftpLease) -> T): T {
        val lease = acquireLease(machine)
        return try {
            lease.use(block)
        } catch (error: FileNotFoundException) {
            throw error
        } catch (error: Exception) {
            throw missingSftpDocument(error.message ?: "SFTP operation failed.", error)
        }
    }

    private fun includeRootDocument(
        cursor: MatrixCursor,
        columns: Array<out String>,
        rootId: String,
        machine: RemoteMachine
    ) {
        val displayName = machine.name.ifBlank {
            requireNotNull(context).getString(R.string.remote_machine_unnamed)
        }
        includeSftpRootDocument(cursor, columns, rootId, displayName)
    }

    private fun deleteDirectory(channel: ChannelSftp, absolute: String) {
        channel.ls(absolute).forEach { entry ->
            if (entry.filename == "." || entry.filename == "..") return@forEach
            val child = sftpJoinRemote(absolute, entry.filename)
            val attrs = channel.lstat(child)
            if (attrs.isDir && !attrs.isLink) {
                deleteDirectory(channel, child)
            } else {
                channel.rm(child)
            }
        }
        channel.rmdir(absolute)
    }

    private fun machineForRoot(rootId: String): RemoteMachine {
        val machineId = sftpMachineIdOf(rootId)
        return service.state.value.machines.firstOrNull { it.id == machineId }
            ?: missingSftpDocument("Remote machine no longer exists.")
    }

    private fun notifyChildren(rootId: String, parentRelative: String) {
        val parentId = if (parentRelative.isBlank()) rootId else sftpChildDocumentId(rootId, parentRelative)
        val appContext = requireNotNull(context)
        appContext.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(authority(appContext), parentId),
            null
        )
    }

    private fun storageManager(): StorageManager =
        requireNotNull(requireNotNull(context).getSystemService(StorageManager::class.java))

    private fun tempDirectory(): File =
        File(requireNotNull(context).cacheDir, TEMP_DIRECTORY).apply { mkdirs() }

    private fun cleanupOldTempFiles() {
        val directory = tempDirectory()
        val cutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MILLIS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }

    private data class RemoteChild(val name: String, val relativePath: String, val attrs: SftpATTRS)

    companion object {
        private const val TEMP_DIRECTORY = "remote-sftp"
        private const val TEMP_FILE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1_000L

        fun authority(context: Context): String = "${context.packageName}.sftp"

        fun rootId(machineId: String): String = sftpRootId(machineId)

        /**
         * Opens DocumentsUI at one machine's SFTP root. The provider opens its SSH session lazily,
         * so simply showing the Files entry does not connect to the machine.
         */
        fun open(context: Context, machineId: String): Boolean {
            val authority = authority(context)
            val rootId = rootId(machineId)
            val attempts = listOf(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(DocumentsContract.buildRootUri(authority, rootId), DocumentsContract.Root.MIME_TYPE_ITEM)
                },
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildTreeDocumentUri(authority, rootId)
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                },
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri(authority, rootId)
                    )
                }
            )
            for (intent in attempts) {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {
                    // This device's file manager does not handle that form; try the next one.
                }
            }
            return false
        }

        private val ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_FLAGS
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
