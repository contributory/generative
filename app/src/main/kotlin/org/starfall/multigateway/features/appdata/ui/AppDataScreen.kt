package org.starfall.multigateway.features.appdata.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDataScreen(
    conversationCount: Int,
    providerCount: Int,
    onClearAllConversations: () -> Unit,
    onResetAllData: () -> Unit,
    onExportJson: () -> Unit = {},
    onImportJson: () -> Unit = {},
    onBack: () -> Unit
) {
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_app_data)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                DataSection(title = stringResource(R.string.settings_data_overview)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DataStatItem(stringResource(R.string.settings_chats), conversationCount.toString())
                        DataStatItem(stringResource(R.string.settings_providers), providerCount.toString())
                    }
                }
            }
            item {
                DataSection(title = stringResource(R.string.settings_manage_data)) {
                    Text(
                        stringResource(R.string.settings_manage_data_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_clear_all_chat_history)) }
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_reset_all_application_data)) }
                }
            }
            item {
                DataSection(title = stringResource(R.string.settings_export_data)) {
                    Text(
                        stringResource(R.string.settings_export_data_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DataActionButton(stringResource(R.string.settings_export_all_data_json)) {
                        onExportJson()
                    }
                    Spacer(Modifier.height(4.dp))
                    DataActionButton(stringResource(R.string.settings_import_all_data_json)) {
                        onImportJson()
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_all_chats_title)) },
            text = { Text(stringResource(R.string.settings_clear_all_chats_message)) },
            confirmButton = {
                Button(onClick = {
                    onClearAllConversations()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.settings_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_factory_reset_title)) },
            text = { Text(stringResource(R.string.settings_factory_reset_message)) },
            confirmButton = {
                Button(onClick = {
                    onResetAllData()
                    showResetConfirm = false
                }) { Text(stringResource(R.string.settings_reset_everything)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun DataSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun DataStatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = count,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DataActionButton(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title)
    }
}
