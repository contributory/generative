package org.starfall.generative.features.chatoptions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.effectiveTtsProviderId
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Selects the saved speech service used when reading messages aloud. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsProviderSettings(
    speechServices: List<SpeechService>,
    selectedSpeechServiceId: String?,
    onProviderChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedId = speechServices.effectiveTtsProviderId(selectedSpeechServiceId)
    val selectedService = speechServices.firstOrNull { it.id == selectedId }
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_tts_provider_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.settings_tts_provider_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                SelectableOutlinedTextField(
                    value = selectedService?.name
                        ?: stringResource(R.string.settings_tts_provider_none),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_tts_provider_select)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    speechServices.forEach { service ->
                        DropdownMenuItem(
                            text = { Text(service.name) },
                            onClick = {
                                onProviderChange(service.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
            if (speechServices.isEmpty()) {
                Text(
                    stringResource(R.string.settings_tts_provider_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
