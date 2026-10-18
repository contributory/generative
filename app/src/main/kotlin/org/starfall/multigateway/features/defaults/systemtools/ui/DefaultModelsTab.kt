package org.starfall.multigateway.features.defaults.systemtools.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.starfall.multigateway.R
import androidx.compose.ui.res.stringResource
import org.starfall.multigateway.integrations.media.videoOptionFields
import org.starfall.multigateway.shared.ui.layout.SettingsCard
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.ui.sheets.AppBottomSheet
import org.starfall.multigateway.shared.ui.icons.EntityIcon
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField
import org.starfall.multigateway.shared.ui.layout.SlideScreenContent
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.systemMediaToolAvailable

@Composable
fun DefaultModelsTab(
    providers: List<ModelProvider>,
    settings: ToolSettings,
    onChooseModel: (String) -> Unit,
    onOpenMediaSettings: (String) -> Unit,
    onEditPrompt: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.tool_settings_media_tools_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        items(listOf("generate_image", "generate_video")) { name ->
            val config = settings.system[name] ?: SystemToolConfig()
            val provider = providers.find { it.id == config.providerId }
            val model = provider?.config?.modelConfigs?.get(config.modelId)
            val available = systemMediaToolAvailable(name, config, providers)
            MediaDefaultModelCard(
                name = name,
                config = config,
                provider = provider,
                model = model,
                available = available,
                onChooseModel = { onChooseModel(name) },
                onOpenSettings = { onOpenMediaSettings(name) }
            )
        }

        item {
            Text(stringResource(R.string.tool_settings_conversation_helpers_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        items(listOf("title_generation", "chat_summary")) { name ->
            val config = settings.system[name] ?: SystemToolConfig()
            val provider = providers.find { it.id == config.providerId }
            val model = provider?.config?.modelConfigs?.get(config.modelId)
            val title = stringResource(if (name == "title_generation") R.string.tool_title_generation_title else R.string.tool_chat_summary_title)
            val description = if (name == "title_generation")
                stringResource(R.string.tool_title_generation_description)
            else
                stringResource(R.string.tool_chat_summary_description)

            SettingsCard {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DefaultModelSelector(provider, config.modelId, model, onClick = { onChooseModel(name) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEditPrompt(name) }) { Text(stringResource(R.string.tool_instructions)) }
                }
            }
        }
    }
}

@Composable
private fun DefaultModelSelector(
    provider: ModelProvider?,
    modelId: String,
    model: ModelConfiguration?,
    onClick: () -> Unit
) {
    val modelName = model?.displayName?.ifBlank { modelId }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EntityIcon(
                image = model?.icon,
                modifier = Modifier.size(48.dp),
                text = modelId.substringAfterLast('/').take(1).uppercase(),
                fallback = if (model == null) Icons.Outlined.Add else null,
                matchName = modelName,
                model = true
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    modelName ?: stringResource(R.string.tool_select_model),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (model == null) stringResource(R.string.tool_select_model_hint) else provider?.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FluentIcon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
private fun MediaDefaultModelCard(
    name: String,
    config: SystemToolConfig,
    provider: ModelProvider?,
    model: ModelConfiguration?,
    available: Boolean,
    onChooseModel: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val image = name == "generate_image"
    val title = stringResource(if (image) R.string.tool_image_generation_title else R.string.tool_video_generation_title)
    val description = if (image) {
        stringResource(R.string.tool_generate_image_description)
    } else {
        stringResource(R.string.tool_generate_video_description)
    }
    val modelType = if (image) ModelType.IMAGE_GENERATION else ModelType.VIDEO_GENERATION
    val settingsAvailable = provider != null && model?.modelType == modelType &&
        (image || videoOptionFields(provider, config.modelId).isNotEmpty() || config.videoOptions.isNotEmpty())

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                FluentIcon(
                    imageVector = if (image) Icons.Outlined.Image else Icons.Outlined.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        DefaultModelSelector(provider, config.modelId, model, onClick = onChooseModel)
        if (!available) {
            Text(
                stringResource(R.string.tool_select_compatible_model_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(enabled = settingsAvailable, onClick = onOpenSettings) {
                Text(stringResource(if (image) R.string.tool_image_settings else R.string.tool_video_settings))
            }
        }
    }
}
