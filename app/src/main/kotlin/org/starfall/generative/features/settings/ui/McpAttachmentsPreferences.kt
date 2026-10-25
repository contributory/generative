package org.starfall.generative.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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

/** Content of the "MCP Attachments" preferences section. */
@Composable
fun ColumnScope.McpAttachmentsPreferences(
    settings: McpAttachmentSettings,
    onChange: (McpAttachmentSettings) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            McpAttachmentMode.DYNAMIC to R.string.mcp_attachments_mode_dynamic,
            McpAttachmentMode.CLASSIC to R.string.mcp_attachments_mode_classic
        ).forEach { (mode, label) ->
            FilterChip(
                selected = settings.mode == mode,
                onClick = { onChange(settings.copy(mode = mode)) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.weight(1f).testTag("mcp-attachment-mode-${mode.name.lowercase()}")
            )
        }
    }
    if (settings.mode == McpAttachmentMode.CLASSIC) {
        Text(stringResource(R.string.mcp_attachments_classic_description), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PreferenceToggle(
            stringResource(R.string.mcp_attachments_include_description),
            stringResource(R.string.mcp_attachments_include_description_summary),
            settings.includeToolDescription
        ) { onChange(settings.copy(includeToolDescription = it)) }
        return
    }

    Text(stringResource(R.string.mcp_attachments_dynamic_description), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    McpNumberField(
        value = settings.minMcps, range = 0..MAX_MCP_DYNAMIC_MIN, tag = "mcp-attachment-min-mcps",
        label = stringResource(R.string.mcp_attachments_min_mcps),
        hint = stringResource(R.string.mcp_attachments_min_mcps_hint, DEFAULT_MCP_DYNAMIC_MIN_MCPS)
    ) { onChange(settings.copy(minMcps = it)) }
    McpNumberField(
        value = settings.minTools, range = 0..MAX_MCP_DYNAMIC_MIN, tag = "mcp-attachment-min-tools",
        label = stringResource(R.string.mcp_attachments_min_tools),
        hint = stringResource(R.string.mcp_attachments_min_tools_hint, DEFAULT_MCP_DYNAMIC_MIN_TOOLS)
    ) { onChange(settings.copy(minTools = it)) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            McpDynamicStrategy.OVERFLOW to R.string.mcp_attachments_strategy_overflow,
            McpDynamicStrategy.ALL to R.string.mcp_attachments_strategy_all
        ).forEach { (strategy, label) ->
            FilterChip(
                selected = settings.strategy == strategy,
                onClick = { onChange(settings.copy(strategy = strategy)) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.weight(1f).testTag("mcp-attachment-strategy-${strategy.name.lowercase()}")
            )
        }
    }
    Text(
        stringResource(
            if (settings.strategy == McpDynamicStrategy.OVERFLOW) R.string.mcp_attachments_strategy_overflow_description
            else R.string.mcp_attachments_strategy_all_description
        ),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    McpNumberField(
        value = settings.listLimit, range = 1..MAX_MCP_LIST_LIMIT, tag = "mcp-attachment-list-limit",
        label = stringResource(R.string.mcp_attachments_list_limit),
        hint = stringResource(R.string.common_number_range_default, DEFAULT_MCP_LIST_LIMIT)
    ) { onChange(settings.copy(listLimit = it)) }
    McpNumberField(
        value = settings.loadLimit, range = 1..MAX_MCP_LOAD_LIMIT, tag = "mcp-attachment-load-limit",
        label = stringResource(R.string.mcp_attachments_load_limit),
        hint = stringResource(R.string.common_number_range_default, settings.defaultLoadLimit)
    ) { onChange(settings.withLoadLimit(it)) }
}

/** Numeric field that only saves values inside [range]; anything else is shown as an error. */
@Composable
private fun McpNumberField(
    value: Int,
    range: IntRange,
    tag: String,
    label: String,
    hint: String,
    onValidValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()?.takeIf { it in range }
    SelectableOutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= range.last.toString().length && input.all(Char::isDigit)) {
                text = input
                input.toIntOrNull()?.takeIf { it in range }?.let(onValidValue)
            }
        },
        label = { Text(label) },
        supportingText = {
            Text(
                if (parsed != null) hint else stringResource(
                    if (range.first == 0) R.string.mcp_attachments_number_invalid_from_zero else R.string.common_number_range_invalid,
                    range.last
                )
            )
        },
        isError = parsed == null, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}
