package org.starfall.multigateway.features.providers.shared.ui

import org.starfall.multigateway.features.providers.shared.logic.ModelConnectionTests

import org.starfall.multigateway.features.providers.shared.logic.ModelConnectionRemovals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.features.providers.shared.logic.isEmpty
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.layout.windowHeightIn

@Composable
internal fun ModelConnectionDialog(provider: ModelProvider, tests: ModelConnectionTests, available: Boolean,
    onRemoveModels: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val removals = remember(provider.id) {
        ModelConnectionRemovals((provider.config.modelIds ?: provider.config.modelConfigs.keys.toList())
            .filter { provider.config.modelConfigs[it]?.modelType == ModelType.TEXT_GENERATION })
    }
    val textModels = removals.modelIds
    val testableModels = textModels.filterNot { it in removals.pending }
    val testingAny = tests.batchRunning.value || textModels.any { tests.running[it] == true }
    val unavailable = textModels.filter { tests.results[it]?.isFailure == true && it !in removals.pending }.toSet()
    val hasRemovals = removals.pending.isNotEmpty()
    fun close() {
        if (testingAny) tests.cancelAll()
        removals.close(remove = { ids -> tests.forget(ids); onRemoveModels(ids) }, dismiss = onDismiss)
    }
    AlertDialog(onDismissRequest = ::close,
         title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.test_connection), Modifier.weight(1f))
                IconButton(onClick = { if (testingAny) tests.cancelAll() else tests.testAll(provider, testableModels, skip = { it in removals.pending }) },
                     enabled = (testingAny || testableModels.isNotEmpty()) && available) {
                     if (testingAny) FluentIcon(Icons.Outlined.StopCircle, stringResource(R.string.common_cancel))
                     else FluentIcon(Icons.Outlined.NetworkCheck, "Test all text models")
                }
                 IconButton(onClick = { if (hasRemovals) removals.restoreAll() else removals.mark(unavailable) },
                     enabled = hasRemovals || unavailable.isNotEmpty()) {
                    FluentIcon(if (hasRemovals) Icons.Outlined.Restore else Icons.Outlined.DeleteSweep,
                        stringResource(if (hasRemovals) R.string.restore_removed_models else R.string.remove_unavailable_models),
                         tint = if (hasRemovals) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                 }
                 IconButton(onClick = ::close) {
                     FluentIcon(Icons.Outlined.Close, stringResource(R.string.common_close))
                 }
            }
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().windowHeightIn(maxFraction = 0.75f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (textModels.isEmpty()) item { Text("No text generation models") }
                items(textModels, key = { it }) { id ->
                    val testing = tests.running[id] == true
                    val removed = id in removals.pending
                    val result = tests.results[id]
                    val config = provider.config.modelConfigs[id] ?: ModelConfiguration()
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(config.displayName.ifBlank { id }, fontWeight = FontWeight.SemiBold)
                                    if (config.displayName.isNotBlank()) Text(id, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                 IconButton(enabled = available && (testing || (!removed && !testingAny)), onClick = { if (testing) tests.cancel(id) else tests.test(provider, id) }) {
                                     if (testing) FluentIcon(Icons.Outlined.StopCircle, stringResource(R.string.common_cancel))
                                    else FluentIcon(Icons.Outlined.NetworkCheck, stringResource(R.string.test_model, id))
                                }
                                IconButton(onClick = { if (!removed && testing) tests.cancel(id); removals.toggle(id) }) {
                                    FluentIcon(if (removed) Icons.Outlined.Restore else Icons.Outlined.Delete,
                                        stringResource(if (removed) R.string.restore_model else R.string.delete_model, id),
                                        tint = if (removed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                }
                            }
                            if (removed) Text(stringResource(R.string.model_removed_on_dialog_close),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            result?.let { tested ->
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    FluentIcon(if (tested.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error, null,
                                        tint = if (tested.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tested.getOrNull() ?: tested.exceptionOrNull()?.localizedMessage ?: stringResource(R.string.connection_failed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (tested.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = {})
}
