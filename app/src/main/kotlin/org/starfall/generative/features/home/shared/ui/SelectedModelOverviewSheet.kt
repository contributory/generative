package org.starfall.generative.features.home.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.model.LocalThinkingScreenBurst
import org.starfall.generative.shared.ui.model.ThinkingScreenBurstLayer
import org.starfall.generative.shared.ui.model.ReasoningEffortControl
import org.starfall.generative.shared.ui.model.localizedReasoningEffortLabels
import org.starfall.generative.shared.ui.model.maximumEffortCardBrush
import org.starfall.generative.shared.ui.model.reasoningEffortOptions
import org.starfall.generative.shared.ui.model.reasoningEffortIndex
import org.starfall.generative.shared.ui.model.reasoningEffortTier
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.ThinkingPassback
import org.starfall.generative.shared.model.language.passbackMode
import org.starfall.generative.shared.model.language.supportedReasoningEfforts
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.sheets.BottomSheet
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.model.ModelPickerCard
import org.starfall.generative.shared.ui.model.drawMaximumEffortStreaks
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

/** Sheet background that follows the reasoning tier: the maximum effort tier gets a red brush. */
internal class SelectedModelSheetStyle(
    val color: Color,
    val brush: Brush?,
    val navigationBarColor: Color,
    val overlay: (DrawScope.() -> Unit)?
)

@Composable
internal fun selectedModelSheetStyle(maxEffort: Boolean, retracting: Boolean = false): SelectedModelSheetStyle {
    val brush = if (maxEffort || retracting) maximumEffortCardBrush() else null
    val color = if (maxEffort) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
    return SelectedModelSheetStyle(
        color = color,
        brush = brush,
        navigationBarColor = if (maxEffort) Color(0xFF1A0A06) else color,
        overlay = if (maxEffort || retracting) ({ drawMaximumEffortStreaks(1f) }) else null
    )
}

/** Tier previewed by the effort slider; hoisted so a host sheet can restyle itself while it moves. */
@Composable
internal fun rememberPreviewTier(config: ModelConfiguration, conversationReasoningEffort: String?): MutableIntState {
    val reasoningLabels = localizedReasoningEffortLabels()
    val effortOptions = remember(config.supportedReasoningEfforts, reasoningLabels) {
        reasoningEffortOptions(config.supportedReasoningEfforts, reasoningLabels)
    }
    return remember(conversationReasoningEffort, effortOptions) {
        mutableIntStateOf(reasoningEffortTier(reasoningEffortIndex(conversationReasoningEffort, effortOptions), effortOptions))
    }
}

/** Standalone sheet; the chat uses [SelectedModelOverviewContent] as a page of a shared model sheet instead. */
@Composable
internal fun SelectedModelOverviewSheet(
    provider: ModelProvider?,
    folderName: String?,
    modelId: String,
    config: ModelConfiguration,
    conversationReasoningEffort: String?,
    onSetReasoningEffort: (String?) -> Unit,
    onSetThinkingPassback: ((ThinkingPassback) -> Unit)? = null,
    onSetModelTools: ((List<String>) -> Unit)? = null,
    onEditModel: (() -> Unit)? = null,
    onOpenModelPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val previewTier = rememberPreviewTier(config, conversationReasoningEffort)
    val screenBurst = LocalThinkingScreenBurst.current
    val style = selectedModelSheetStyle(
        previewTier.intValue == 6 && config.supportsThinking,
        screenBurst?.isRetracting == true
    )
    BottomSheet(onDismissRequest = {
        screenBurst?.hide()
        onDismiss()
    }, containerColor = style.color, containerBrush = style.brush,
        navigationBarColor = style.navigationBarColor,
        // Read in the draw phase: the reveal animates every frame and must not recompose the sheet.
        containerBrushRevealOrigin = { screenBurst?.origin?.takeIf { it != Offset.Zero } },
        containerBrushRevealProgress = { screenBurst?.progress?.value ?: 1f },
        containerBrushRetractionProgress = { screenBurst?.takeIf { it.isRetracting }?.retractionProgress?.value },
        containerOverlay = style.overlay
    ) {
        // The sheet is its own window, so the screen-owned star overlay must also be attached to it;
        // otherwise the stars are only drawn on the activity window, underneath the sheet and cards.
        screenBurst?.let { ThinkingScreenBurstLayer(it) }
        SelectedModelOverviewContent(
            provider = provider,
            folderName = folderName,
            modelId = modelId,
            config = config,
            conversationReasoningEffort = conversationReasoningEffort,
            previewTier = previewTier.intValue,
            onPreviewTierChange = { previewTier.intValue = it },
            onSetReasoningEffort = onSetReasoningEffort,
            onSetThinkingPassback = onSetThinkingPassback,
            onSetModelTools = onSetModelTools,
            onEditModel = onEditModel,
            onOpenModelPicker = onOpenModelPicker
        )
    }
}

@Composable
internal fun SelectedModelOverviewContent(
    provider: ModelProvider?,
    folderName: String?,
    modelId: String,
    config: ModelConfiguration,
    conversationReasoningEffort: String?,
    previewTier: Int,
    onPreviewTierChange: (Int) -> Unit,
    onSetReasoningEffort: (String?) -> Unit,
    onSetThinkingPassback: ((ThinkingPassback) -> Unit)? = null,
    onSetModelTools: ((List<String>) -> Unit)? = null,
    onEditModel: (() -> Unit)? = null,
    onOpenModelPicker: () -> Unit,
    /** Horizontal context indicator shown above the model card; the host owns what it displays. */
    contextWindowContent: (@Composable (onDarkBackground: Boolean) -> Unit)? = null,
    /** Shows a Summarize button beside Change language model when set. */
    onSummarize: (() -> Unit)? = null
) {
    var modelOptionsExpanded by remember(modelId) { mutableStateOf(false) }
    val visibleModelTools = remember(config.supportedModelTools, provider?.type) {
        // supportedModelTools already comes from this model's catalog/editor configuration.
        // Do not silently hide valid configured entries behind a second hard-coded allowlist.
        config.supportedModelTools.distinct()
    }
    val screenBurst = LocalThinkingScreenBurst.current
    LaunchedEffect(previewTier, screenBurst) {
        // A max-effort burst is started only by crossing into the red tier on the slider.
        // Reopening this sheet at an already-selected max tier should not replay it.
        when {
            previewTier != 6 && screenBurst?.keepAfterCompletion == true && !screenBurst.isRetracting -> screenBurst.hide()
            previewTier == 6 && config.supportsThinking && screenBurst != null && !screenBurst.keepAfterCompletion ->
                screenBurst.restoreSettled()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Expanded Model Options can exceed the sheet's max height; scroll instead of overlapping.
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val maxEffortSheet = previewTier == 6 && config.supportsThinking
        val changeModelButton: @Composable (Modifier) -> Unit = { buttonModifier ->
            FilledTonalButton(
                onClick = {
                    screenBurst?.hide()
                    onOpenModelPicker()
                },
                contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                modifier = buttonModifier.testTag("open-model-picker-button")
            ) {
                Text(
                    stringResource(R.string.change_model),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                SharedIcon(
                    Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (onSummarize != null) Arrangement.SpaceBetween else Arrangement.End
        ) {
            if (onSummarize != null) {
                FilledTonalButton(
                    onClick = {
                        screenBurst?.hide()
                        onSummarize()
                    },
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                    modifier = Modifier.testTag("summarize-conversation-button")
                ) {
                    SharedIcon(
                        Icons.Outlined.Summarize,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.summarize_now), maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
            }
            changeModelButton(Modifier.weight(1f, fill = false))
        }
        contextWindowContent?.invoke(maxEffortSheet)
        ModelPickerCard(
            modelId = modelId,
            config = config,
            isSelected = true,
            providerName = provider?.name,
            folderName = folderName,
            conversationReasoningEffort = conversationReasoningEffort,
             onSetReasoningEffort = onSetReasoningEffort,
             showReasoningEffort = true,
             onLongClick = {
                 screenBurst?.hide()
                 onEditModel?.invoke()
             },
              embedded = true,
              drawBackground = false,
             onPreviewTierChange = onPreviewTierChange
        )
        // Show the dropdown only when this model has at least one configurable option.
        // Keep optional model controls tucked away until the user opens Model Options.
        if (visibleModelTools.isNotEmpty() || config.supportsThinking) {
            val burstActive = previewTier == 6 && config.supportsThinking
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (burstActive) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                } else {
                    prominentContainerColor()
                },
                border = if (burstActive) null else prominentContainerBorder(),
                modifier = Modifier.fillMaxWidth().testTag("model-options-dropdown")
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { modelOptionsExpanded = !modelOptionsExpanded }
                        .testTag("model-options-toggle")
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.model_options_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    SharedIcon(
                        imageVector = if (modelOptionsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (modelOptionsExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (config.supportsThinking) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            val modes = listOf(ThinkingPassback.AUTO, ThinkingPassback.ON, ThinkingPassback.OFF)
                            val labels = listOf(
                                stringResource(R.string.common_auto),
                                stringResource(R.string.common_on),
                                stringResource(R.string.common_off)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SharedIcon(
                                    Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    stringResource(R.string.send_thinking_content_back),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("passback-thinking-mode")) {
                                modes.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = config.passbackMode == mode,
                                        onClick = { onSetThinkingPassback?.invoke(mode) },
                                        enabled = onSetThinkingPassback != null,
                                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                        modifier = Modifier.testTag("passback-thinking-${mode.name.lowercase()}")
                                    ) {
                                        Text(labels[index], maxLines = 1)
                                    }
                                }
                            }
                            Text(
                                stringResource(R.string.send_thinking_content_back_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (visibleModelTools.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Text(
                                stringResource(R.string.model_tools_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.model_tools_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            visibleModelTools.distinct().forEach { tool ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().testTag("model-tool-$tool"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        modelToolDisplayName(tool),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = tool in config.enabledModelTools,
                                        onCheckedChange = { enabled ->
                                            val current = config.enabledModelTools.filter { it in visibleModelTools }.toSet()
                                            val updated = if (enabled) current + tool else current - tool
                                            onSetModelTools?.invoke(updated.toList())
                                        },
                                        enabled = onSetModelTools != null,
                                        modifier = Modifier.testTag("model-tool-switch-$tool")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

}


@Composable
private fun modelToolDisplayName(tool: String): String = when (tool.lowercase()) {
    "google_search", "web_search" -> stringResource(R.string.model_tool_web_search)
    "google_maps" -> stringResource(R.string.model_tool_google_maps)
    "url_context", "web_fetch" -> stringResource(R.string.model_tool_url_context)
    "code_execution", "code_interpreter" -> stringResource(R.string.model_tool_code_execution)
    "file_search" -> stringResource(R.string.model_tool_file_search)
    "image_generation" -> stringResource(R.string.tool_image_generation_title)
    "computer_use", "computer" -> stringResource(R.string.model_tool_computer_use)
    else -> tool.split('_', '-', '.').filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
