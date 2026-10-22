package org.starfall.generative.features.machines.details.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.features.machines.shared.ui.RemoteSection
import org.starfall.generative.features.machines.shared.ui.RemoteTextField
import org.starfall.generative.features.machines.shared.ui.RemoteToggle
import org.starfall.generative.shared.model.PortForwardingConfig

/**
 * Port forwarding group of the machine editor: one switch per forward type, with the ports for each type.
 * Rule text is kept locally so blank lines and half-typed entries survive while editing; [onChange] receives
 * the parsed config, which is validated when the machine is saved.
 */
@Composable
internal fun PortForwardingSection(
    initial: PortForwardingConfig,
    onChange: (PortForwardingConfig) -> Unit
) {
    var config by remember { mutableStateOf(initial) }
    var localText by remember { mutableStateOf(initial.localRules.joinToString("\n")) }
    var remoteText by remember { mutableStateOf(initial.remoteRules.joinToString("\n")) }
    var dynamicText by remember { mutableStateOf(initial.dynamicPorts.joinToString(", ")) }
    fun update(next: PortForwardingConfig) {
        config = next
        onChange(next)
    }

    RemoteSection(
        title = stringResource(R.string.remote_machine_port_forwarding_section),
        description = stringResource(R.string.remote_machine_port_forwarding_help)
    ) {
        RemoteToggle(
            label = stringResource(R.string.remote_machine_forward_local),
            checked = config.localEnabled,
            description = stringResource(R.string.remote_machine_forward_local_help),
            onCheckedChange = { update(config.copy(localEnabled = it)) }
        )
        if (config.localEnabled) {
            RemoteTextField(
                value = localText,
                onValueChange = {
                    localText = it
                    update(config.copy(localRules = it.splitEntries()))
                },
                label = stringResource(R.string.remote_machine_forward_local_rules),
                supporting = stringResource(R.string.remote_machine_forward_local_hint),
                minLines = 2
            )
        }
        RemoteToggle(
            label = stringResource(R.string.remote_machine_forward_remote),
            checked = config.remoteEnabled,
            description = stringResource(R.string.remote_machine_forward_remote_help),
            onCheckedChange = { update(config.copy(remoteEnabled = it)) }
        )
        if (config.remoteEnabled) {
            RemoteTextField(
                value = remoteText,
                onValueChange = {
                    remoteText = it
                    update(config.copy(remoteRules = it.splitEntries()))
                },
                label = stringResource(R.string.remote_machine_forward_remote_rules),
                supporting = stringResource(R.string.remote_machine_forward_remote_hint),
                minLines = 2
            )
        }
        RemoteToggle(
            label = stringResource(R.string.remote_machine_forward_dynamic),
            checked = config.dynamicEnabled,
            description = stringResource(R.string.remote_machine_forward_dynamic_help),
            onCheckedChange = { update(config.copy(dynamicEnabled = it)) }
        )
        if (config.dynamicEnabled) {
            RemoteTextField(
                value = dynamicText,
                onValueChange = { input ->
                    val text = input.filter { it.isDigit() || it == ',' || it == ' ' }
                    dynamicText = text
                    update(config.copy(dynamicPorts = text.splitEntries().mapNotNull(String::toIntOrNull)))
                },
                label = stringResource(R.string.remote_machine_forward_dynamic_ports),
                supporting = stringResource(R.string.remote_machine_forward_dynamic_hint)
            )
        }
    }
}

private fun String.splitEntries(): List<String> = split('\n', ',').map(String::trim).filter(String::isNotEmpty)
