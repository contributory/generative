package org.starfall.multigateway.ui.chat

import org.starfall.multigateway.ui.icons.FluentIcon

import org.starfall.multigateway.ui.components.AppBottomSheet
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import org.starfall.multigateway.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.starfall.multigateway.shared.model.ToolActivity
import org.starfall.multigateway.data.local.preferences.WordWrapMode
import org.starfall.multigateway.data.tools.ToolFiles
import org.starfall.multigateway.ui.tools.MediaViewer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import org.starfall.multigateway.ui.icons.Icons

private val toolJson = Json { prettyPrint = true }

/**
 * Holds the tool whose details sheet is open for one assistant message. The message card owns it so
 * the sheet survives the cards moving around underneath (a running tool finishing, the live
 * preview being replaced by the final content, older items leaving the preview window).
 */
internal class ToolSheetState {
    var selectedId by mutableStateOf<String?>(null)
}

internal val LocalToolSheetState = staticCompositionLocalOf<ToolSheetState?> { null }

@Composable
internal fun ToolDetailsSheetHost(state: ToolSheetState, activities: List<ToolActivity>) {
    val activity = state.selectedId?.let { id -> activities.find { it.id == id } } ?: return
    ToolDetailsSheet(activity = activity, onDismiss = { state.selectedId = null })
}

@Composable
fun ToolActivityCards(activities: List<ToolActivity>) {
    val hostedState = LocalToolSheetState.current
    var localSelectedId by remember { mutableStateOf<String?>(null) }
    fun select(id: String?) {
        if (hostedState != null) hostedState.selectedId = id else localSelectedId = id
    }
    val visibleActivities = activities.filterNot { it.name.endsWith(": connect") || it.name == "send_file" }
    val selectedActivity = if (hostedState != null) null
        else localSelectedId?.let { id -> visibleActivities.find { it.id == id } }

    if (visibleActivities.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            visibleActivities.forEach { activity ->
                key(activity.id) {
                    ToolActivityLabel(
                        activity = activity,
                        onClick = { select(activity.id) }
                    )
                }
            }
        }
    }

    selectedActivity?.let { activity ->
        ToolDetailsSheet(
            activity = activity,
            onDismiss = { select(null) }
        )
    }
}

@Composable
private fun ToolActivityLabel(
    activity: ToolActivity,
    onClick: () -> Unit
) {
    val running = activity.status == "running"
    val phase = if (running) {
        val transition = rememberInfiniteTransition(label = "tool-running")
        val wavePhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "tool-alpha"
        )
        wavePhase
    } else {
        0f
    }
    val color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val displayName = when (activity.name) {
        "generate_image" -> stringResource(if (running) R.string.tool_generating_image else R.string.tool_generate_image)
        "generate_video" -> stringResource(if (running) R.string.tool_generating_video else R.string.tool_generate_video)
        else -> activity.name.replace('_', ' ')
    }
    val label = buildAnnotatedString {
        displayName.forEachIndexed { index, character ->
            val alpha = if (running) 0.4f + 0.6f * ((sin(phase - index * 0.5f) + 1f) / 2f) else 1f
            withStyle(SpanStyle(color = color.copy(alpha = alpha))) { append(character) }
        }
    }
    Text(
        text = label,
        style = if (running) MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 7.dp, bottom = 7.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolDetailsSheet(
    activity: ToolActivity,
    onDismiss: () -> Unit
) {
    var argsTab by remember(activity.id) { mutableStateOf(0) }
    val context = LocalContext.current
    val revision by ToolFiles.revision.collectAsState()
    val responseText = remember(activity.response, activity.summary) {
        unescapeToolResponse(activity.response.ifBlank { activity.summary })
    }
    val fileNames = remember(activity.files) {
        activity.files.map { it.removePrefix("tool-file:") }.filter { it.isNotBlank() }.distinct()
    }
    var viewingFile by remember(activity.id) { mutableStateOf<File?>(null) }
    val responseIsJson = remember(responseText) { looksLikeJson(responseText) }
    val downloadResponse = rememberResponseDownload(
        fileName = "${
            activity.name.replace(
                Regex("[^A-Za-z0-9._-]+"),
                "_"
            )
        }_response.${if (responseIsJson) "json" else "txt"}",
        mime = if (responseIsJson) "application/json" else "text/plain"
    ) {
        // Build the export only after the user taps download; tool responses are never stored as text files.
        ResponseSource(responseText)
    }
    val canDownload = responseText.isNotBlank()

    AppBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Args",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = argsTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                divider = {}
            ) {
                Tab(
                    selected = argsTab == 0,
                    onClick = { argsTab = 0 },
                    text = { Text("Pretty") }
                )
                Tab(
                    selected = argsTab == 1,
                    onClick = { argsTab = 1 },
                    text = { Text("Raw") }
                )
            }

            Spacer(Modifier.height(10.dp))
            if (argsTab == 0) {
                PrettyArguments(activity.arguments)
            } else {
                RawValueBlock(activity.arguments.ifBlank { "{}" })
            }

            Spacer(Modifier.height(26.dp))
            Text(
                text = "Response",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            // Lazy chunked rendering: no character cap, only visible chunks are composed and highlighted.
            LazyCodeBlock(
                language = if (responseIsJson) "json" else "text",
                text = responseText.ifBlank {
                    if (activity.status == "running") "Waiting for tool response…" else "No response"
                },
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                onDownload = downloadResponse.takeIf { canDownload }
            )

            if (fileNames.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fileNames.forEach { name ->
                        val file = remember(name, revision) { ToolFiles(context).resolve(name) }
                        ToolFileRow(name, file) { file?.let { viewingFile = it } }
                    }
                }
            }
        }
    }
    viewingFile?.let { file -> MediaViewer(ToolFiles(context), file) { viewingFile = null } }
}


/**
 * Tool responses are sometimes wrapped in a JSON string or carry literal escape sequences ("\n", "\t", …),
 * which would otherwise show up as raw text on one line. Decode a JSON string wrapper first, then fall back
 * to unescaping the common sequences so newlines render as real line breaks.
 */
private fun unescapeToolResponse(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
        val decoded = runCatching {
            (Json.parseToJsonElement(trimmed) as? JsonPrimitive)?.takeIf { it.isString }?.content
        }.getOrNull()
        if (decoded != null) return decoded
    }
    if (!raw.contains('\\')) return raw
    val out = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '\\' && i + 1 < raw.length) {
            when (raw[i + 1]) {
                'n' -> {
                    out.append('\n'); i += 2
                }

                't' -> {
                    out.append('\t'); i += 2
                }

                'r' -> {
                    out.append('\r'); i += 2
                }

                'b' -> {
                    out.append('\b'); i += 2
                }

                'f' -> {
                    out.append('\u000C'); i += 2
                }

                '"' -> {
                    out.append('"'); i += 2
                }

                '/' -> {
                    out.append('/'); i += 2
                }

                '\\' -> {
                    out.append('\\'); i += 2
                }

                else -> {
                    out.append(c); i++
                }
            }
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

@Composable
private fun ToolFileRow(name: String, file: File?, onOpen: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = file != null, onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FluentIcon(Icons.Outlined.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(
                    name, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    file?.let { android.text.format.Formatter.formatFileSize(context, it.length()) } ?: "File deleted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrettyArguments(raw: String) {
    val parsed = remember(raw) { parseJsonObject(raw) }
    if (parsed == null || parsed.isEmpty()) {
        RawValueBlock(raw.ifBlank { "{}" })
        return
    }

    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        parsed.entries.forEachIndexed { index, (key, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(0.36f)
                        .border(0.5.dp, borderColor)
                        .padding(horizontal = 10.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(0.64f)
                        .border(0.5.dp, borderColor)
                        .padding(horizontal = 10.dp, vertical = 11.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = displayJsonValue(value),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Same rendering rules as chat code blocks: font size, highlighting and word wrap all come from [CodeBlockSurface]. */
@Composable
private fun RawValueBlock(value: String, onDownload: (() -> Unit)? = null) {
    val language = remember(value) { if (looksLikeJson(value)) "json" else "text" }
    CodeBlockSurface(
        language = language,
        code = value,
        originalCode = value,
        isStreaming = false,
        onExpansionChange = null,
        expanded = true,
        onDownload = onDownload
    )
}

private class ResponseSource(val text: String)

/** Returns a click handler that opens the system "save as" picker; nothing is written until a location is chosen. */
@Composable
private fun rememberResponseDownload(fileName: String, mime: String, source: () -> ResponseSource): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ResponseSource?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri: Uri? ->
        val data = pending
        pending = null
        if (uri == null || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                        out.bufferedWriter(Charsets.UTF_8).use { it.write(data.text) }
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (saved) "File saved" else "Could not save file", Toast.LENGTH_SHORT).show()
        }
    }
    return { pending = source(); launcher.launch(fileName) }
}

private fun parseJsonObject(raw: String): JsonObject? = runCatching {
    Json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject
}.getOrNull()

private fun displayJsonValue(value: JsonElement): String = when (value) {
    is JsonPrimitive -> if (value.isString) value.content else value.toString()
    else -> runCatching { toolJson.encodeToString(JsonElement.serializer(), value) }.getOrDefault(value.toString())
}

private fun looksLikeJson(value: String): Boolean {
    val first = value.firstOrNull { !it.isWhitespace() } ?: return false
    if (first != '{' && first != '[') return false
    // Parsing builds a whole tree; for large values only the outer shape is checked to avoid memory spikes.
    if (value.length > 100_000) {
        val last = value.lastOrNull { !it.isWhitespace() }
        return (first == '{' && last == '}') || (first == '[' && last == ']')
    }
    return runCatching { Json.parseToJsonElement(value) }.isSuccess
}
