package org.starfall.generative.features.tools.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.text.selectAllOnTripleClick

private data class MediaVariableSuggestion(
    val value: String,
    val description: String,
    val pathHint: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaTemplateAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    stepNumber: Int,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    val cursor = fieldValue.selection.end.coerceIn(0, fieldValue.text.length)
    val opening = fieldValue.text.lastIndexOf("{{", (cursor - 1).coerceAtLeast(0))
    val closing = if (opening >= 0) fieldValue.text.indexOf("}}", opening + 2) else -1
    val activeToken = focused && fieldValue.selection.collapsed && opening >= 0 && (closing < 0 || closing >= cursor)
    val prefix = if (activeToken) fieldValue.text.substring(opening + 2, cursor).trim() else ""
    val suggestions = if (activeToken) {
        mediaVariableSuggestions(stepNumber).filter { suggestion ->
            suggestion.value.startsWith(prefix, ignoreCase = true) ||
                (suggestion.pathHint != null && prefix.startsWith(suggestion.value, ignoreCase = true))
        }.take(12)
    } else emptyList()

    ExposedDropdownMenuBox(
        expanded = suggestions.isNotEmpty(),
        onExpandedChange = {}
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValue = updated
                onValueChange(updated.text)
            },
            modifier = modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                .onFocusChanged { focused = it.isFocused }
                .selectAllOnTripleClick {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                },
            textStyle = textStyle,
            label = label,
            supportingText = supportingText,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            colors = OutlinedTextFieldDefaults.colors()
        )
        ExposedDropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = {},
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text(
                            "{{${suggestion.value}${suggestion.pathHint?.let { "<$it>" }.orEmpty()}}}",
                                fontFamily = FontFamily.Monospace
                            )
                            androidx.compose.material3.Text(
                                suggestion.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    onClick = {
                        val replacement = "{{${suggestion.value}}}"
                        val updatedText = fieldValue.text.replaceRange(opening, cursor, replacement)
                        val caret = opening + replacement.length - if (suggestion.pathHint != null) 2 else 0
                        fieldValue = TextFieldValue(updatedText, TextRange(caret))
                        onValueChange(updatedText)
                    }
                )
            }
        }
    }
}

@Composable
private fun mediaVariableSuggestions(stepNumber: Int): List<MediaVariableSuggestion> = buildList {
    add(MediaVariableSuggestion("prompt", stringResource(R.string.media_var_prompt)))
    add(MediaVariableSuggestion("model", stringResource(R.string.media_var_model)))
    add(MediaVariableSuggestion("api_token", stringResource(R.string.media_var_api_token)))
    add(MediaVariableSuggestion("input_files", stringResource(R.string.media_var_input_files)))
    add(MediaVariableSuggestion("input_file[0]", stringResource(R.string.media_var_first_input_file)))
    for (step in 1 until stepNumber) {
        add(MediaVariableSuggestion("step$step.response.body", stringResource(R.string.media_var_step_response_body, step)))
        add(MediaVariableSuggestion("step$step.response.body.", stringResource(R.string.media_var_step_response_body_path, step), pathHint = "path"))
        add(MediaVariableSuggestion("step$step.response.headers.", stringResource(R.string.media_var_step_response_header, step), pathHint = "header"))
        add(MediaVariableSuggestion("step$step.request.headers.", stringResource(R.string.media_var_step_request_header, step), pathHint = "header"))
        add(MediaVariableSuggestion("step$step.request.body.", stringResource(R.string.media_var_step_request_body, step), pathHint = "path"))
        add(MediaVariableSuggestion("step$step.url", stringResource(R.string.media_var_step_url, step)))
        add(MediaVariableSuggestion("step$step.request.method", stringResource(R.string.media_var_step_method, step)))
        add(MediaVariableSuggestion("step$step.response.status", stringResource(R.string.media_var_step_status, step)))
    }
}
