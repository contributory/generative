package org.starfall.generative.features.chatoptions.logic

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.starfall.generative.integrations.github.MAX_SKILL_FILE_BYTES

/** Only a bounded SKILL.md file is imported. No files are written until it validates. */
suspend fun readAgentSkillDocument(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val output = ByteArrayOutputStream()
    val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open selected file" }
    input.use { source ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            require(output.size() + read <= MAX_SKILL_FILE_BYTES) { "SKILL.md exceeds 256 KB" }
            output.write(buffer, 0, read)
        }
    }
    output.toByteArray().toString(Charsets.UTF_8)
}

suspend fun writeAgentSkillDocument(context: Context, uri: Uri, document: String) =
    withContext(Dispatchers.IO) {
        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
            "Cannot open export destination"
        }
        output.use { it.write(document.toByteArray(Charsets.UTF_8)) }
    }
