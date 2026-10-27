package org.starfall.generative.features.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.menus.AppDropdownMenu
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem

/** Profile selection belongs to this tool, independent of every Provider/model. */
@Composable
internal fun MusicToolSettings(config: SystemToolConfig, onChange: (SystemToolConfig) -> Unit, onOpenProfiles: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tool_music_generation_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.tool_generate_music_description), style = MaterialTheme.typography.bodySmall)
            }
            Switch(config.enabled, { onChange(config.copy(enabled = it)) }, Modifier.testTag("music-enabled"))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("music-profile-picker")) {
                    Text(config.musicRequestProfiles[config.activeMusicRequestProfileId]?.name ?: stringResource(R.string.music_select_profile), Modifier.weight(1f))
                    FluentIcon(Icons.Default.KeyboardArrowDown, null)
                }
                AppDropdownMenu(expanded, { expanded = false }) {
                    config.profiles(org.starfall.generative.shared.model.media.MediaKind.MUSIC).values.forEach { profile ->
                        RoundedDropdownMenuItem(text = { Text(profile.name) }, onClick = {
                            expanded = false; onChange(config.copy(activeMusicRequestProfileId = profile.id))
                        })
                    }
                }
            }
            IconButton(onClick = onOpenProfiles) { FluentIcon(Icons.Outlined.FormatListBulleted, stringResource(R.string.music_profiles)) }
        }
        if (config.activeMusicRequestProfile == null) Text(stringResource(R.string.music_profile_required),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
