package org.starfall.generative.integrations.remote

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import org.starfall.generative.shared.files.AppDocumentsLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SFTP file operations for the in-app explorer; shares the DocumentsProvider's idle session pool. */
class RemoteSftpExplorer(private val service: RemoteMachineService) {
    fun documentUri(context: Context, machineId: String, relative: String): Uri {
        if (relative.isBlank() || relative.startsWith('/') || '\u0000' in relative ||
            relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            throw FileNotFoundException("Invalid SFTP file path")
        }
        if (service.state.value.machines.none { it.id == machineId }) {
            throw FileNotFoundException("Remote machine no longer exists")
        }
        val rootId = sftpRootId(machineId)
        return DocumentsContract.buildDocumentUri(AppDocumentsLocation.authority(context),
            sftpChildDocumentId(rootId, relative))
    }

    data class Entry(val name: String, val relativePath: String, val directory: Boolean, val size: Long, val modified: Long)

    suspend fun list(machineId: String, path: String): List<Entry> = withLease(machineId, path) { lease ->
        lease.channel.ls(lease.absolute(path)).asSequence()
            .filter { it.filename != "." && it.filename != ".." }
            .map { file ->
                val child = sftpJoinRelative(path, file.filename)
                val attrs = if (file.attrs.isLink) {
                    runCatching { lease.channel.stat(lease.absolute(child)) }.getOrDefault(file.attrs)
                } else file.attrs
                Entry(file.filename, child, attrs.isDir, attrs.size, attrs.mTime.toLong() * 1000L)
            }
            .sortedWith(compareByDescending<Entry> { it.directory }.thenBy { it.name.lowercase() }).toList()
    }

    suspend fun createFolder(machineId: String, parent: String, name: String) = withLease(machineId, parent) { lease ->
        val target = sftpJoinRelative(parent, sanitizeSftpName(name))
        lease.channel.mkdir(lease.absolute(target))
    }

    suspend fun rename(machineId: String, path: String, newName: String) = withLease(machineId, path) { lease ->
        require(path.isNotEmpty()) { "Cannot rename the machine root" }
        val target = sftpJoinRelative(sftpParentRelative(path), sanitizeSftpName(newName))
        lease.channel.rename(lease.absolute(path), lease.absolute(target))
    }

    suspend fun delete(machineId: String, path: String) = withLease(machineId, path) { lease ->
        require(path.isNotEmpty()) { "Cannot delete the machine root" }
        fun remove(path: String) {
            val absolute = lease.absolute(path)
            val attrs = lease.channel.lstat(absolute)
            if (attrs.isDir && !attrs.isLink) {
                lease.channel.ls(absolute).forEach { child ->
                    if (child.filename != "." && child.filename != "..") remove(sftpJoinRelative(path, child.filename))
                }
                lease.channel.rmdir(absolute)
            } else lease.channel.rm(absolute)
        }
        remove(path)
    }

    private suspend fun <T> withLease(machineId: String, path: String, block: (RemoteSftpLease) -> T): T =
        withContext(Dispatchers.IO) {
            // Reject traversal before joining a document-relative path to the configured SFTP root.
            if (path.startsWith('/') || '\u0000' in path || path.split('/').any { it == "." || it == ".." ||
                    (path.isNotEmpty() && it.isEmpty()) }) throw FileNotFoundException("Invalid SFTP path")
            val machine = service.state.value.machines.firstOrNull { it.id == machineId }
                ?: throw FileNotFoundException("Remote machine no longer exists")
            service.acquireSftpLease(machine).use(block)
        }
}
