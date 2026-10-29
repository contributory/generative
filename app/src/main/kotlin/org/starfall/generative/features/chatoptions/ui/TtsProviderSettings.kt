package org.starfall.generative.features.chatoptions.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.sheets.BottomSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.effectiveTtsProviderId
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.icons.providerInitials
import org.starfall.generative.shared.ui.model.DefaultSelectionItem

/** Selects the saved speech service used when reading messages aloud. */
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityIcon(null, Modifier.size(42.dp), fallback = Icons.Outlined.RecordVoiceOver)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_tts_provider_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(stringResource(R.string.settings_tts_provider_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth()) {
                DefaultSelectionItem(
                    title = selectedService?.name
                        ?: stringResource(R.string.settings_tts_provider_none),
                    subtitle = stringResource(R.string.settings_tts_provider_select),
                    icon = {
                        EntityIcon(
                            image = selectedService?.icon,
                            modifier = Modifier.size(48.dp),
                            text = providerInitials(selectedService?.name.orEmpty()),
                            fallback = Icons.Outlined.RecordVoiceOver,
                            matchName = selectedService?.name
                        )
                    },
                    onClick = { expanded = true }
                )
                if (expanded) {
                    BottomSheet(onDismissRequest = { expanded = false }) {
                        Text(stringResource(R.string.settings_tts_provider_title),
                            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                        LazyColumn(Modifier.fillMaxWidth()) {
                            if (speechServices.isEmpty()) item {
                                Text(stringResource(R.string.settings_tts_provider_empty), Modifier.padding(16.dp))
                            }
                            items(speechServices, key = { it.id }) { service ->
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = { Text(service.name) },
                                    trailingContent = {
                                        if (service.id == selectedId) SharedIcon(Icons.Default.Check, stringResource(R.string.common_selected))
                                    },
                                    modifier = Modifier.clickable {
                                        onProviderChange(service.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
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
