package org.starfall.generative.home.chat.ui.markdown

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.starfall.generative.R
import org.starfall.generative.shared.storage.preferences.CodeBlockDisplayMode
import org.starfall.generative.shared.ui.code.CodeBlockSurface
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.nestedContainerBorder
import org.starfall.generative.shared.ui.layout.nestedContainerColor

/** Chat-only code block logic: long-block display modes (extendable / drawer) on top of the shared [CodeBlockSurface]. */
@Composable
internal fun RenderCodeBlock(language: String, code: String, isStreaming: Boolean) {
    val preferences = LocalCodeRenderingPreferences.current
    val lineCount = code.lineSequence().count()
    val codeLanguageFallback = stringResource(R.string.code_language_fallback)
    val managesUndefined = preferences.codeBlockIncludeUndefined || language.isNotBlank()
    val isLong = !isStreaming && managesUndefined && lineCount > preferences.codeBlockMaxLines
    val displayMode = preferences.codeBlockDisplayMode
    var expanded by remember(language, code) { mutableStateOf(false) }
    var drawerVisible by remember(language, code) { mutableStateOf(false) }

    if (displayMode == CodeBlockDisplayMode.SHOW_IN_DRAWER && isLong) {
        Surface(
            onClick = { drawerVisible = true },
            shape = RoundedCornerShape(10.dp),
            color = nestedContainerColor(),
            border = nestedContainerBorder(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.code_block_summary, language.ifBlank { codeLanguageFallback }, lineCount),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        if (drawerVisible) {
            CodeBlockDialog(
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
        modifier = Modifier.padding(vertical = 4.dp),
        originalCode = code,
        isStreaming = isStreaming,
        onExpansionChange = if (displayMode == CodeBlockDisplayMode.EXTENDABLE && isLong) {
            { expanded = !expanded }
        } else null,
        expanded = expanded
    )
}

@Composable
private fun CodeBlockDialog(
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
                        modifier = Modifier.padding(vertical = 4.dp),
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
