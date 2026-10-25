package org.starfall.generative.features.appdata.logic

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.starfall.generative.features.appdata.model.AppDataBackup
import org.starfall.generative.features.appdata.model.ZipAppDataBackup

private const val MANIFEST_ENTRY = "manifest.json"
private const val ICON_PREFIX = "icons/"
private const val FILE_PREFIX = "files/"
private const val IDENTITY_PREFIX = "remote-identities/"
private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
private const val MAX_ARCHIVE_BYTES = 1L * 1024 * 1024 * 1024

private val backupJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun writeZipBackup(
    output: OutputStream,
    data: AppDataBackup,
    iconFiles: Map<String, File>,
    toolFiles: Map<String, File>,
    identityFiles: Map<String, Pair<File, File?>>
) {
    val names = mutableSetOf<String>()
    ZipOutputStream(BufferedOutputStream(output)).use { zip ->
        fun addBytes(name: String, bytes: ByteArray) {
            check(names.add(name)) { "Duplicate backup entry: $name" }
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }

        fun addFile(name: String, file: File) {
            require(file.isFile) { "Backup source file is missing: ${file.name}" }
            check(names.add(name)) { "Duplicate backup entry: $name" }
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

        addBytes(MANIFEST_ENTRY, backupJson.encodeToString(data).toByteArray(Charsets.UTF_8))
        iconFiles.forEach { (name, file) -> addFile(ICON_PREFIX + name, file) }
        toolFiles.forEach { (name, file) -> addFile(FILE_PREFIX + name, file) }
        identityFiles.forEach { (id, files) ->
            addFile("$IDENTITY_PREFIX$id", files.first)
            files.second?.let { addFile("$IDENTITY_PREFIX$id.pub", it) }
        }
    }
}

/** Extracts a ZIP backup into [directory] after validating all paths and size limits. */
fun readZipBackup(input: InputStream, directory: File): ZipAppDataBackup {
    require(directory.mkdirs() || directory.isDirectory) { "Could not create backup staging directory." }
    var totalBytes = 0L
    var manifest: AppDataBackup? = null
    val iconFiles = linkedMapOf<String, File>()
    val toolFiles = linkedMapOf<String, File>()
    val privateIdentities = linkedMapOf<String, File>()
    val publicIdentities = linkedMapOf<String, File>()
    val seen = mutableSetOf<String>()

    ZipInputStream(BufferedInputStream(input)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val name = entry.name
            check(seen.add(name)) { "Duplicate backup entry: $name" }
            check(name == MANIFEST_ENTRY || name.startsWith(ICON_PREFIX) || name.startsWith(FILE_PREFIX) || name.startsWith(IDENTITY_PREFIX)) {
                "Unsupported backup entry: $name"
            }
            check(!name.startsWith('/') && ".." !in name.split('/')) { "Unsafe backup entry path." }
            val relative = when {
                name == MANIFEST_ENTRY -> "manifest.json"
                name.startsWith(ICON_PREFIX) -> name.removePrefix(ICON_PREFIX)
                name.startsWith(FILE_PREFIX) -> name.removePrefix(FILE_PREFIX)
                else -> name.removePrefix(IDENTITY_PREFIX)
            }
            require(relative.isNotBlank() && '/' !in relative) { "Invalid backup entry name: $name" }
            val destination = File(directory, name.replace('/', '_'))
            var written = 0L
            destination.outputStream().use { output ->
                val buffer = ByteArray(32768)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    written += count
                    totalBytes += count
                    check(written <= MAX_ENTRY_BYTES) { "Backup entry is too large: $name" }
                    check(totalBytes <= MAX_ARCHIVE_BYTES) { "Backup archive is too large." }
                    output.write(buffer, 0, count)
                }
            }
            when {
                name == MANIFEST_ENTRY -> manifest = backupJson.decodeFromString(destination.readText(Charsets.UTF_8))
                name.startsWith(ICON_PREFIX) -> iconFiles[relative] = destination
                name.startsWith(FILE_PREFIX) -> toolFiles[relative] = destination
                name.startsWith(IDENTITY_PREFIX) && relative.endsWith(".pub") ->
                    publicIdentities[relative.removeSuffix(".pub")] = destination
                name.startsWith(IDENTITY_PREFIX) -> privateIdentities[relative] = destination
            }
            zip.closeEntry()
        }
    }

    val data = requireNotNull(manifest) { "The backup archive has no manifest." }
    require(data.type == "full_backup") { "The archive is not a Generative full backup." }
    require(data.formatVersion == AppDataBackup.CURRENT_FORMAT_VERSION) { "Unsupported backup version." }
    val identities = privateIdentities.mapValues { (id, file) -> file to publicIdentities[id] }
    return ZipAppDataBackup(data, iconFiles, toolFiles, identities)
}
