package org.starfall.multigateway.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.data.local.preferences.DEFAULT_MESSAGE_FONT_SIZE
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.starfall.multigateway.R
import org.starfall.multigateway.data.local.preferences.AppPreferences
import org.starfall.multigateway.data.local.preferences.WordWrapMode
import org.starfall.multigateway.data.local.preferences.MessageFontFamily
import org.starfall.multigateway.data.local.preferences.CodeBlockDisplayMode
import org.starfall.multigateway.ui.components.SelectableOutlinedTextField
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CodeRenderingPreferences(
    preferences: AppPreferences,
    onModeChange: (WordWrapMode) -> Unit,
    onColumnChange: (Int) -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onFontFamilyChange: (MessageFontFamily) -> Unit,
    onCodeBlockDisplayModeChange: (CodeBlockDisplayMode) -> Unit = {},
    onCodeBlockMaxLinesChange: (Int) -> Unit = {},
    onCodeBlockIncludeUndefinedChange: (Boolean) -> Unit = {},
    includeTextAppearance: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        WordWrapMode.OFF to stringResource(R.string.word_wrap_off),
        WordWrapMode.VIEWPORT to stringResource(R.string.word_wrap_viewport),
        WordWrapMode.COLUMN to stringResource(R.string.word_wrap_column),
        WordWrapMode.BOUNDED to stringResource(R.string.word_wrap_bounded)
    )
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        SelectableOutlinedTextField(
            value = labels.getValue(preferences.wordWrapMode), onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.word_wrap)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("word-wrap-mode")
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            labels.forEach { (mode, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onModeChange(mode); expanded = false })
            }
        }
    }
    if (preferences.wordWrapMode == WordWrapMode.COLUMN || preferences.wordWrapMode == WordWrapMode.BOUNDED) {
        var column by remember(preferences.wordWrapColumn) { mutableStateOf(preferences.wordWrapColumn.toString()) }
        val parsed = column.toIntOrNull()?.takeIf { it > 0 }
        SelectableOutlinedTextField(
            value = column,
            onValueChange = { text ->
                if (text.all(Char::isDigit)) {
                    column = text
                    text.toIntOrNull()?.takeIf { it > 0 }?.let(onColumnChange)
                }
            },
            label = { Text(stringResource(R.string.word_wrap_column_count)) },
            supportingText = { Text(stringResource(if (parsed == null) R.string.word_wrap_column_invalid else R.string.word_wrap_column_hint)) },
            isError = parsed == null, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("word-wrap-column")
        )
    }
    PreferenceToggle(stringResource(R.string.code_preview), stringResource(R.string.code_preview_hint),
        preferences.codePreviewEnabled, onPreviewChange)
    var codeBlockExpanded by remember { mutableStateOf(false) }
    val codeBlockLabels = mapOf(
        CodeBlockDisplayMode.SHOW_ALL to stringResource(R.string.code_block_show_all),
        CodeBlockDisplayMode.EXTENDABLE to stringResource(R.string.code_block_extendable),
        CodeBlockDisplayMode.SHOW_IN_DRAWER to stringResource(R.string.code_block_show_in_drawer)
    )
    ExposedDropdownMenuBox(codeBlockExpanded, { codeBlockExpanded = !codeBlockExpanded }) {
        SelectableOutlinedTextField(
            value = codeBlockLabels.getValue(preferences.codeBlockDisplayMode),
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.code_block_display)) },
            supportingText = { Text(stringResource(R.string.code_block_display_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(codeBlockExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("code-block-display-mode")
        )
        ExposedDropdownMenu(codeBlockExpanded, { codeBlockExpanded = false }) {
            codeBlockLabels.forEach { (mode, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onCodeBlockDisplayModeChange(mode)
                    codeBlockExpanded = false
                })
            }
        }
    }
    if (preferences.codeBlockDisplayMode != CodeBlockDisplayMode.SHOW_ALL) {
        var maxLines by remember(preferences.codeBlockMaxLines) {
            mutableStateOf(preferences.codeBlockMaxLines.toString())
        }
        val parsedMaxLines = maxLines.toIntOrNull()?.takeIf { it > 0 }
        SelectableOutlinedTextField(
            value = maxLines,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) {
                    maxLines = value
                    value.toIntOrNull()?.takeIf { it in 1..10_000 }?.let(onCodeBlockMaxLinesChange)
                }
            },
            label = { Text(stringResource(R.string.code_block_max_lines)) },
            supportingText = {
                Text(stringResource(if (parsedMaxLines == null) R.string.code_block_max_lines_invalid
                    else R.string.code_block_max_lines_hint))
            },
            isError = parsedMaxLines == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("code-block-max-lines")
        )
        PreferenceToggle(
            stringResource(R.string.code_block_include_undefined),
            stringResource(R.string.code_block_include_undefined_hint),
            preferences.codeBlockIncludeUndefined,
            onCodeBlockIncludeUndefinedChange
        )
    }
    if (includeTextAppearance) {
        TextAppearancePreferences(preferences, onFontSizeChange, onFontFamilyChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextAppearancePreferences(
    preferences: AppPreferences,
    onFontSizeChange: (Int) -> Unit,
    onFontFamilyChange: (MessageFontFamily) -> Unit,
    onFontSizeCustomChange: (Boolean) -> Unit = {}
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    PreferenceToggle(
        stringResource(R.string.message_text_size),
        stringResource(R.string.message_text_size_custom_hint),
        preferences.messageFontSizeCustom,
        onFontSizeCustomChange
    )
    if (preferences.messageFontSizeCustom) {
        Text(stringResource(R.string.message_text_size_hint, preferences.messageFontSize),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = preferences.messageFontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.roundToInt()) },
                valueRange = 12f..24f,
                steps = 11,
                modifier = Modifier.weight(1f).testTag("message-font-size")
            )
            IconButton(
                onClick = { onFontSizeChange(DEFAULT_MESSAGE_FONT_SIZE) },
                enabled = preferences.messageFontSize != DEFAULT_MESSAGE_FONT_SIZE,
                modifier = Modifier.size(36.dp).testTag("message-font-size-reset")
            ) {
                Icon(Icons.Outlined.RestartAlt, stringResource(R.string.message_text_size_reset),
                    modifier = Modifier.size(20.dp))
            }
        }
    }

    Text(stringResource(R.string.message_font_family), style = MaterialTheme.typography.titleMedium)
    var fontExpanded by remember { mutableStateOf(false) }
    val fontLabels = mapOf(
        MessageFontFamily.DEFAULT to stringResource(R.string.font_family_default),
        MessageFontFamily.SANS_SERIF to stringResource(R.string.font_family_sans_serif),
        MessageFontFamily.SERIF to stringResource(R.string.font_family_serif),
        MessageFontFamily.MONOSPACE to stringResource(R.string.font_family_monospace),
        MessageFontFamily.CURSIVE to stringResource(R.string.font_family_cursive)
    )
    ExposedDropdownMenuBox(fontExpanded, { fontExpanded = !fontExpanded }) {
        SelectableOutlinedTextField(
            value = fontLabels.getValue(preferences.messageFontFamily), onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.font_family_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fontExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("message-font-family")
        )
        ExposedDropdownMenu(fontExpanded, { fontExpanded = false }) {
            fontLabels.forEach { (family, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onFontFamilyChange(family)
                    fontExpanded = false
                })
            }
        }
    }
}
