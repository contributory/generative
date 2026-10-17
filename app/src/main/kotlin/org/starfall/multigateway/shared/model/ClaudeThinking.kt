package org.starfall.multigateway.shared.model

import kotlinx.serialization.json.*
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.REASONING_EFFORT_ACTIVE
import org.starfall.multigateway.shared.model.language.requestReasoningEffort
import org.starfall.multigateway.shared.model.language.reasoningBudget
import org.starfall.multigateway.shared.model.language.reasoningEffortDisabled

/** Known API capabilities; unknown/custom aliases retain manual thinking. */
data class ClaudeThinkingCapabilities(
    val efforts: List<String> = emptyList(),
    val adaptive: Boolean = false,
    val canDisable: Boolean = true,
    val betweenTools: Boolean = false
)

fun claudeThinkingCapabilities(modelId: String): ClaudeThinkingCapabilities {
    val id = modelId.lowercase()
    val versionPattern = "[45](?:[-_.][0-9](?![0-9]))?"
    val match = Regex("(opus|sonnet|fable|mythos)[-_ .]+($versionPattern)(?![0-9])").find(id)
        ?: Regex("($versionPattern)[-_ .]+(opus|sonnet)").find(id)?.let {
            // Some gateways put the version before the family.
            return claudeThinkingCapabilities("claude-${it.groupValues[2]}-${it.groupValues[1]}")
        }
    val family = match?.groupValues?.get(1)
    val version = match?.groupValues?.get(2)?.replace('_', '-')?.replace('.', '-')
    if (family == "opus" && version == "4-5") {
        return ClaudeThinkingCapabilities(listOf("low", "medium", "high"))
    }
    val preview = id.contains("mythos-preview")
    val adaptive = preview || (family == "opus" && version in listOf("4-6", "4-7", "4-8", "5", "5-5")) ||
            (family == "sonnet" && version in listOf("4-6", "5", "5-5")) ||
            (family in listOf("fable", "mythos") && version in listOf("5", "5-1"))
    if (!adaptive) return ClaudeThinkingCapabilities()
    val xhigh = !preview && version != "4-6"
    val alwaysOn = preview || family in listOf("fable", "mythos") || (family == "opus" && version == "5-5")
    val betweenTools = family == "sonnet" && version == "5-5"
    return ClaudeThinkingCapabilities(
        efforts = buildList {
            addAll(listOf("low", "medium", "high"))
            addAll(if (xhigh) listOf("xhigh") else emptyList())
            add("max")
        },
        adaptive = true,
        canDisable = !alwaysOn && !betweenTools,
        betweenTools = betweenTools
    )
}

/** Shared by the SDK and native Messages API requests, including every tool round. */
fun ModelConfiguration.claudeThinkingParameters(modelId: String, maxTokens: Int): JsonObject = buildJsonObject {
    if (!supportsThinking) return@buildJsonObject
    val capabilities = claudeThinkingCapabilities(modelId)
    val requested = requestReasoningEffort ?: return@buildJsonObject
    if (requested in listOf("none", "off")) {
        when {
            capabilities.betweenTools -> {
                put("thinking", buildJsonObject { put("type", "between_tools") })
                put("output_config", buildJsonObject { put("effort", "low") })
            }

            capabilities.canDisable -> put("thinking", buildJsonObject { put("type", "disabled") })
            // This model cannot be switched off, so Off sends nothing instead of another level.
        }
        return@buildJsonObject
    }
    // The level is sent exactly as chosen; Active only turns thinking on.
    if (capabilities.efforts.isNotEmpty() && requested != REASONING_EFFORT_ACTIVE) {
        put("output_config", buildJsonObject { put("effort", requested) })
    }
    if (capabilities.adaptive) {
        put("thinking", buildJsonObject { put("type", "adaptive"); put("display", "summarized") })
    } else if (maxTokens > 1024) {
        put("thinking", buildJsonObject {
            put("type", "enabled")
            put("budget_tokens", copy(reasoningEffort = requested).reasoningBudget(maxTokens))
        })
    }
}

/** Keep sampling options out of adaptive and manual thinking requests. */
fun ModelConfiguration.claudeAllowsSampling(modelId: String, maxTokens: Int): Boolean =
    !claudeThinkingCapabilities(modelId).adaptive &&
            (!supportsThinking || requestReasoningEffort == null || reasoningEffortDisabled || maxTokens <= 1024)
