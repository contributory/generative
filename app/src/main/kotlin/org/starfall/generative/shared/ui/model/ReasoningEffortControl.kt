package org.starfall.generative.shared.ui.model

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.starfall.generative.R
import org.starfall.generative.shared.model.language.DEFAULT_REASONING_EFFORTS
import org.starfall.generative.shared.model.language.REASONING_EFFORT_ACTIVE
import org.starfall.generative.shared.model.language.SELECTABLE_REASONING_EFFORTS

/** Labels for the built-in reasoning options. Defaults keep plain English for non-Compose callers. */
internal data class ReasoningEffortLabels(
    val none: String = "None",
    val default: String = "Default",
    val active: String = "Active",
    val xhigh: String = "X-High",
)

@Composable
internal fun localizedReasoningEffortLabels(): ReasoningEffortLabels = ReasoningEffortLabels(
    none = stringResource(R.string.common_none),
    default = stringResource(R.string.common_default),
    active = stringResource(R.string.common_active),
    xhigh = stringResource(R.string.reasoning_level_xhigh),
)

internal fun reasoningEffortLabel(
    effort: String,
    labels: ReasoningEffortLabels = ReasoningEffortLabels()
): String = when (effort) {
    "xhigh" -> labels.xhigh
    else -> effort.replaceFirstChar { it.uppercase() }
}

private fun isReasoningLevel(effort: String?): Boolean =
    effort != null && reasoningEffortEnabled(effort) && effort != REASONING_EFFORT_ACTIVE

/**
 * Colour tiers follow the model's own levels: its two highest levels get the purple and red
 * effects, lower ones keep the normal colours. A single level only gets purple, Default never
 * gets a colour, and Active (a thinking model without levels) is purple.
 */
internal fun reasoningEffortTier(index: Int, options: List<ReasoningEffortOption>): Int {
    val option = options.getOrNull(index) ?: return 1
    if (!reasoningEffortEnabled(option.effort)) return 0
    if (option.effort == null) return 1
    if (option.effort == REASONING_EFFORT_ACTIVE) return 5
    val levels = options.filter { isReasoningLevel(it.effort) }
    if (levels.size == 1) return 5
    val position = levels.indexOf(option)
    return when (levels.lastIndex - position) {
        0 -> 6
        1 -> 5
        else -> minOf(2 + position, 4)
    }
}

internal fun reasoningEffortAccent(tier: Int, default: Color): Color = when (tier) {
    5 -> Color(0xFF8B5CF6)
    6 -> Color(0xFFB91C1C)
    else -> default
}

internal fun reasoningEffortBurstDuration(tier: Int): Int = when (tier) {
    0, 1 -> 850
    2 -> 1050
    3 -> 1250
    4 -> 1500
    5 -> 2600
    else -> 4200
}

internal data class ReasoningEffortOption(val effort: String?, val label: String)

internal fun reasoningEffortOptions(
    supportedEfforts: List<String> = DEFAULT_REASONING_EFFORTS,
    labels: ReasoningEffortLabels = ReasoningEffortLabels()
): List<ReasoningEffortOption> {
    val levels = SELECTABLE_REASONING_EFFORTS.filter { it in supportedEfforts }.map { effort ->
        ReasoningEffortOption(effort, reasoningEffortLabel(effort, labels))
    }
    // Active asks the server to enable reasoning without choosing a configured level.
    val active = if (levels.isEmpty()) listOf(ReasoningEffortOption(REASONING_EFFORT_ACTIVE, labels.active)) else emptyList()
    return listOf(ReasoningEffortOption("none", labels.none), ReasoningEffortOption(null, labels.default)) +
        if (levels.isEmpty()) active else levels
}

internal fun reasoningEffortEnabled(effort: String?): Boolean =
    effort?.trim()?.lowercase() !in listOf("none", "off")

/** Anything the selector does not offer is shown as Default, which is also what gets sent. */
internal fun reasoningEffortIndex(
    effort: String?,
    options: List<ReasoningEffortOption> = reasoningEffortOptions()
): Int {
    val normalized = effort?.trim()?.lowercase()?.let { if (it == "off") "none" else it }
    val defaultIndex = options.indexOfFirst { it.effort == null }.coerceAtLeast(0)
    if (normalized.isNullOrEmpty()) return defaultIndex
    return options.indexOfFirst { it.effort == normalized }.takeIf { it >= 0 } ?: defaultIndex
}

/** Changes only the conversation override; model capabilities remain untouched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReasoningEffortControl(
    effort: String?,
    onEffortChange: (String?) -> Unit,
    onPreviewValueChange: (Float) -> Unit = {},
    options: List<ReasoningEffortOption> = reasoningEffortOptions(labels = localizedReasoningEffortLabels()),
    onColorBurst: (Offset, Int) -> Unit = { _, _ -> },
    trackBackground: Color = MaterialTheme.colorScheme.surface
) {
    var sliderValue by remember(effort, options) {
        mutableFloatStateOf(reasoningEffortIndex(effort, options).toFloat())
    }
    val maximumValue = options.lastIndex.toFloat()
    val layoutDirection = LocalLayoutDirection.current
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val thumbScale by animateFloatAsState(if (dragged || pressed) 1.1f else 1f,
        tween(160), label = "thinking-thumb-scale")
    val burst = rememberThinkingColorBurst()
    var trackBounds by remember { mutableStateOf(Rect.Zero) }
    fun colorBand(value: Float) = reasoningEffortTier(value.roundToInt(), options)
    val band = colorBand(sliderValue)
    var previousColorBand by remember { mutableIntStateOf(band) }
    val defaultColor = MaterialTheme.colorScheme.primary
    val defaultInactive = MaterialTheme.colorScheme.primaryContainer
    fun accentFor(tier: Int) = reasoningEffortAccent(tier, defaultColor)
    val accent = accentFor(band)
    val thumbColor by animateColorAsState(accent, tween(500), label = "thinking-thumb-color")
    val sliderColors = SliderDefaults.colors(thumbColor = thumbColor)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Slider(
                value = sliderValue,
                onValueChange = {
                    val previousBand = colorBand(sliderValue)
                    sliderValue = it
                    onPreviewValueChange(it)
                    val nextBand = colorBand(it)
                    if (nextBand != previousBand && trackBounds.width > 0f) {
                        previousColorBand = previousBand
                        val fraction = (it / maximumValue).coerceIn(0f, 1f)
                        val physicalFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction
                        val origin = Offset(trackBounds.width * physicalFraction, trackBounds.height / 2f)
                        // Lower tiers retain their normal static colors. Only purple/red transitions
                        // get the bloom and screen effects.
                        if (nextBand >= 5 || previousBand >= 5) {
                            burst.start(origin, accentFor(nextBand), reasoningEffortBurstDuration(nextBand))
                            onColorBurst(trackBounds.topLeft + origin, nextBand)
                        }
                    }
                },
                onValueChangeFinished = {
                    onEffortChange(options[sliderValue.roundToInt().coerceIn(options.indices)].effort)
                },
                valueRange = 0f..maximumValue,
                steps = options.size - 2,
                colors = sliderColors,
                interactionSource = interactionSource,
                thumb = {
                    Canvas(
                         modifier = Modifier.size(28.dp).graphicsLayer {
                            val pulse = burst.progress.value
                            val scale = thumbScale + 0.12f * (1f - pulse)
                             scaleX = scale
                             scaleY = scale
                              alpha = 1f
                          }.shadow(3.dp, CircleShape)
                    ) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(lerp(thumbColor, Color.White, 0.28f), thumbColor),
                                center = center - Offset(3.dp.toPx(), 3.dp.toPx()),
                                radius = size.minDimension
                            )
                        )
                    }
                },
                track = {
                    Box(Modifier.fillMaxWidth().height(12.dp)
                        .onGloballyPositioned {
                             trackBounds = Rect(it.localToScreen(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                        }
                        .clip(CircleShape).drawBehind {
                             val radius = CornerRadius(size.height / 2f)
                             fun paintDefaultTrack() {
                                 drawRoundRect(defaultInactive, cornerRadius = radius)
                                 val width = size.width * (sliderValue / maximumValue).coerceIn(0f, 1f)
                                 drawRoundRect(defaultColor,
                                     topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f, 0f),
                                     size = Size(width, size.height), cornerRadius = radius)
                             }
                             fun paintTrack(tier: Int) {
                                 if (tier < 5) {
                                     paintDefaultTrack()
                                 } else {
                                     drawRoundRect(Brush.horizontalGradient(
                                         if (tier == 6) listOf(Color(0xFF991B1B), Color(0xFF160406))
                                         else listOf(lerp(trackBackground, accentFor(tier), 0.5f), accentFor(tier))
                                     ), cornerRadius = radius)
                                 }
                             }
                             val animatingStrongTier = band >= 5 && previousColorBand >= 5 && burst.progress.value < 1f
                             paintTrack(if (animatingStrongTier) previousColorBand else band)
                             // Reveal purple/red from the thumb instead of sweeping from an edge.
                             if (band >= 5 && burst.progress.value < 1f) {
                                 if (burst.progress.value >= 0.52f) {
                                     paintTrack(band)
                                 } else {
                                     val radius = 14.dp.toPx() + hypot(size.width, size.height) * burst.bloomProgress
                                     clipPath(androidx.compose.ui.graphics.Path().apply {
                                         addOval(Rect(burst.origin, radius))
                                     }) { paintTrack(band) }
                                 }
                             }
                            // Subtle detents remain visible beneath the settling color.
                            repeat(options.size) { index ->
                                val x = size.width * index / options.lastIndex
                                  drawCircle(Color.White.copy(alpha = 0.32f), 1.25.dp.toPx(), Offset(x, size.height / 2f))
                             }
                              if (band >= 5) drawThinkingColorBurst(burst)
                        })
                },
                modifier = Modifier.fillMaxWidth().testTag("reasoning-slider")
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                options.forEachIndexed { index, option ->
                    Text(option.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = when {
                            index != sliderValue.roundToInt() -> labelColor.copy(alpha = 0.65f)
                            band < 5 -> labelColor
                            else -> labelColor
                        })
                }
            }
        }
    }
}

/** Max-effort card: only black, purple, red and orange (light streaks are drawn on top of it). */
internal fun maximumEffortCardBrush(): Brush = Brush.horizontalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFF0A0A0C),
        0.26f to Color(0xFF3B1275),
        0.52f to Color(0xFFB91C1C),
        0.78f to Color(0xFFEA580C),
        1.00f to Color(0xFF1A0A06)
    )
)

/** Physical left-to-right colors: the strongest purple stays on the right. */
internal fun maximumThinkingCardBrush(surface: Color): Brush = Brush.horizontalGradient(
    listOf(lerp(surface, Color(0xFF7C3AED), 0.16f), lerp(surface, Color(0xFF7C3AED), 0.48f))
)
