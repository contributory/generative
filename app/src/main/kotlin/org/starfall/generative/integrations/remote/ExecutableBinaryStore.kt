package org.starfall.generative.integrations.remote

import android.content.Context
import android.net.Uri
import android.system.Os
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** An executable file the user uploaded once; features reference it by [name]. */
@Serializable
data class ExecutableBinary(
    val id: String,
    val name: String,
    val path: String
)

/**
 * Read-only view of the uploaded executables. This is all a feature needs in order to use them
 * (for example Remote Machines expanding a ProxyCommand), so such features depend on this
 * interface only: they never see the settings UI, preferences, or the ability to import/delete.
 */
interface ExecutableBinaries {
    val binaries: StateFlow<List<ExecutableBinary>>

    /**
     * How to invoke [binary] from a shell command line. Defaults to its path; implementations may
     * prefix a launcher when Android forbids executing the file directly.
     */
    fun launchCommand(binary: ExecutableBinary): String = binary.path

    /** Replaces every whole-word reference to an uploaded binary's name with a runnable invocation. */
    fun resolveCommand(command: String): String =
        resolveExecutableCommand(command, binaries.value, ::launchCommand)
}

fun resolveExecutableCommand(
    command: String,
    binaries: List<ExecutableBinary>,
    launch: (ExecutableBinary) -> String = { it.path }
): String {
    var resolved = command
    binaries.forEach { binary ->
        val pattern = Regex("(^|\\s)${Regex.escape(binary.name)}(?=\\s|$)")
        resolved = pattern.replace(resolved) { match -> "${match.value.substringBefore(binary.name)}${launch(binary)}" }
    }
    return resolved
}

/**
 * Android 10+ (for apps targeting API 29+) denies execve() on files in the app's own data
 * directory with EACCES even when the mode bits allow it. ELF files can still be started through
 * the system dynamic linker, and scripts through their interpreter. [header] is the file's first
 * bytes; [executeBlocked] is whether direct execution is denied on this device/app.
 */
fun launchCommandFor(
    path: String,
    header: ByteArray,
    executeBlocked: Boolean,
    exists: (String) -> Boolean = { java.io.File(it).exists() }
): String {
    if (!executeBlocked) return path
    val isElf = header.size >= 5 && header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() &&
        header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
    if (isElf) {
        val linker = if (header[4].toInt() == 2) "/system/bin/linker64" else "/system/bin/linker"
        return if (exists(linker)) "$linker $path" else path
    }
    if (header.size >= 2 && header[0] == '#'.code.toByte() && header[1] == '!'.code.toByte()) {
        val interpreter = String(header, 2, header.size - 2, Charsets.UTF_8)
            .lineSequence().first().trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        // /usr/bin/env, /bin/bash... don't exist on Android; fall back to the system shell.
        return "${interpreter.takeIf { it.isNotEmpty() && exists(it) } ?: "/system/bin/sh"} $path"
    }
    return path
}

/**
 * Owns the uploaded executables: files under `filesDir/binaries` plus a small JSON index. It is
 * independent of the Remote Machines state; its settings screen manages it, while command
 * execution reads it via [ExecutableBinaries].
 */
class ExecutableBinaryStore(context: Context) : ExecutableBinaries {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val _binaries = MutableStateFlow(readIndex())
    override val binaries: StateFlow<List<ExecutableBinary>> = _binaries.asStateFlow()

    private val directory: File get() = File(appContext.filesDir, DIRECTORY).apply { mkdirs() }

    private val executeBlocked: Boolean =
        android.os.Build.VERSION.SDK_INT >= 29 && appContext.applicationInfo.targetSdkVersion >= 29

    override fun launchCommand(binary: ExecutableBinary): String {
        val header = ByteArray(HEADER_BYTES)
        val read = runCatching { File(binary.path).inputStream().use { it.read(header) } }.getOrDefault(-1)
        return launchCommandFor(binary.path, header.copyOf(read.coerceAtLeast(0)), executeBlocked)
    }

    /** Re-applies owner-only rwx to every stored file (e.g. after a restore). Safe to repeat. */
    suspend fun repairPermissions() = withContext(Dispatchers.IO) {
        _binaries.value.forEach { makeExecutable(File(it.path)) }
    }

    private fun makeExecutable(file: File) {
        if (!file.isFile) return
        file.setReadable(false, false)
        file.setWritable(true, true)
        file.setExecutable(true, false)
        // setExecutable(.., false) opens it to everyone; tighten to owner-only rwx like before.
        runCatching { Os.chmod(file.absolutePath, 0b111_000_000) }
    }

    /** Copies the picked file into app storage and makes it executable. Same name replaces the old one. */
    suspend fun import(uri: Uri): ExecutableBinary = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() }?.substringAfterLast('/') ?: "binary-${UUID.randomUUID()}"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "binary-${UUID.randomUUID()}" }
        val target = File(directory, "${UUID.randomUUID()}-$safeName")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected binary.")
        makeExecutable(target)
        val binary = ExecutableBinary(UUID.randomUUID().toString(), displayName, target.absolutePath)
        val replaced = update { current ->
            current.filterNot { it.name == displayName } + binary
        }
        replaced.forEach { runCatching { File(it.path).delete() } }
        binary
    }

    suspend fun delete(id: String) {
        val removed = withContext(Dispatchers.IO) { update { current -> current.filterNot { it.id == id } } }
        removed.forEach { runCatching { File(it.path).delete() } }
    }

    /**
     * Registers a file that predates this store (uploaded under Remote Machines). The file is moved
     * into the shared directory when possible. Idempotent: an existing entry with that name wins.
     */
    suspend fun adoptLegacy(name: String, file: File) = withContext(Dispatchers.IO) {
        lock.withLock {
            val current = _binaries.value
            if (!file.isFile || current.any { it.name == name }) return@withLock
            val moved = File(directory, file.name).takeIf { file.renameTo(it) } ?: file
            makeExecutable(moved)
            writeIndex(current + ExecutableBinary(UUID.randomUUID().toString(), name, moved.absolutePath))
        }
    }

    /** Applies [transform], persists, and returns the entries that were dropped. */
    private suspend fun update(transform: (List<ExecutableBinary>) -> List<ExecutableBinary>): List<ExecutableBinary> =
        lock.withLock {
            val before = _binaries.value
            val after = transform(before)
            writeIndex(after)
            before.filter { old -> after.none { it.id == old.id } }
        }

    private fun writeIndex(list: List<ExecutableBinary>) {
        preferences.edit(commit = true) { putString(KEY, json.encodeToString(list)) }
        _binaries.value = list
    }

    private fun readIndex(): List<ExecutableBinary> {
        val stored = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ExecutableBinary>>(stored) }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES = "executable_binaries"
        const val KEY = "index"
        const val DIRECTORY = "binaries"
        const val HEADER_BYTES = 256
    }
}
