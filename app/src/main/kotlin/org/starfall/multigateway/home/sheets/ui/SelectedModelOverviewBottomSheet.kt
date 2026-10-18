package org.starfall.multigateway.home.sheets.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.model.LocalThinkingScreenBurst
import org.starfall.multigateway.shared.ui.model.ThinkingScreenBurstLayer
import org.starfall.multigateway.shared.ui.model.ReasoningEffortControl
import org.starfall.multigateway.shared.ui.model.maximumEffortCardBrush
import org.starfall.multigateway.shared.ui.model.reasoningEffortOptions
import org.starfall.multigateway.shared.ui.model.reasoningEffortIndex
import org.starfall.multigateway.shared.ui.model.reasoningEffortTier
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ThinkingPassback
import org.starfall.multigateway.shared.model.language.passbackMode
import org.starfall.multigateway.shared.model.language.supportedReasoningEfforts
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.ui.sheets.AppBottomSheet
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.model.ModelPickerCard
import org.starfall.multigateway.shared.ui.model.drawMaximumEffortStreaks

@Composable
internal fun SelectedModelOverviewBottomSheet(
    provider: ModelProvider?,
    folderName: String?,
    modelId: String,
    config: ModelConfiguration,
    conversationReasoningEffort: String?,
    onSetReasoningEffort: (String?) -> Unit,
    onSetThinkingPassback: ((ThinkingPassback) -> Unit)? = null,
    onEditModel: (() -> Unit)? = null,
    onOpenModelPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val supportedEfforts = config.supportedReasoningEfforts
    val effortOptions = remember(supportedEfforts) {
        reasoningEffortOptions(supportedEfforts)
    }
    var previewTier by remember(conversationReasoningEffort, effortOptions) {
        mutableIntStateOf(reasoningEffortTier(reasoningEffortIndex(conversationReasoningEffort, effortOptions), effortOptions))
    }
    val screenBurst = LocalThinkingScreenBurst.current
    LaunchedEffect(previewTier, screenBurst) {
        // A max-effort burst is started only by crossing into the red tier on the slider.
        // Reopening this sheet at an already-selected max tier should not replay it.
        if (previewTier != 6 && screenBurst?.keepAfterCompletion == true) {
            screenBurst.hide()
        }
    }
    // The purple tier is shown only on the effort control; max effort keeps its red sheet treatment.
    val sheetBrush = if (previewTier == 6 && config.supportsThinking) maximumEffortCardBrush() else null
    val sheetColor = if (sheetBrush == null) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }
    AppBottomSheet(onDismissRequest = {
        screenBurst?.hide()
        onDismiss()
    }, containerColor = sheetColor, containerBrush = sheetBrush,
        navigationBarColor = when {
            previewTier == 6 && config.supportsThinking -> Color(0xFF1A0A06)
            else -> sheetColor
        },
        // Read in the draw phase: the reveal animates every frame and must not recompose the sheet.
        containerBrushRevealOrigin = { screenBurst?.origin?.takeIf { it != Offset.Zero } },
        containerBrushRevealProgress = { screenBurst?.progress?.value ?: 1f },
        containerOverlay = if (previewTier == 6 && config.supportsThinking) {
            { drawMaximumEffortStreaks(1f) }
        } else null
    ) {
        // The sheet is its own window, so the screen-owned star overlay must also be attached to it;
        // otherwise the stars are only drawn on the activity window, underneath the sheet and cards.
        screenBurst?.let { ThinkingScreenBurstLayer(it) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.choose_another_model_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (previewTier == 6 && config.supportsThinking) {
                    Color.White.copy(alpha = 0.82f)
                } else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            ModelPickerCard(
                modelId = modelId,
                config = config,
                isSelected = true,
                providerName = provider?.name,
                folderName = folderName,
                conversationReasoningEffort = conversationReasoningEffort,
                 onSetReasoningEffort = onSetReasoningEffort,
                 showReasoningEffort = true,
                 onClick = {
                     screenBurst?.hide()
                     onOpenModelPicker()
                 },
                 onLongClick = {
                     screenBurst?.hide()
                     onEditModel?.invoke()
                 },
                  embedded = true,
                  drawBackground = false,
                 onPreviewTierChange = { previewTier = it }
            )
            // Every reasoning-capable model has this setting; it does not wait for a saved configuration.
            if (config.supportsThinking) {
                val modes = listOf(ThinkingPassback.AUTO, ThinkingPassback.ON, ThinkingPassback.OFF)
                val labels = listOf(
                    stringResource(R.string.common_auto),
                    stringResource(R.string.common_on),
                    stringResource(R.string.common_off)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().testTag("selected-model-passback-thinking")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FluentIcon(
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
                }
            }
        }
    }
}
