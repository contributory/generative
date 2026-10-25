package org.starfall.generative.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Selects and enables the MCP server that supplies web-search tools. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchProviderSettings(
    servers: List<McpInfo>,
    settings: ToolSettings,
    onProviderChange: (String?) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedProvider = servers.firstOrNull { it.id == settings.webSearchProviderId }
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.tool_web_search_provider_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.tool_web_search_provider_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedProvider != null) {
                Switch(
                    checked = settings.webSearchEnabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            SelectableOutlinedTextField(
                value = selectedProvider?.name ?: stringResource(R.string.tool_web_search_provider_none),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.tool_web_search_provider_select)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tool_web_search_provider_none)) },
                    onClick = {
                        onProviderChange(null)
                        expanded = false
                    }
                )
                servers.forEach { server ->
                    DropdownMenuItem(
                        text = { Text(server.name) },
                        onClick = {
                            onProviderChange(server.id)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (servers.isEmpty()) {
            Text(
                stringResource(R.string.tool_web_search_provider_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
