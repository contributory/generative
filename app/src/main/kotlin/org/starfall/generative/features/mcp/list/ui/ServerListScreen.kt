package org.starfall.generative.features.mcp.list.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ScrollMoveMode
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import java.util.UUID
import org.starfall.generative.features.mcp.shared.ui.McpUnifiedCard
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.McpProtocol
import org.starfall.generative.shared.model.ToolDefinition
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.lists.FadeGridListContent
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.lists.moved
import org.starfall.generative.shared.ui.layout.windowHeightIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    mcpServers: List<McpInfo>, isGridView: Boolean = false, onToggleGridView: ((Boolean) -> Unit)? = null,
    toolsCache: Map<String, List<ToolDefinition>>, toolErrors: Map<String, String>, toolsLoading: Set<String>,
    toolSettings: ToolSettings, onSetToolEnabled: (String, String, Boolean) -> Unit,
    onSetMcpEnabled: (String, Boolean) -> Unit,
    onDeleteMcpServer: (String) -> Unit, onReorderMcpServers: (List<String>) -> Unit,
    onOpenEditor: (McpInfo, Boolean) -> Unit, onBack: () -> Unit
) {
    var deletingServerId by remember { mutableStateOf<String?>(null) }
    var selectedToolError by remember { mutableStateOf<Pair<String, String>?>(null) }
    var orderedServers by remember(mcpServers) { mutableStateOf(mcpServers) }
    val context = LocalContext.current
    Scaffold(topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.mcp_servers_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(stringResource(R.string.mcp_servers_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }, navigationIcon = { IconButton(onClick = onBack) { FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                actions = {
                    IconButton(onClick = { onToggleGridView?.invoke(!isGridView) }) {
                        FluentIcon(if (isGridView) Icons.Default.List else Icons.Default.GridView, stringResource(R.string.toggle_grid_list))
                    }
                    IconButton(onClick = {
                        onOpenEditor(McpInfo(id = UUID.randomUUID().toString(), name = "", protocol = McpProtocol.STREAMABLE_HTTP,
                            url = null, headers = emptyMap()), true)
                    }) { FluentIcon(Icons.Default.Add, stringResource(R.string.common_add)) }
                })
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (mcpServers.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FluentIcon(Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.no_mcp_servers), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_mcp_servers_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                } else FadeGridListContent(isGrid = isGridView, modifier = Modifier.fillMaxSize()) { gridMode ->
                    val gridState = rememberLazyGridState()
                    val reorderState = rememberReorderableLazyGridState(gridState, scrollMoveMode = ScrollMoveMode.INSERT) { from, to ->
                        orderedServers = orderedServers.moved(from.index, to.index)
                    }
                    LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(if (gridMode) 2 else 1),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(orderedServers, key = { _, item -> item.id }) { _, server ->
                            ReorderableItem(reorderState, key = server.id) { _ ->
                                McpUnifiedCard(server = server, isGrid = gridMode,
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStopped = { onReorderMcpServers(orderedServers.map { it.id }) }
                                    ),
                                    toolCount = (toolsCache[server.id] ?: server.cachedTools)?.size, toolError = toolErrors[server.id], loading = server.id in toolsLoading,
                                    isEnabled = toolSettings.quickMcp[server.id] != false,
                                    onToggleEnabled = { onSetMcpEnabled(server.id, toolSettings.quickMcp[server.id] == false) },
                                    onToolErrorClick = { selectedToolError = server.name to it },
                                    onEdit = { onOpenEditor(server, false) }, onDelete = { deletingServerId = server.id })
                            }
                        }
                    }
                }
            }
    }

    if (deletingServerId != null) AlertDialog(onDismissRequest = { deletingServerId = null },
        text = { Text(stringResource(R.string.delete_mcp_server_message)) },
        confirmButton = { Button(onClick = { deletingServerId?.let(onDeleteMcpServer); deletingServerId = null },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.common_delete)) } },
        dismissButton = { TextButton(onClick = { deletingServerId = null }) { Text(stringResource(R.string.common_cancel)) } })
    selectedToolError?.let { (serverName, error) ->
        AlertDialog(onDismissRequest = { selectedToolError = null }, title = { Text(stringResource(R.string.mcp_tool_discovery_error_title, serverName)) },
            text = {
                Box(Modifier.fillMaxWidth().windowHeightIn(maxFraction = 0.6f).verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(error, style = MaterialTheme.typography.bodySmall) }
                }
            }, confirmButton = { TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.mcp_tool_discovery_error_clipboard), error))
            }) { Text(stringResource(R.string.common_copy)) } },
            dismissButton = { TextButton(onClick = { selectedToolError = null }) { Text(stringResource(R.string.common_close)) } })
    }
}
