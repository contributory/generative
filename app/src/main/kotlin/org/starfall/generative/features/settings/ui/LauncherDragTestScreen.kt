package org.starfall.generative.features.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.shared.uiextensions.launcher.LauncherDragDropHost
import org.starfall.generative.shared.uiextensions.launcher.LauncherDragDropState
import org.starfall.generative.shared.uiextensions.launcher.LauncherDragOutcome
import org.starfall.generative.shared.uiextensions.launcher.LauncherDragSession
import org.starfall.generative.shared.uiextensions.launcher.LauncherGridGeometry
import org.starfall.generative.shared.uiextensions.launcher.LauncherCell
import org.starfall.generative.shared.uiextensions.launcher.LauncherFolderExitEffect
import org.starfall.generative.shared.uiextensions.launcher.LauncherHoverEffect
import org.starfall.generative.shared.uiextensions.launcher.LauncherReorderLayout
import org.starfall.generative.shared.uiextensions.launcher.launcherDragAutoScroll
import org.starfall.generative.shared.uiextensions.launcher.launcherDragSource
import org.starfall.generative.shared.uiextensions.launcher.launcherDropTarget
import org.starfall.generative.shared.uiextensions.launcher.rememberLauncherDragDropState

private const val Workspace = "workspace"
private const val Folder = "folder"
private const val FolderItem = "folder-alpha"

private fun insertionAfter(
    drag: LauncherDragSession<String>,
    cellKey: String,
    positions: Map<String, Rect>
): Boolean = positions[cellKey]?.let { bounds ->
    LauncherGridGeometry.closestSlot(
        drag.visualCenter,
        listOf(LauncherCell(cellKey, bounds)),
        horizontal = true
    )?.after
} ?: true

private fun sampleLayout(): Map<String, List<String>> = mapOf(
    Workspace to listOf(FolderItem) + (1..18).map { "Tile $it" },
    Folder to (19..24).map { "Tile $it" }
)

/**
 * Isolated manual test harness for the reusable launcher drag system.
 * No provider data, repository or preferences are read or written.
 */
@Composable
fun LauncherDragTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state = rememberLauncherDragDropState<String>()
    val layout = remember { LauncherReorderLayout({ it }, sampleLayout()) }
    var openFolder by remember { mutableStateOf(false) }
    var dragFromFolder by remember { mutableStateOf(false) }
    val initial = stringResource(R.string.launcher_drag_test_initial)
    val cancelled = stringResource(R.string.launcher_drag_test_cancelled)
    val reset = stringResource(R.string.launcher_drag_test_reset_result)
    var lastOutcome by remember { mutableStateOf(initial) }
    val positions = remember { mutableMapOf<String, Rect>() }

    fun finish(outcome: LauncherDragOutcome) {
        if (outcome.accepted) {
            layout.commit()
            lastOutcome = context.getString(R.string.launcher_drag_test_dropped, outcome.targetKey.orEmpty())
        } else {
            layout.cancel()
            lastOutcome = cancelled
        }
        dragFromFolder = false
    }

    fun handleBack() {
        if (state.session != null) state.cancel()
        else if (openFolder) openFolder = false
        else onBack()
    }
    BackHandler { handleBack() }

    // A stable host stays alive even when the folder and its drag source disappear.
    LauncherHoverEffect(state, delayMillis = 260L) { target, drag ->
        when {
            target == "folder-icon" && drag.item.key != FolderItem -> {
                layout.hover(Folder)
                openFolder = true
            }
            target.startsWith("slot:") -> {
                val parts = target.removePrefix("slot:").split(":", limit = 2)
                if (parts.size == 2) {
                    val container = parts[0]
                    val id = parts[1]
                    layout.hover(container, id, insertionAfter(drag, target, positions))
                }
            }
            target == Workspace || target == Folder -> layout.hover(target)
        }
    }
    LauncherFolderExitEffect(
        state = state,
        folderTargetKey = "folder-region",
        open = openFolder,
        fromFolder = { dragFromFolder },
        onExit = { openFolder = false },
        delayMillis = 400L
    )

    Scaffold(
        topBar = { MainAppbar(stringResource(R.string.launcher_drag_test_title), ::handleBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(
            top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()
        )) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.launcher_drag_test_workspace), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = {
                    state.cancel()
                    layout.update(sampleLayout())
                    openFolder = false
                    lastOutcome = reset
                }, modifier = Modifier.testTag("launcher_reset")) { Text(stringResource(R.string.launcher_drag_test_reset)) }
                if (state.session != null) {
                    Button(onClick = { state.cancel() }, modifier = Modifier.testTag("launcher_cancel")) {
                        Text(stringResource(R.string.launcher_drag_test_cancel))
                    }
                }
            }
            Text(
                text = stringResource(R.string.launcher_drag_test_help),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val noneLabel = stringResource(R.string.launcher_drag_test_none)
            val source = state.session?.item?.key ?: noneLabel
            Text(
                stringResource(R.string.launcher_drag_test_status, source, state.hoveredTargetKey ?: noneLabel),
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("launcher_drag_status"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                lastOutcome,
                Modifier.padding(horizontal = 16.dp).testTag("launcher_drag_result"),
                style = MaterialTheme.typography.labelMedium
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LauncherDragDropHost(
                state = state,
                modifier = Modifier.fillMaxSize().testTag("launcher_drag_host"),
                preview = { drag ->
                    TestTile(drag.item.key, dragging = true)
                }
            ) {
                val rootGrid = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = rootGrid,
                    modifier = Modifier.fillMaxSize()
                        .launcherDropTarget(state, Workspace,
                            onDrop = { layout.hover(Workspace); true })
                        .then(if (openFolder) Modifier else Modifier.launcherDragAutoScroll(state, rootGrid))
                        .testTag("launcher_workspace"),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(layout.preview[Workspace].orEmpty(), key = { it }) { item ->
                        TestDragTile(
                            item = item,
                            container = Workspace,
                            state = state,
                            dragging = state.session?.item?.key == item,
                            sourceEnabled = !openFolder,
                            onBegin = {
                                dragFromFolder = false
                                layout.begin(item)
                                lastOutcome = context.getString(R.string.launcher_drag_test_dragging, item, Workspace)
                            },
                            onEnd = ::finish,
                            onOpenFolder = { openFolder = true },
                            positions = positions,
                            layout = layout
                        )
                    }
                }
                if (openFolder) {
                    Card(
                        modifier = Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            .heightIn(min = 210.dp, max = 320.dp)
                            .launcherDropTarget(
                                state, "folder-region", priority = 20,
                                accepts = { it.key != FolderItem },
                                onDrop = { layout.hover(Folder); true }
                            )
                            .testTag("launcher_folder_panel"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.launcher_drag_test_folder_alpha), Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { openFolder = false }) { Text(stringResource(R.string.launcher_drag_test_close)) }
                            }
                            Spacer(Modifier.height(6.dp))
                            val folderGrid = rememberLazyGridState()
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                state = folderGrid,
                                modifier = Modifier.fillMaxWidth().height(232.dp)
                                    .launcherDragAutoScroll(state, folderGrid)
                                    .testTag("launcher_folder_grid"),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(layout.preview[Folder].orEmpty(), key = { it }) { item ->
                                    TestDragTile(
                                        item = item,
                                        container = Folder,
                                        state = state,
                                        dragging = state.session?.item?.key == item,
                                        sourceEnabled = true,
                                        onBegin = {
                                            dragFromFolder = true
                                            layout.begin(item)
                                            lastOutcome = context.getString(R.string.launcher_drag_test_dragging, item, Folder)
                                        },
                                        onEnd = ::finish,
                                        onOpenFolder = {},
                                        positions = positions,
                                        layout = layout
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestDragTile(
    item: String,
    container: String,
    state: LauncherDragDropState<String>,
    dragging: Boolean,
    sourceEnabled: Boolean,
    onBegin: () -> Unit,
    onEnd: (LauncherDragOutcome) -> Unit,
    onOpenFolder: () -> Unit,
    positions: MutableMap<String, Rect>,
    layout: LauncherReorderLayout<String>
) {
    val isFolder = item == FolderItem
    val targetKey = if (isFolder) "folder-icon" else "slot:$container:$item"
    TestTile(
        item = item,
        modifier = Modifier
            .onGloballyPositioned { positions[targetKey] = it.boundsInWindow() }
            .launcherDragSource(
                state = state, key = item, data = item,
                enabled = sourceEnabled, priority = if (container == Folder) 30 else 0,
                onDragStart = { onBegin() }, onDragEnd = onEnd
            )
            .launcherDropTarget(
                state, targetKey, priority = if (container == Folder) 30 else 6,
                accepts = { it.key != item && (!isFolder || it.key != FolderItem) },
                onDrop = {
                    if (isFolder) {
                        layout.hover(Folder)
                        onOpenFolder()
                    } else {
                        layout.hover(container, item, insertionAfter(it, targetKey, positions))
                    }
                    true
                }
            )
            .then(if (isFolder) Modifier.clickable(onClick = onOpenFolder) else Modifier)
            .testTag("launcher_item_$item"),
        hidden = dragging
    )
}

@Composable
private fun TestTile(
    item: String,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    dragging: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth().height(88.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!hidden) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (item == FolderItem) "▣" else "◆",
                        color = if (dragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (item == FolderItem) stringResource(R.string.launcher_drag_test_folder) else item,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
