package org.starfall.generative.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.shared.ui.dialogs.Dialog
import org.starfall.generative.R
import org.starfall.generative.extensions.launcher.Launcher

/** Session-only demo controls; production consumers choose their own interaction policy. */
internal data class LauncherDragTestOptions(
    val list: Boolean = false,
    val columns: Int = 3,
    val reorderDelayMillis: Long = Launcher.defaults.ReorderDelayMillis,
    val reorderProximityDp: Float = 12f,
    val folderOpenDelayMillis: Long = Launcher.defaults.FolderOpenDelayMillis,
    val folderHitFraction: Float = 0.5f,
    val reorderOnEntry: Boolean = true,
    val folderJoinEnabled: Boolean = true
)

@Composable
internal fun LauncherDragTestControls(options: LauncherDragTestOptions, onChange: (LauncherDragTestOptions) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(options.copy(list = !options.list)) },
                modifier = Modifier.testTag("launcher_toggle_layout")) {
                Text(stringResource(if (options.list) R.string.launcher_drag_test_grid else R.string.launcher_drag_test_list))
            }
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("launcher_options")) {
                Text(stringResource(R.string.launcher_drag_test_options))
            }
        }
    }
    if (expanded) Dialog(
        onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.launcher_drag_test_options)) },
        scrollableContent = true,
        buttons = {
            TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.launcher_drag_test_close)) }
        },
        content = { Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.launcher_drag_test_reorder_on_entry), Modifier.weight(1f))
                Switch(checked = options.reorderOnEntry,
                    onCheckedChange = { onChange(options.copy(reorderOnEntry = it)) },
                    modifier = Modifier.testTag("launcher_reorder_on_entry"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.launcher_drag_test_allow_folder_join), Modifier.weight(1f))
                Switch(checked = options.folderJoinEnabled,
                    onCheckedChange = { onChange(options.copy(folderJoinEnabled = it)) },
                    modifier = Modifier.testTag("launcher_folder_join_enabled"))
            }
            Text(stringResource(R.string.launcher_drag_test_reorder_delay, options.reorderDelayMillis.toInt()))
            Slider(value = options.reorderDelayMillis.toFloat(), valueRange = 0f..900f,
                modifier = Modifier.testTag("launcher_reorder_delay_slider"),
                onValueChange = { onChange(options.copy(reorderDelayMillis = it.toLong())) })
            Text(stringResource(R.string.launcher_drag_test_reorder_proximity, options.reorderProximityDp.toInt()))
            Slider(value = options.reorderProximityDp, valueRange = 0f..32f,
                onValueChange = { onChange(options.copy(reorderProximityDp = it)) })
            Text(stringResource(R.string.launcher_drag_test_folder_delay, options.folderOpenDelayMillis.toInt()))
            Slider(value = options.folderOpenDelayMillis.toFloat(), valueRange = 200f..1200f,
                onValueChange = { onChange(options.copy(folderOpenDelayMillis = it.toLong())) })
            Text(stringResource(R.string.launcher_drag_test_folder_hit, (options.folderHitFraction * 100).toInt()))
            Slider(value = options.folderHitFraction, valueRange = 0.3f..0.8f,
                onValueChange = { onChange(options.copy(folderHitFraction = it)) })
            if (!options.list) {
                Text(stringResource(R.string.launcher_drag_test_columns, options.columns))
                Slider(value = options.columns.toFloat(), valueRange = 2f..5f, steps = 2,
                    onValueChange = { onChange(options.copy(columns = it.toInt())) })
            }
        } }
    )
}
