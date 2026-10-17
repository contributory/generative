package org.starfall.multigateway.features.main.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R

/** Main feature directory. Deliberately has no page title; the themed app logo is its header. */
@Composable
fun FeaturesScreen(
    modifier: Modifier = Modifier,
    onNavigateToDefaultModels: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToSpeech: () -> Unit = {},
    onNavigateToRemoteMachines: () -> Unit = {},
    onNavigateToStorage: () -> Unit = {},
    onNavigateToAppSettings: () -> Unit = {},
    onNavigateToIcons: () -> Unit = {},
    onNavigateToAppData: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val logo = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        R.drawable.multigateway_logo_dark
    } else {
        R.drawable.multigateway_logo_light
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "app-logo") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(84.dp)
                )
            }
        }
        item(key = "configuration-heading") {
            SectionHeading(stringResource(R.string.settings_group_configuration))
        }
        item(key = "default-models") {
            FeatureItem(stringResource(R.string.settings_default_and_tools), stringResource(R.string.settings_default_models_description), R.drawable.ic_fluent_wrench_screwdriver_24_regular, onNavigateToDefaultModels)
        }
        item(key = "providers") {
            FeatureItem(stringResource(R.string.settings_providers), stringResource(R.string.settings_providers_description), R.drawable.ic_fluent_cloud_24_regular, onNavigateToProviders)
        }
        item(key = "mcp") {
            FeatureItem(stringResource(R.string.settings_mcp_manage), stringResource(R.string.settings_mcp_manage_description), R.drawable.ic_fluent_puzzle_piece_24_regular, onNavigateToMcp)
        }
        item(key = "speech") {
            FeatureItem(stringResource(R.string.settings_speech_services), stringResource(R.string.settings_speech_services_description), R.drawable.ic_fluent_person_voice_24_regular, onNavigateToSpeech)
        }
        item(key = "remote-machines") {
            FeatureItem(stringResource(R.string.settings_remote_machines), stringResource(R.string.settings_remote_machines_description), R.drawable.ic_fluent_desktop_24_regular, onNavigateToRemoteMachines)
        }
        item(key = "storage") {
            FeatureItem(stringResource(R.string.settings_storage), stringResource(R.string.settings_storage_description), R.drawable.ic_fluent_folder_open_24_regular, onNavigateToStorage)
        }
        item(key = "system-heading") {
            Spacer(Modifier.height(8.dp))
            SectionHeading(stringResource(R.string.settings_group_system))
        }
        item(key = "app-settings") {
            FeatureItem(stringResource(R.string.settings_category_app_settings), stringResource(R.string.settings_category_app_settings_description), R.drawable.ic_fluent_settings_24_regular, onNavigateToAppSettings)
        }
        item(key = "icons") {
            FeatureItem(stringResource(R.string.icon_settings_title), stringResource(R.string.icon_settings_description), R.drawable.ic_fluent_image_24_regular, onNavigateToIcons)
        }
        item(key = "app-data") {
            FeatureItem(stringResource(R.string.settings_category_app_data), stringResource(R.string.settings_category_app_data_description), R.drawable.ic_fluent_history_24_regular, onNavigateToAppData)
        }
        item(key = "about") {
            FeatureItem(stringResource(R.string.settings_category_about), stringResource(R.string.settings_category_about_description), R.drawable.ic_fluent_info_24_regular, onNavigateToAbout)
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun FeatureItem(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_fluent_arrow_forward_24_regular), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}
