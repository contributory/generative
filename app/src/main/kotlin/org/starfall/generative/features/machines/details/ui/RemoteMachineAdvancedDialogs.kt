package org.starfall.generative.features.machines.details.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.features.machines.shared.ui.RemoteTextField
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.WebSocketProxyConfig
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

internal data class SshOptionDraft(val key: String = "", val value: String = "")

@Composable
internal fun SshOptionsEditor(
    options: Map<String, String>,
    onChange: (Map<String, String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.remote_machine_ssh_options_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.entries.forEach { (key, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RemoteTextField(key, {}, stringResource(R.string.remote_machine_ssh_option_name), Modifier.weight(1f), readOnly = true)
                RemoteTextField(
                    value,
                    { newValue -> onChange(options + (key to newValue)) },
                    stringResource(R.string.remote_machine_ssh_option_value),
                    Modifier.weight(1f)
                )
                IconButton(onClick = { onChange(options - key) }) {
                    FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_remove))
                }
            }
        }
        var draft by remember { mutableStateOf(SshOptionDraft()) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RemoteTextField(draft.key, { draft = draft.copy(key = it) }, stringResource(R.string.remote_machine_ssh_option_name), Modifier.weight(1f))
            RemoteTextField(draft.value, { draft = draft.copy(value = it) }, stringResource(R.string.remote_machine_ssh_option_value), Modifier.weight(1f))
            IconButton(
                enabled = draft.key.isNotBlank() && draft.value.isNotBlank() && draft.key !in options,
                onClick = {
                    onChange(options + (draft.key.trim() to draft.value.trim()))
                    draft = SshOptionDraft()
                }
            ) {
                FluentIcon(Icons.Default.Add, stringResource(R.string.remote_machine_add_ssh_option))
            }
        }
    }
}

@Composable
internal fun WebSocketProxyDialog(
    initial: WebSocketProxyConfig?,
    onDismiss: () -> Unit,
    onSave: (WebSocketProxyConfig?) -> Unit
) {
    var url by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var headersText by remember(initial) {
        mutableStateOf(initial?.headers.orEmpty().entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }
    var connectTimeout by remember(initial) { mutableStateOf((initial?.connectTimeoutSeconds ?: 15).toString()) }
    var skipCertificates by remember(initial) { mutableStateOf(initial?.skipCertificateVerification ?: false) }
    var caCertificate by remember(initial) { mutableStateOf(initial?.caCertificatePem.orEmpty()) }
    var handshakeTimeout by remember(initial) { mutableStateOf((initial?.handshakeTimeoutSeconds ?: 15).toString()) }
    var readTimeout by remember(initial) { mutableStateOf((initial?.readTimeoutSeconds ?: 0).toString()) }
    var writeTimeout by remember(initial) { mutableStateOf((initial?.writeTimeoutSeconds ?: 0).toString()) }
    var closeTimeout by remember(initial) { mutableStateOf((initial?.closeTimeoutSeconds ?: 5).toString()) }
    var subProtocols by remember(initial) { mutableStateOf(initial?.subProtocols.orEmpty().joinToString(", ")) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_machine_websocket_proxy)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RemoteTextField(url, { url = it }, stringResource(R.string.remote_machine_websocket_url))
                RemoteTextField(
                    headersText,
                    { headersText = it },
                    stringResource(R.string.remote_machine_websocket_headers),
                    minLines = 3,
                    maxLines = 6
                )
                RemoteTextField(
                    connectTimeout,
                    { if (it.all(Char::isDigit)) connectTimeout = it },
                    stringResource(R.string.remote_machine_websocket_connect_timeout)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.remote_machine_websocket_skip_certificates))
                    androidx.compose.material3.Switch(
                        checked = skipCertificates,
                        onCheckedChange = { skipCertificates = it }
                    )
                }
                Box {
                    OutlinedButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.remote_machine_websocket_hide_advanced else R.string.remote_machine_websocket_show_advanced))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RemoteTextField(caCertificate, { caCertificate = it }, stringResource(R.string.remote_machine_websocket_ca_certificate), minLines = 3, maxLines = 8)
                            RemoteTextField(handshakeTimeout, { if (it.all(Char::isDigit)) handshakeTimeout = it }, stringResource(R.string.remote_machine_websocket_handshake_timeout))
                            RemoteTextField(readTimeout, { if (it.all(Char::isDigit)) readTimeout = it }, stringResource(R.string.remote_machine_websocket_read_timeout))
                            RemoteTextField(writeTimeout, { if (it.all(Char::isDigit)) writeTimeout = it }, stringResource(R.string.remote_machine_websocket_write_timeout))
                            RemoteTextField(closeTimeout, { if (it.all(Char::isDigit)) closeTimeout = it }, stringResource(R.string.remote_machine_websocket_close_timeout))
                            RemoteTextField(subProtocols, { subProtocols = it }, stringResource(R.string.remote_machine_websocket_subprotocols))
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    val headers = headersText.lineSequence().mapNotNull { line ->
                        line.substringBefore(':', "").trim().takeIf(String::isNotBlank)?.let { key ->
                            key to line.substringAfter(':', "").trim()
                        }
                    }.toMap()
                    val config = WebSocketProxyConfig(
                        url = url,
                        headers = headers,
                        connectTimeoutSeconds = connectTimeout.toIntOrNull() ?: 15,
                        skipCertificateVerification = skipCertificates,
                        caCertificatePem = caCertificate,
                        handshakeTimeoutSeconds = handshakeTimeout.toIntOrNull() ?: 15,
                        readTimeoutSeconds = readTimeout.toIntOrNull() ?: 0,
                        writeTimeoutSeconds = writeTimeout.toIntOrNull() ?: 0,
                        closeTimeoutSeconds = closeTimeout.toIntOrNull() ?: 5,
                        subProtocols = subProtocols.split(',').map(String::trim).filter(String::isNotBlank)
                    ).normalized()
                    onSave(config.takeIf { it.isConfigured })
                }.onFailure { error = it.message }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.common_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}
