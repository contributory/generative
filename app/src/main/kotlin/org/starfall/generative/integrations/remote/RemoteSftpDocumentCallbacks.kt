package org.starfall.generative.integrations.remote

import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking

internal const val REMOTE_SFTP_DOCUMENTS_TAG = "RemoteSftpDocuments"

/**
 * Streams a remote file through a proxy descriptor. Keeping the SFTP channel open only while the
 * descriptor is alive avoids copying the whole file into app storage just to preview it.
 */
internal class RemoteSftpReadCallback(
    private val lease: RemoteSftpLease,
    private val relativePath: String,
    private val fileSize: Long,
    initialInput: InputStream,
    private val thread: HandlerThread
) : ProxyFileDescriptorCallback() {
    private var input = initialInput
    private var position = 0L
    private var released = false

    override fun onGetSize(): Long = fileSize

    @Synchronized
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if (released) throw ErrnoException("read", OsConstants.EBADF)
        if (offset < 0 || size <= 0) return 0
        // The descriptor contract treats reads at or past EOF as an empty result, not an error.
        if (offset >= fileSize) return 0
        return try {
            seek(offset)
            val count = input.read(data, 0, minOf(size, data.size))
            if (count <= 0) {
                0
            } else {
                position += count
                count
            }
        } catch (error: Exception) {
            throw ErrnoException("read", OsConstants.EIO, error)
        }
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int =
        throw ErrnoException("write", OsConstants.EROFS)

    override fun onRelease() {
        synchronized(this) {
            if (released) return
            released = true
            runCatching { input.close() }
            runCatching { lease.close() }
        }
        thread.quitSafely()
    }

    private fun seek(offset: Long) {
        if (offset == position) return
        input.close()
        input = lease.channel.get(lease.absolute(relativePath))
        position = 0L
        var remaining = offset
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (input.read() < 0) break
                remaining--
            }
        }
        position = offset - remaining
    }
}

/**
 * Stages writes in a local cache file and uploads it when the descriptor is released. SFTP has no
 * random-access write stream, so this keeps DocumentsUI's offset-based writes reliable.
 */
internal class RemoteSftpWriteCallback(
    private val service: RemoteMachineService,
    private val machineId: String,
    private val relativePath: String,
    private val tempFile: File,
    private val thread: HandlerThread,
    private val onUploaded: () -> Unit
) : ProxyFileDescriptorCallback() {
    private val file = RandomAccessFile(tempFile, "rw")
    private var released = false

    @Synchronized
    override fun onGetSize(): Long = file.length()

    @Synchronized
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if (released) throw ErrnoException("read", OsConstants.EBADF)
        if (offset < 0 || size <= 0 || offset >= file.length()) return 0
        return try {
            file.seek(offset)
            val count = file.read(data, 0, minOf(size, data.size))
            if (count < 0) 0 else count
        } catch (error: Exception) {
            throw ErrnoException("read", OsConstants.EIO, error)
        }
    }

    @Synchronized
    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        if (released) throw ErrnoException("write", OsConstants.EBADF)
        if (offset < 0 || size <= 0) return 0
        return try {
            file.seek(offset)
            val count = minOf(size, data.size)
            file.write(data, 0, count)
            count
        } catch (error: Exception) {
            throw ErrnoException("write", OsConstants.EIO, error)
        }
    }

    @Synchronized
    override fun onFsync() {
        if (released) throw ErrnoException("fsync", OsConstants.EBADF)
        try {
            file.fd.sync()
        } catch (error: Exception) {
            throw ErrnoException("fsync", OsConstants.EIO, error)
        }
    }

    /** Drops a staging file when the descriptor could not be opened; unlike onRelease it uploads nothing. */
    fun abort() {
        synchronized(this) {
            if (released) return
            released = true
            runCatching { file.close() }
        }
        tempFile.delete()
        thread.quitSafely()
    }

    override fun onRelease() {
        synchronized(this) {
            if (released) return
            released = true
            runCatching { file.close() }
        }
        try {
            runBlocking {
                val machine = service.state.value.machines.firstOrNull { it.id == machineId }
                    ?: error("Remote machine no longer exists.")
                service.acquireSftpLease(machine).use { lease ->
                    lease.channel.put(tempFile.absolutePath, lease.absolute(relativePath))
                }
            }
            onUploaded()
        } catch (error: Exception) {
            Log.w(REMOTE_SFTP_DOCUMENTS_TAG, "Could not upload $relativePath", error)
        } finally {
            tempFile.delete()
            thread.quitSafely()
        }
    }
}
