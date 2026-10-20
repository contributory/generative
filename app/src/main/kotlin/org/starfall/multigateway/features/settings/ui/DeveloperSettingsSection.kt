package org.starfall.multigateway.features.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.model.logging.DebugLogCategory
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.shared.ui.logging.labelRes

/** Debug log switch plus, while it is on, the choice of which kinds of log the floating panel records. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeveloperSettingsSection(
    appPreferences: AppPreferences,
    onDebugModeChange: (Boolean) -> Unit,
    onDebugLogCategoriesChange: (Set<DebugLogCategory>) -> Unit
) {
    SettingsSection(stringResource(R.string.settings_developer)) {
        PreferenceToggle(
            stringResource(R.string.settings_debug_logs), stringResource(R.string.settings_debug_logs_description),
            appPreferences.debugMode, onDebugModeChange
        )
        AnimatedVisibility(visible = appPreferences.debugMode) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_debug_log_scope), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_debug_log_scope_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugLogCategory.entries.forEach { category ->
                        val selected = category in appPreferences.debugLogCategories
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onDebugLogCategoriesChange(
                                    if (selected) appPreferences.debugLogCategories - category
                                    else appPreferences.debugLogCategories + category
                                )
                            },
                            label = { Text(stringResource(category.labelRes())) }
                        )
                    }
                }
            }
        }
    }
}
