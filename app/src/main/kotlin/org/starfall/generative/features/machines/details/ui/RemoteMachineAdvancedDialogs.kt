package org.starfall.generative.features.machines.details.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.features.machines.shared.ui.RemoteErrorText
import org.starfall.generative.features.machines.shared.ui.RemoteTextField
import org.starfall.generative.features.machines.shared.ui.RemoteToggle
import org.starfall.generative.shared.model.WebSocketProxyConfig
import org.starfall.generative.shared.ui.dialogs.AppDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

/**
 * Editor for the extra JSch session options. Options stay hidden until the user adds one, so the
 * machine editor only shows the options that are actually set.
 */
@Composable
internal fun SshOptionsEditor(
    options: Map<String, String>,
    onChange: (Map<String, String>) -> Unit
) {
    var draftKey by remember { mutableStateOf("") }
    var draftValue by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.remote_machine_ssh_options_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.entries.forEach { (key, value) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteTextField(key, {}, stringResource(R.string.remote_machine_ssh_option_name), Modifier.weight(1f), readOnly = true)
                RemoteTextField(
                    value,
                    { newValue -> onChange(options + (key to newValue)) },
                    stringResource(R.string.remote_machine_ssh_option_value),
                    Modifier.weight(1f)
                )
                IconButton(onClick = { onChange(options - key) }) {
                    FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_remove), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RemoteTextField(draftKey, { draftKey = it }, stringResource(R.string.remote_machine_ssh_option_name), Modifier.weight(1f))
            RemoteTextField(draftValue, { draftValue = it }, stringResource(R.string.remote_machine_ssh_option_value), Modifier.weight(1f))
            IconButton(
                enabled = draftKey.isNotBlank() && draftValue.isNotBlank() && draftKey.trim() !in options,
                onClick = {
                    onChange(options + (draftKey.trim() to draftValue.trim()))
                    draftKey = ""
                    draftValue = ""
                }
            ) {
                FluentIcon(Icons.Default.Add, stringResource(R.string.remote_machine_add_ssh_option))
            }
        }
    }
}

/**
 * WebSocket Proxy configuration. Follows the app's other editor dialogs ([AppDialog] with a
 * rounded surface and its own top bar) instead of an alert dialog crammed with fields; the
 * rarely used settings live in an expandable advanced section inside the same dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var advancedExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun numeric(update: (String) -> Unit): (String) -> Unit =
        { value -> if (value.all(Char::isDigit)) update(value) }

    AppDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.remote_machine_websocket_proxy),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            FluentIcon(Icons.Default.Close, stringResource(R.string.common_close))
                        }
                    }
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RemoteTextField(
                        url,
                        { url = it; error = null },
                        stringResource(R.string.remote_machine_websocket_url),
                        supporting = stringResource(R.string.remote_machine_websocket_url_help)
                    )
                    RemoteTextField(
                        headersText,
                        { headersText = it },
                        stringResource(R.string.remote_machine_websocket_headers),
                        minLines = 3,
                        maxLines = 6
                    )
                    RemoteTextField(
                        connectTimeout,
                        numeric { connectTimeout = it },
                        stringResource(R.string.remote_machine_websocket_connect_timeout)
                    )
                    RemoteToggle(
                        label = stringResource(R.string.remote_machine_websocket_skip_certificates),
                        checked = skipCertificates,
                        onCheckedChange = { skipCertificates = it }
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(
                                if (advancedExpanded) R.string.remote_machine_websocket_hide_advanced
                                else R.string.remote_machine_websocket_show_advanced
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        FluentIcon(
                            if (advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    AnimatedVisibility(visible = advancedExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RemoteTextField(
                                caCertificate,
                                { caCertificate = it },
                                stringResource(R.string.remote_machine_websocket_ca_certificate),
                                minLines = 3,
                                maxLines = 8
                            )
                            RemoteTextField(
                                handshakeTimeout,
                                numeric { handshakeTimeout = it },
                                stringResource(R.string.remote_machine_websocket_handshake_timeout)
                            )
                            RemoteTextField(
                                readTimeout,
                                numeric { readTimeout = it },
                                stringResource(R.string.remote_machine_websocket_read_timeout)
                            )
                            RemoteTextField(
                                writeTimeout,
                                numeric { writeTimeout = it },
                                stringResource(R.string.remote_machine_websocket_write_timeout)
                            )
                            RemoteTextField(
                                closeTimeout,
                                numeric { closeTimeout = it },
                                stringResource(R.string.remote_machine_websocket_close_timeout)
                            )
                            RemoteTextField(
                                subProtocols,
                                { subProtocols = it },
                                stringResource(R.string.remote_machine_websocket_subprotocols)
                            )
                        }
                    }
                    error?.let { RemoteErrorText(it) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initial != null) {
                        TextButton(onClick = { onSave(null) }) {
                            Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        runCatching {
                            val headers = headersText.lineSequence().mapNotNull { line ->
                                line.substringBefore(':', "").trim().takeIf(String::isNotBlank)?.let { key ->
                                    key to line.substringAfter(':', "").trim()
                                }
                            }.toMap()
                            WebSocketProxyConfig(
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
                        }
                            .onSuccess { config -> onSave(config.takeIf { it.isConfigured }) }
                            .onFailure { failure -> error = failure.message }
                    }) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}
