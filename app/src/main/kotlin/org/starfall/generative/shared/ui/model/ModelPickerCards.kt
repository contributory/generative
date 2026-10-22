package org.starfall.generative.shared.ui.model

import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.model.LocalThinkingScreenBurst
import org.starfall.generative.shared.ui.model.ReasoningEffortControl
import org.starfall.generative.shared.ui.model.ThinkingScreenBurstLayer
import org.starfall.generative.shared.ui.model.maximumEffortCardBrush
import org.starfall.generative.shared.ui.model.maximumThinkingCardBrush
import org.starfall.generative.shared.ui.model.reasoningEffortBurstDuration
import org.starfall.generative.shared.ui.model.reasoningEffortIndex
import org.starfall.generative.shared.ui.model.reasoningEffortOptions
import org.starfall.generative.shared.ui.model.reasoningEffortTier
import org.starfall.generative.shared.ui.model.rememberThinkingColorBurst
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.DEFAULT_CONTEXT_WINDOW_TOKENS
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.language.ThinkingPassback
import org.starfall.generative.shared.model.language.passbackMode
import org.starfall.generative.shared.model.language.supportedReasoningEfforts
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.icons.providerInitials
import org.starfall.generative.shared.ui.model.revealThinkingColor
import org.starfall.generative.shared.ui.model.drawMaximumEffortStreaks

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModelPickerCard(
    modelId: String,
    config: ModelConfiguration,
    isSelected: Boolean,
    providerName: String? = null,
    folderName: String? = null,
    conversationReasoningEffort: String?,
    onSetReasoningEffort: (String?) -> Unit,
    showReasoningEffort: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    onPreviewTierChange: (Int) -> Unit = {},
    embedded: Boolean = false,
    drawBackground: Boolean = true
) {
    val supportedEfforts = config.supportedReasoningEfforts
    val effortOptions = remember(supportedEfforts) {
        reasoningEffortOptions(supportedEfforts)
    }
    var previewReasoningValue by remember(conversationReasoningEffort, isSelected, effortOptions) {
        mutableFloatStateOf(reasoningEffortIndex(conversationReasoningEffort, effortOptions).toFloat())
    }
    val tier = reasoningEffortTier(previewReasoningValue.roundToInt(), effortOptions)
    val enhancedThinking = isSelected && showReasoningEffort && config.supportsThinking && tier >= 5
    val cardTint by animateFloatAsState(
        if (enhancedThinking) 1f else 0f,
        tween(if (tier == 6) 1800 else 2600, easing = FastOutSlowInEasing),
        label = "thinkingCardTint"
    )
    val cardBase = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        else MaterialTheme.colorScheme.surfaceContainerLow
    val settledCardColor = cardBase
    val cardBrush = if (tier == 6) maximumEffortCardBrush()
        else maximumThinkingCardBrush(MaterialTheme.colorScheme.surfaceContainerLow)
    val cardBurst = rememberThinkingColorBurst()
    val screenBurst = LocalThinkingScreenBurst.current
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
    val cardContentColor = if (!embedded && enhancedThinking && tier >= 5) Color.White else MaterialTheme.colorScheme.onSurface
    val cardGesture = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    val cardModifier = Modifier.fillMaxWidth().then(if (embedded) Modifier else cardGesture)
    val cardContentModifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { cardOrigin = it.localToScreen(Offset.Zero) }
        .then(
            if (drawBackground) Modifier.drawBehind {
                revealThinkingColor(cardBurst) {
                    drawRect(cardBrush, alpha = cardTint)
                    if (tier == 6) drawMaximumEffortStreaks(cardTint)
                }
            } else Modifier
        )
        .padding(10.dp)
    val cardContent: @Composable ColumnScope.() -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().then(if (embedded) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .then(cardGesture)
                        .padding(10.dp)
                } else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EntityIcon(config.icon, Modifier.size(40.dp), text = modelInitial(modelId), matchName = config.displayName.ifBlank { modelId }, fallbackMatchName = modelId, model = true)

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = compactModelLabel(config.displayName.ifBlank { modelId }),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (config.displayName.isNotBlank() && config.displayName != modelId) {
                        Text(
                            text = "ID: ${compactModelLabel(modelId)}",
                            style = MaterialTheme.typography.labelSmall,
                             color = if (!embedded && enhancedThinking && tier >= 5) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!providerName.isNullOrBlank() || !folderName.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            providerName?.takeIf { it.isNotBlank() }?.let {
                                ModelContextChip(
                                    text = it,
                                    modifier = Modifier.weight(1f),
                                    containerAlpha = if (embedded) 0.5f else 0.78f,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            folderName?.takeIf { it.isNotBlank() }?.let {
                                ModelContextChip(
                                    text = "Group: $it",
                                    modifier = Modifier.weight(1f),
                                    containerAlpha = if (embedded) 0.5f else 0.78f,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    ModelCapabilityBadges(
                        config,
                        modifier = if (embedded) Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(3.dp)
                        else Modifier
                    )
                }

                if (isSelected) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                         color = MaterialTheme.colorScheme.primary.copy(alpha = if (embedded) 0.5f else 1f),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FluentIcon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Off is the lowest slider level, so the slider stays visible whenever the model supports thinking.
            if (showReasoningEffort && isSelected && config.supportsThinking) {
                val activeLabel = effortOptions[previewReasoningValue.roundToInt().coerceIn(effortOptions.indices)].label

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FluentIcon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Reasoning Effort",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (!embedded && enhancedThinking && tier >= 5) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = activeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ReasoningEffortControl(
                    effort = conversationReasoningEffort,
                    onEffortChange = onSetReasoningEffort,
                    onPreviewValueChange = {
                        previewReasoningValue = it
                        onPreviewTierChange(reasoningEffortTier(it.roundToInt(), effortOptions))
                    },
                    options = effortOptions,
                    onColorBurst = { origin, selectedTier ->
                         val burstColor = if (selectedTier == 6) Color(0xFFDC2626) else Color(0xFFA78BFA)
                         if (selectedTier >= 5) cardBurst.start(origin - cardOrigin, burstColor,
                             reasoningEffortBurstDuration(selectedTier))
                         screenBurst?.let { burst ->
                             when (selectedTier) {
                                 6 -> burst.start(
                                     origin = origin,
                                     color = burstColor,
                                     durationMillis = 6500,
                                     keepAfterCompletion = true,
                                     starCount = 180
                                 )
                                 5 -> if (embedded) burst.hide() else burst.start(
                                     origin = origin,
                                     color = Color(0xFFA78BFA),
                                     durationMillis = 2600,
                                     keepAfterCompletion = false,
                                     starCount = 32
                                 )
                                 else -> burst.hide()
                             }
                         }
                    },
                    trackBackground = settledCardColor
                )
            }
    }
    CompositionLocalProvider(LocalContentColor provides cardContentColor) {
        if (embedded) {
            Column(modifier = cardContentModifier, content = cardContent)
        } else {
            Surface(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                color = cardBase,
                contentColor = cardContentColor,
                border = if (isSelected) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                } else null
            ) {
                Column(modifier = cardContentModifier, content = cardContent)
            }
        }
    }
}

@Composable
private fun ModelContextChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    containerAlpha: Float = 0.78f
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = containerColor.copy(alpha = containerAlpha),
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ModelCapabilityBadges(config: ModelConfiguration, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModelBadge(
            label = when (config.modelType) {
                ModelType.TEXT_GENERATION -> "Chat"
                ModelType.IMAGE_GENERATION -> "Image"
                ModelType.VIDEO_GENERATION -> "Video"
                ModelType.TEXT_TO_SPEECH -> "TTS"
                ModelType.SPEECH_TO_TEXT -> "STT"
                else -> config.modelType.displayName
            },
            icon = when (config.modelType) {
                ModelType.TEXT_GENERATION -> Icons.Outlined.ChatBubbleOutline
                ModelType.IMAGE_GENERATION -> Icons.Outlined.Image
                ModelType.VIDEO_GENERATION -> Icons.Outlined.Videocam
                ModelType.TEXT_TO_SPEECH, ModelType.SPEECH_TO_TEXT -> Icons.Outlined.Audiotrack
                ModelType.EMBEDDING, ModelType.RERANKING -> null
                ModelType.REALTIME -> Icons.Outlined.ChatBubbleOutline
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        if (config.modelType != ModelType.TEXT_GENERATION) return@Row
        ModelBadge(
            label = compactContextWindow(config.contextWindowTokens),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        if (config.supportsVision) {
            ModelBadge(
                label = "Image",
                iconOnly = true,
                icon = Icons.Outlined.Image,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        if (config.supportsVideoInput) {
            ModelBadge(
                label = "Video",
                iconOnly = true,
                icon = Icons.Outlined.Videocam,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        if (config.supportsAudioInput) {
            ModelBadge(
                label = "Audio",
                iconOnly = true,
                icon = Icons.Outlined.Audiotrack,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        if (config.supportsThinking) {
            ModelBadge(
                label = "Thinking",
                iconOnly = true,
                icon = Icons.Outlined.Psychology,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        if (config.supportsToolCalls) {
            ModelBadge(
                label = "Tools",
                iconOnly = true,
                icon = Icons.Outlined.Build,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

private fun compactContextWindow(tokens: Int): String {
    val count = tokens.takeIf { it > 0 } ?: DEFAULT_CONTEXT_WINDOW_TOKENS
    val divisor = when {
        count >= 1_000_000 -> 1_000_000
        count >= 1_000 -> 1_000
        else -> return count.toString()
    }
    val value = java.math.BigDecimal(count).divide(java.math.BigDecimal(divisor), 1, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
    return value + if (divisor == 1_000_000) "M" else "k"
}

@Composable
private fun ModelBadge(
    label: String,
    icon: ImageVector? = null,
    iconOnly: Boolean = false,
    colors: ChipColors = AssistChipDefaults.assistChipColors()
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.containerColor,
        contentColor = colors.labelColor,
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            icon?.let { FluentIcon(it, contentDescription = if (iconOnly) label else null, modifier = Modifier.size(12.dp)) }
            if (!iconOnly) Text(label, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun ProviderMark(
    provider: ModelProvider,
    compact: Boolean = false
) {
    val mark = providerInitials(provider.name)
    EntityIcon(provider.icon, Modifier.size(if (compact) 22.dp else 42.dp), text = mark, matchName = provider.name)
}
