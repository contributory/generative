package org.starfall.generative.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.storage.preferences.DEFAULT_MCP_DYNAMIC_MIN_MCPS
import org.starfall.generative.shared.storage.preferences.DEFAULT_MCP_DYNAMIC_MIN_TOOLS
import org.starfall.generative.shared.storage.preferences.DEFAULT_MCP_LIST_LIMIT
import org.starfall.generative.shared.storage.preferences.MAX_MCP_DYNAMIC_MIN
import org.starfall.generative.shared.storage.preferences.MAX_MCP_LIST_LIMIT
import org.starfall.generative.shared.storage.preferences.MAX_MCP_LOAD_LIMIT
import org.starfall.generative.shared.storage.preferences.McpAttachmentMode
import org.starfall.generative.shared.storage.preferences.McpAttachmentSettings
import org.starfall.generative.shared.storage.preferences.McpDynamicStrategy
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** MCP strategy controls embedded in the single General Settings "MCP & Tools" group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpAttachmentsPreferences(
    settings: McpAttachmentSettings,
    onChange: (McpAttachmentSettings) -> Unit
) {
    Text(stringResource(R.string.mcp_attachments_title), style = MaterialTheme.typography.titleMedium)

    var modeExpanded by remember { mutableStateOf(false) }
    val modeLabels = mapOf(
        McpAttachmentMode.DYNAMIC to stringResource(R.string.mcp_attachments_mode_dynamic),
        McpAttachmentMode.CLASSIC to stringResource(R.string.mcp_attachments_mode_classic)
    )
    ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = it }) {
        SelectableOutlinedTextField(
            value = modeLabels.getValue(settings.mode),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.mcp_attachments_mode_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .testTag("mcp-attachment-mode")
        )
        ExposedDropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
            modeLabels.forEach { (mode, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onChange(settings.copy(mode = mode))
                    modeExpanded = false
                })
            }
        }
    }

    if (settings.mode == McpAttachmentMode.CLASSIC) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(
                    value = settings.includeToolDescription,
                    role = Role.Switch,
                    onValueChange = { onChange(settings.copy(includeToolDescription = it)) }
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.mcp_attachments_include_description), style = MaterialTheme.typography.titleSmall)
            Switch(checked = settings.includeToolDescription, onCheckedChange = null)
        }
        return
    }

    McpNumberField(
        value = settings.minMcps,
        defaultValue = DEFAULT_MCP_DYNAMIC_MIN_MCPS,
        range = 0..MAX_MCP_DYNAMIC_MIN,
        tag = "mcp-attachment-min-mcps",
        label = stringResource(R.string.mcp_attachments_min_mcps)
    ) { onChange(settings.copy(minMcps = it)) }
    McpNumberField(
        value = settings.minTools,
        defaultValue = DEFAULT_MCP_DYNAMIC_MIN_TOOLS,
        range = 0..MAX_MCP_DYNAMIC_MIN,
        tag = "mcp-attachment-min-tools",
        label = stringResource(R.string.mcp_attachments_min_tools)
    ) { onChange(settings.copy(minTools = it)) }

    var strategyExpanded by remember { mutableStateOf(false) }
    val strategyLabels = mapOf(
        McpDynamicStrategy.OVERFLOW to stringResource(R.string.mcp_attachments_strategy_overflow),
        McpDynamicStrategy.ALL to stringResource(R.string.mcp_attachments_strategy_all)
    )
    ExposedDropdownMenuBox(expanded = strategyExpanded, onExpandedChange = { strategyExpanded = it }) {
        SelectableOutlinedTextField(
            value = strategyLabels.getValue(settings.strategy),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.mcp_attachments_dynamic_strategy_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .testTag("mcp-attachment-strategy")
        )
        ExposedDropdownMenu(expanded = strategyExpanded, onDismissRequest = { strategyExpanded = false }) {
            strategyLabels.forEach { (strategy, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onChange(settings.copy(strategy = strategy))
                    strategyExpanded = false
                })
            }
        }
    }

    McpNumberField(
        value = settings.listLimit,
        defaultValue = DEFAULT_MCP_LIST_LIMIT,
        range = 1..MAX_MCP_LIST_LIMIT,
        tag = "mcp-attachment-list-limit",
        label = stringResource(R.string.mcp_attachments_list_limit)
    ) { onChange(settings.copy(listLimit = it)) }
    McpNumberField(
        value = settings.loadLimit,
        defaultValue = settings.defaultLoadLimit,
        range = 1..MAX_MCP_LOAD_LIMIT,
        tag = "mcp-attachment-load-limit",
        label = stringResource(R.string.mcp_attachments_load_limit)
    ) { onChange(settings.withLoadLimit(it)) }
}

/** Blank means use [defaultValue]; a custom value is shown only when it differs from the default. */
@Composable
private fun McpNumberField(
    value: Int,
    defaultValue: Int,
    range: IntRange,
    tag: String,
    label: String,
    onValidValue: (Int) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(value.takeUnless { it == defaultValue }?.toString().orEmpty())
    }
    val parsed = text.toIntOrNull()?.takeIf { it in range }
    SelectableOutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= range.last.toString().length && input.all(Char::isDigit)) {
                text = input
                if (input.isBlank()) onValidValue(defaultValue)
                else input.toIntOrNull()?.takeIf { it in range }?.let(onValidValue)
            }
        },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.common_default_value_hint, defaultValue)) },
        isError = text.isNotBlank() && parsed == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}
