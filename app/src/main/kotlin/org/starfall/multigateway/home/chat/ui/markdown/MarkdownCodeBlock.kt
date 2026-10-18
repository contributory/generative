package org.starfall.multigateway.home.chat.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.starfall.multigateway.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.starfall.multigateway.preview.CodePreviewActivity
import org.starfall.multigateway.preview.canPreviewCode
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.shared.storage.preferences.CodeBlockDisplayMode
import org.starfall.multigateway.shared.storage.preferences.WordWrapMode
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.home.chat.ui.rememberGlyphTarget
import org.starfall.multigateway.home.chat.ui.rememberStreamingTextPresentation
import org.starfall.multigateway.shared.code.highlightedCode
import org.starfall.multigateway.home.chat.ui.glyphTarget

internal val LocalCodeRenderingPreferences = staticCompositionLocalOf { AppPreferences() }

/** Width of the text area, excluding code block padding. Null means no wrapping. */
internal fun codeWrapWidth(mode: WordWrapMode, viewport: Float, column: Float): Float? = when (mode) {
    WordWrapMode.OFF -> null
    WordWrapMode.VIEWPORT -> viewport
    WordWrapMode.COLUMN -> column
    WordWrapMode.BOUNDED -> minOf(viewport, column)
}

@Composable
internal fun RenderCodeBlock(language: String, code: String, isStreaming: Boolean) {
    val context = LocalContext.current
    val preferences = LocalCodeRenderingPreferences.current
    val lineCount = code.lineSequence().count()
    val managesUndefined = preferences.codeBlockIncludeUndefined || language.isNotBlank()
    val isLong = !isStreaming && managesUndefined && lineCount > preferences.codeBlockMaxLines
    val displayMode = preferences.codeBlockDisplayMode
    var expanded by remember(language, code) { mutableStateOf(false) }
    var drawerVisible by remember(language, code) { mutableStateOf(false) }

    if (displayMode == CodeBlockDisplayMode.SHOW_IN_DRAWER && isLong) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        ) {
            TextButton(
                onClick = { drawerVisible = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text("${language.ifBlank { "code" }} | $lineCount lines", fontFamily = FontFamily.Monospace)
            }
        }
        if (drawerVisible) {
            CodeBlockDrawer(
                language = language,
                code = code,
                isStreaming = isStreaming,
                onDismiss = { drawerVisible = false }
            )
        }
        return
    }

    val visibleCode = if (displayMode == CodeBlockDisplayMode.EXTENDABLE && isLong && !expanded) {
        code.lineSequence().take(preferences.codeBlockMaxLines).joinToString("\n")
    } else code
    CodeBlockSurface(
        language = language,
        code = visibleCode,
        originalCode = code,
        isStreaming = isStreaming,
        onExpansionChange = if (displayMode == CodeBlockDisplayMode.EXTENDABLE && isLong) {
            { expanded = !expanded }
        } else null,
        expanded = expanded
    )
}

@Composable
private fun CodeBlockDrawer(
    language: String,
    code: String,
    isStreaming: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(language.ifBlank { "code" }, Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                    IconButton(onClick = onDismiss) {
                        FluentIcon(Icons.Outlined.Close, stringResource(R.string.code_block_close))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    CodeBlockSurface(
                        language = language,
                        code = code,
                        originalCode = code,
                        isStreaming = isStreaming,
                        onExpansionChange = null,
                        expanded = true
                    )
                }
            }
        }
    }
}

@Composable
internal fun CodeBlockSurface(
    language: String,
    code: String,
    originalCode: String,
    isStreaming: Boolean,
    onExpansionChange: (() -> Unit)?,
    expanded: Boolean,
    onDownload: (() -> Unit)? = null
) {
    val preferences = LocalCodeRenderingPreferences.current
    var sessionWrap by remember(language, originalCode) { mutableStateOf(false) }
    val mode = if (preferences.wordWrapMode == WordWrapMode.OFF && sessionWrap) WordWrapMode.VIEWPORT else preferences.wordWrapMode
    val wrapCode = mode != WordWrapMode.OFF
    val textStyle = codeTextStyle()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val characterWidth = remember(textStyle, density) { (measurer.measure("0".repeat(100), textStyle, softWrap = false).size.width / 100f).coerceAtLeast(1f) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val highlighted by produceState(AnnotatedString(code), code, language, dark) {
        value = withContext(Dispatchers.Default) { highlightedCode(language, code, dark) }
    }
    val codeScrollState = rememberScrollState()
    val codePresentation = rememberStreamingTextPresentation(code)
    val glyphTarget = rememberGlyphTarget()
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))) {
        Column {
            CodeBlockHeader(
                language = language,
                isStreaming = isStreaming,
                originalCode = originalCode,
                sessionWrap = sessionWrap,
                onToggleWrap = { sessionWrap = !sessionWrap },
                onDownload = onDownload
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val viewport = with(density) { (maxWidth - 24.dp).coerceAtLeast(1.dp).toPx() }
                val target = codeWrapWidth(mode, viewport, (characterWidth * preferences.wordWrapColumn).coerceAtMost(200_000f))
                val bodyModifier = if (target == null) Modifier else Modifier.width(with(density) { target.toDp() } + 24.dp)
                Box(Modifier.fillMaxWidth().then(if (mode == WordWrapMode.OFF || mode == WordWrapMode.COLUMN) Modifier.horizontalScroll(codeScrollState) else Modifier)) {
                    Text(highlighted, style = textStyle,
                        softWrap = wrapCode, modifier = bodyModifier.padding(12.dp).then(codePresentation.modifier).glyphTarget(glyphTarget),
                        onTextLayout = { codePresentation.onTextLayout(it); glyphTarget?.layout = it }, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            onExpansionChange?.let { toggle ->
                TextButton(onClick = toggle, modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)) {
                    Text(stringResource(if (expanded) R.string.code_block_collapse else R.string.code_block_extend))
                }
            }
        }
    }
}

@Composable
internal fun codeTextStyle(): TextStyle {
    val size = (LocalCodeRenderingPreferences.current.effectiveMessageFontSize * 0.78f).sp
    return MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = size, lineHeight = size * 1.44f)
}

/** Header shared by every code surface: language badge, wrap toggle, preview, download and copy. */
@Composable
internal fun CodeBlockHeader(
    language: String,
    isStreaming: Boolean,
    originalCode: String,
    sessionWrap: Boolean,
    onToggleWrap: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val preferences = LocalCodeRenderingPreferences.current
    var copied by remember(language, originalCode) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(language.ifBlank { "code" }, style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (isStreaming) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (preferences.wordWrapMode == WordWrapMode.OFF) {
                IconButton(onClick = onToggleWrap, modifier = Modifier.size(28.dp)) {
                    FluentIcon(Icons.Outlined.WrapText, stringResource(if (sessionWrap) R.string.code_wrap_disable else R.string.code_wrap_enable),
                        tint = if (sessionWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                }
            }
            if (preferences.codePreviewEnabled && canPreviewCode(language)) {
                IconButton(onClick = { CodePreviewActivity.open(context, language, originalCode) }, modifier = Modifier.size(28.dp)) {
                    FluentIcon(Icons.Outlined.Visibility, stringResource(R.string.preview_code),
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                }
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                    FluentIcon(Icons.Outlined.Download, stringResource(R.string.file_download),
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = {
                // Very large texts exceed the clipboard binder limit; fail softly instead of crashing.
                val result = runCatching {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", originalCode))
                }
                if (result.isSuccess) copied = true
                Toast.makeText(context, if (result.isSuccess) "Code copied" else "Too large to copy", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.size(28.dp)) {
                FluentIcon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, "Copy code",
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
        }
    }
}
