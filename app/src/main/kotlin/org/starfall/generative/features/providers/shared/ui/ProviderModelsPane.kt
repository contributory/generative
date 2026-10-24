package org.starfall.generative.features.providers.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.features.providers.shared.logic.isEmpty
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.ItemContextMenu
import org.starfall.generative.shared.ui.model.ModelCapabilityBadges
import org.starfall.generative.shared.ui.model.modelInitial
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@Composable
internal fun ProviderModelsPane(models: List<String>, configurations: Map<String, ModelConfiguration>, listState: LazyListState,
    onMove: (Int, Int) -> Unit, onDrop: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit, onOpenCatalog: () -> Unit) {
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> onMove(from.index, to.index) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (models.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.provider_no_models_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onEdit("") }) { FluentIcon(Icons.Outlined.Edit, stringResource(R.string.common_add), Modifier.size(18.dp)) }
                    Button(onClick = onOpenCatalog) { FluentIcon(Icons.Outlined.FormatListBulleted, stringResource(R.string.open_model_catalog), Modifier.size(18.dp)) }
                }
            }
        }
        items(models, key = { it }) { id ->
            ReorderableItem(reorderState, key = id) { _ ->
                val model = configurations.getValue(id)
                val cardShape = RoundedCornerShape(16.dp)
                Surface(
                    onClick = { onEdit(id) },
                    shape = cardShape,
                    color = prominentContainerColor(),
                    border = prominentContainerBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .longPressDraggableHandle(onDragStopped = onDrop)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        EntityIcon(model.icon, Modifier.size(42.dp), text = modelInitial(id), matchName = model.displayName.ifBlank { id }, fallbackMatchName = id, model = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(model.displayName.ifBlank { id }, style = MaterialTheme.typography.titleMedium, overflow = TextOverflow.Ellipsis, maxLines = 2)
                            if (model.displayName.isNotBlank()) Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ModelCapabilityBadges(model)
                        }
                        ItemContextMenu(onEdit = { onEdit(id) }, onDelete = { onDelete(id) }, deleteColor = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
