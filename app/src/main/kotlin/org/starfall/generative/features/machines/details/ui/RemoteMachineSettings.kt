@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.starfall.generative.features.machines.details.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.features.machines.shared.logic.RemoteMachinesViewModel
import org.starfall.generative.features.machines.shared.ui.RemoteDropdownField
import org.starfall.generative.features.machines.shared.ui.RemoteErrorText
import org.starfall.generative.features.machines.shared.ui.RemoteIconTile
import org.starfall.generative.features.machines.shared.ui.RemoteListRow
import org.starfall.generative.features.machines.shared.ui.RemoteMachinesTopBar
import org.starfall.generative.features.machines.shared.ui.RemoteSection
import org.starfall.generative.features.machines.shared.ui.RemoteTextField
import org.starfall.generative.shared.model.GCloudOAuthClient
import org.starfall.generative.shared.model.GoogleOAuthClientType
import org.starfall.generative.shared.model.RemoteMachineState
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive

@Composable
internal fun RemoteMachineSettings(
    state: RemoteMachineState,
    viewModel: RemoteMachinesViewModel,
    onBack: () -> Unit
) {
    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = onBack)
    var clientEditor by remember { mutableStateOf<GCloudOAuthClient?>(null) }
    var identityError by remember { mutableStateOf<String?>(null) }
    var oauthError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val identityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importIdentityKey(uri)
                .onSuccess { identityError = null }
                .onFailure { identityError = it.message }
        }
    }
    var publicKeyTarget by remember { mutableStateOf<String?>(null) }
    val publicKeyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val keyId = publicKeyTarget
        publicKeyTarget = null
        if (uri == null || keyId == null) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importPublicKey(keyId, uri)
                .onSuccess { identityError = null }
                .onFailure { identityError = it.message }
        }
    }
    val oauthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importOAuthClientJson(uri)
                .onSuccess { client ->
                    oauthError = null
                    clientEditor = client
                }
                .onFailure { oauthError = it.message }
        }
    }

    Scaffold(
        topBar = {
            RemoteMachinesTopBar(
                title = stringResource(R.string.remote_machine_settings),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RemoteSection(stringResource(R.string.remote_machine_oauth_clients)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { oauthLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }) {
                        FluentIcon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.remote_machine_import_oauth_json))
                    }
                    OutlinedButton(onClick = { clientEditor = viewModel.newOAuthClient() }) {
                        FluentIcon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.remote_machine_add_oauth))
                    }
                }
                state.oauthClients.forEach { client ->
                    RemoteListRow(
                        title = client.name,
                        subtitle = client.clientId,
                        leading = { RemoteIconTile(Icons.Outlined.Key) },
                        trailing = {
                            IconButton(onClick = { clientEditor = client }) {
                                FluentIcon(Icons.Outlined.Edit, stringResource(R.string.common_edit))
                            }
                            IconButton(onClick = { viewModel.deleteOAuthClient(client.id) }) {
                                FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
                oauthError?.let { RemoteErrorText(it) }
            }
            RemoteSection(
                title = stringResource(R.string.remote_machine_ssh_keys),
                description = stringResource(R.string.remote_machine_ssh_keys_help)
            ) {
                state.identities.forEach { key ->
                    val users = state.machines.count { it.identityFile == key.path || it.identityFile.substringAfterLast('/') == key.name }
                    RemoteListRow(
                        title = key.name,
                        subtitle = buildString {
                            append(key.path)
                            append("\n")
                            append(
                                context.getString(
                                    if (key.publicKeyPath.isNotBlank()) R.string.remote_machine_public_key_uploaded
                                    else R.string.remote_machine_public_key_missing
                                )
                            )
                            if (users > 0) {
                                append("\n")
                                append(context.getString(R.string.remote_machine_ssh_key_used_by, users))
                            }
                        },
                        subtitleMaxLines = 3,
                        leading = { RemoteIconTile(Icons.Outlined.Key) },
                        trailing = {
                            IconButton(onClick = {
                                publicKeyTarget = key.id
                                publicKeyLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                            }) {
                                FluentIcon(Icons.Outlined.UploadFile, stringResource(R.string.remote_machine_upload_public_key))
                            }
                            IconButton(enabled = users == 0, onClick = { viewModel.deleteIdentityKey(key.id) }) {
                                FluentIcon(
                                    Icons.Outlined.Delete,
                                    stringResource(R.string.common_delete),
                                    tint = if (users == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
                OutlinedButton(
                    onClick = { identityLauncher.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FluentIcon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.remote_machine_upload_ssh_key))
                }
                identityError?.let { RemoteErrorText(it) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    clientEditor?.let { initial ->
        OAuthClientDialog(
            initial = initial,
            onDismiss = { clientEditor = null },
            onSave = { client ->
                viewModel.saveOAuthClient(client)
                clientEditor = null
            }
        )
    }
}

@Composable
internal fun OAuthClientDialog(
    initial: GCloudOAuthClient,
    onDismiss: () -> Unit,
    onSave: (GCloudOAuthClient) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var clientId by remember(initial.id) { mutableStateOf(initial.clientId) }
    var secret by remember(initial.id) { mutableStateOf(initial.clientSecret) }
    var clientType by remember(initial.id) { mutableStateOf(initial.type) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_machine_oauth_client)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteTextField(name, { name = it }, stringResource(R.string.common_name))
                RemoteTextField(clientId, { clientId = it }, stringResource(R.string.remote_machine_client_id))
                RemoteDropdownField(
                    label = stringResource(R.string.remote_machine_oauth_client_type),
                    options = GoogleOAuthClientType.entries.map { option ->
                        option to stringResource(
                            if (option == GoogleOAuthClientType.ANDROID) R.string.remote_machine_oauth_type_android
                            else R.string.remote_machine_oauth_type_desktop
                        )
                    },
                    selected = clientType,
                    onSelect = { clientType = it }
                )
                if (clientType == GoogleOAuthClientType.DESKTOP) {
                    RemoteTextField(secret, { secret = it }, stringResource(R.string.remote_machine_client_secret), visual = PasswordVisualTransformation())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && clientId.isNotBlank(),
                onClick = { onSave(GCloudOAuthClient(initial.id, name, clientId, secret, clientType)) }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
