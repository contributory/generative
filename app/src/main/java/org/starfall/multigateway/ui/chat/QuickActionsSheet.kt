package org.starfall.multigateway.ui.chat

import org.starfall.multigateway.ui.components.AppBottomSheet
import org.starfall.multigateway.ui.components.EntityIcon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.data.model.*
import org.starfall.multigateway.ui.tools.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(onDismiss: () -> Unit) {
    val controls = LocalToolControls.current
    var machinesExpanded by remember { mutableStateOf(false) }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Tools Manage", style = MaterialTheme.typography.titleLarge)
            Text("System tools", style = MaterialTheme.typography.titleMedium)
            listOf("generate_image", "generate_video").forEach { name ->
                val cfg = controls.settings.system[name] ?: SystemToolConfig()
                val provider = controls.providers.firstOrNull { it.id == cfg.providerId }
                val model = provider?.config?.modelConfigs?.get(cfg.modelId)
                val modelName = model?.displayName?.ifBlank { cfg.modelId }
                    ?.takeIf { it.isNotBlank() }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (modelName != null) {
                            EntityIcon(
                                image = model.icon,
                                modifier = Modifier.size(44.dp),
                                text = modelName.take(2).uppercase(),
                                fallback = if (name == "generate_image") Icons.Outlined.Image else Icons.Outlined.Videocam,
                                matchName = modelName,
                                model = true
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (name == "generate_image") "Image Generation" else "Video Generation",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1
                            )
                            if (modelName != null) {
                                Text(
                                    modelName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Switch(
                            checked = cfg.enabled,
                            onCheckedChange = { controls.setSystem(name, cfg.copy(enabled = it)) }
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Send File", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "Let the AI show files from tool results in the chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = controls.settings.sendFileEnabled,
                        onCheckedChange = controls.setSendFile
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Remote Machines", style = MaterialTheme.typography.titleMedium)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { machinesExpanded = !machinesExpanded }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Computer, null, Modifier.size(44.dp).padding(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Machine tools", style = MaterialTheme.typography.titleSmall)
                        Text("Choose which machines the model may use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (machinesExpanded) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (machinesExpanded) {
                if (controls.machines.isEmpty()) {
                    Text("No remote machines configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                controls.machines.forEach { machine ->
                    val icon = if (machine.type.name == "GCLOUD_SHELL") Icons.Outlined.Cloud else Icons.Outlined.Computer
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, Modifier.size(44.dp).padding(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(machine.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Switch(
                                    checked = remoteMachineEnabled(controls.settings, machine),
                                    onCheckedChange = { controls.setMachine(machine.id, it) }
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("MCP servers", style = MaterialTheme.typography.titleMedium)
            if (controls.servers.isEmpty()) {
                Text(
                    "No MCP servers configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            controls.servers.forEach { server ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        EntityIcon(
                            image = server.icon,
                            modifier = Modifier.size(44.dp),
                            text = server.name.take(2).uppercase(),
                            fallback = Icons.Outlined.Extension,
                            matchName = server.name
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                server.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                server.resolvedUrl().orEmpty().ifBlank { "MCP server" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${server.cachedTools?.size ?: 0} cached tools",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = controls.settings.quickMcp[server.id] != false,
                            onCheckedChange = { controls.setMcp(server.id, it) }
                        )
                    }
                }
            }
        }
    }
}
