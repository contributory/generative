package org.starfall.generative.features.providers.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.mohamedrejeb.compose.dnd.annotation.ExperimentalDndApi
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.scroll.dragAutoScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.features.providers.shared.logic.isEmpty
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.ItemContextMenu
import org.starfall.generative.shared.ui.model.ModelPickerCard

@OptIn(ExperimentalDndApi::class)
@Composable
internal fun ProviderModelsPane(models: List<String>, configurations: Map<String, ModelConfiguration>, listState: LazyListState,
    onMove: (Int, Int) -> Unit, onDrop: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit, onOpenCatalog: () -> Unit) {
    val dndState = rememberDragAndDropState<String>(dragAfterLongPress = true)
    DragAndDropContainer(state = dndState, modifier = Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().dragAutoScroll(state = dndState, lazyListState = listState),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (models.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.provider_no_models_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onEdit("") }) { SharedIcon(Icons.Outlined.Edit, stringResource(R.string.common_add), Modifier.size(18.dp)) }
                    Button(onClick = onOpenCatalog) { SharedIcon(Icons.Outlined.FormatListBulleted, stringResource(R.string.open_model_catalog), Modifier.size(18.dp)) }
                }
            }
        }
        items(models, key = { it }) { id ->
            ReorderableItem(modifier = Modifier.animateItem(), state = dndState, key = id, data = id,
                onDragEnter = { dragged ->
                    val from = models.indexOf(dragged.data)
                    val to = models.indexOf(id)
                    if (from >= 0 && to >= 0 && from != to) onMove(from, to)
                }, onDragEnd = { onDrop() }) {
                val model = configurations.getValue(id)
                ModelPickerCard(
                    modelId = id,
                    config = model,
                    isSelected = false,
                    conversationReasoningEffort = null,
                    onSetReasoningEffort = {},
                    showReasoningEffort = false,
                    onClick = { onEdit(id) },
                    modifier = Modifier,
                    trailingContent = {
                        ItemContextMenu(onEdit = { onEdit(id) }, onDelete = { onDelete(id) }, deleteColor = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                    }
                )
            }
        }
    }
    }
}
