package org.starfall.generative

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.features.appdata.logic.readZipBackup
import org.starfall.generative.features.appdata.logic.writeZipBackup
import org.starfall.generative.features.appdata.model.AppDataBackup
import org.starfall.generative.shared.icons.model.IconRule
import org.starfall.generative.shared.icons.model.IconStoreState
import org.starfall.generative.shared.model.ToolSettings

class ZipAppDataTransferTest {
    @Test fun completeBackupRoundTripsManifestAndBinaryAssets() {
        val source = Files.createTempDirectory("generative-backup-source").toFile()
        val staging = Files.createTempDirectory("generative-backup-staging").toFile()
        try {
            val icon = source.resolve("icon-a.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val upload = source.resolve("upload.pdf").apply { writeBytes(byteArrayOf(4, 5, 6, 7)) }
            val identity = source.resolve("identity").apply { writeText("PRIVATE KEY") }
            val publicIdentity = source.resolve("identity.pub").apply { writeText("ssh-rsa public") }
            val backup = AppDataBackup(
                exportedAt = "2026-10-25T00:00:00Z",
                toolSettings = ToolSettings(sendFileEnabled = false),
                icons = IconStoreState(
                    rules = listOf(IconRule(pattern = "Provider", image = icon.name)),
                    files = listOf(icon.name)
                ),
                storedFileNames = listOf(upload.name),
                appPreferences = AppDataBackup(exportedAt = "2026-10-25T00:00:00Z").appPreferences.copy(featuresLogoImage = icon.name),
                mcpOAuthTokens = mapOf("server" to JsonObject(emptyMap()))
            )

            val bytes = ByteArrayOutputStream().also {
                writeZipBackup(
                    it,
                    backup,
                    iconFiles = mapOf(icon.name to icon),
                    toolFiles = mapOf(upload.name to upload),
                    identityFiles = mapOf("identity-id" to (identity to publicIdentity))
                )
            }.toByteArray()
            val restored = readZipBackup(ByteArrayInputStream(bytes), staging)

            assertEquals(backup, restored.data)
            assertEquals(byteArrayOf(1, 2, 3).toList(), restored.iconFiles.getValue(icon.name).readBytes().toList())
            assertEquals(byteArrayOf(4, 5, 6, 7).toList(), restored.toolFiles.getValue(upload.name).readBytes().toList())
            assertEquals("PRIVATE KEY", restored.remoteIdentityFiles.getValue("identity-id").first.readText())
            assertEquals("ssh-rsa public", restored.remoteIdentityFiles.getValue("identity-id").second!!.readText())
            assertTrue(bytes.copyOfRange(0, 2).contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte())))
        } finally {
            source.deleteRecursively()
            staging.deleteRecursively()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsBackupEntriesLargerThanImportLimitDuringExport() {
        val largeFile = Files.createTempFile("generative-oversized-backup", ".bin").toFile()
        try {
            RandomAccessFile(largeFile, "rw").use { it.setLength(256L * 1024 * 1024 + 1) }
            writeZipBackup(
                ByteArrayOutputStream(),
                AppDataBackup(exportedAt = "2026-10-25T00:00:00Z"),
                iconFiles = emptyMap(),
                toolFiles = mapOf(largeFile.name to largeFile),
                identityFiles = emptyMap()
            )
        } finally {
            largeFile.delete()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsPathTraversalEntries() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../manifest.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val staging = Files.createTempDirectory("generative-invalid-backup").toFile()
        try {
            readZipBackup(ByteArrayInputStream(bytes), staging)
        } finally {
            staging.deleteRecursively()
        }
    }
}
