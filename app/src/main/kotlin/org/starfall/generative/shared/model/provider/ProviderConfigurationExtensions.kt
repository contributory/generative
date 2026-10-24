package org.starfall.generative.shared.model.provider

const val DEFAULT_CODEX_CLIENT_VERSION = "0.159.0"
const val DEFAULT_ANTIGRAVITY_VERSION = "2.24.0"
const val DEFAULT_ANTIGRAVITY_CHANGELIST = "1000335682"

private val clientVersionPattern = Regex("""\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?""")

/** Blank input means the built-in default; other values must be safe in both URLs and headers. */
fun isValidClientVersion(value: String): Boolean =
    value.isBlank() || clientVersionPattern.matches(value.trim())

fun isValidAntigravityChangelist(value: String): Boolean =
    value.isBlank() || value.trim().all { it in '0'..'9' }

fun ProviderConfiguration.effectiveCodexClientVersion(): String {
    val version = codexClientVersion?.trim()?.ifEmpty { null } ?: DEFAULT_CODEX_CLIENT_VERSION
    require(isValidClientVersion(version)) { "Codex client version must use the form 0.159.0." }
    return version
}

fun ProviderConfiguration.effectiveAntigravityVersion(): String {
    val version = antigravityVersion?.trim()?.ifEmpty { null } ?: DEFAULT_ANTIGRAVITY_VERSION
    require(isValidClientVersion(version)) { "Antigravity version must use the form 2.24.0." }
    return version
}

fun ProviderConfiguration.effectiveAntigravityChangelist(): String? {
    val changelist = antigravityChangelist?.trim()?.ifEmpty { null }
    if (changelist == null) return DEFAULT_ANTIGRAVITY_CHANGELIST.takeIf {
        effectiveAntigravityVersion() == DEFAULT_ANTIGRAVITY_VERSION
    }
    require(isValidAntigravityChangelist(changelist)) { "Antigravity changelist must contain digits only." }
    return changelist
}

fun ProviderConfiguration.antigravityProjectId(credentialId: String): String? =
    antigravityProjectIds[credentialId]?.trim()?.ifEmpty { null }

/** Ordering never removes a selected model or replaces its configuration. */
internal fun ProviderConfiguration.reorderedModels(requestedIds: List<String>): ProviderConfiguration {
    val existingIds = modelIds ?: modelConfigs.keys.toList()
    val existingSet = existingIds.toSet()
    val preferred = requestedIds.filter { it in existingSet }.distinct()
    val preferredSet = preferred.toSet()
    val ordered = preferred + existingIds.filterNot { it in preferredSet }
    val configOrder = ordered.filter { it in modelConfigs } + modelConfigs.keys.filterNot { it in ordered }
    return copy(modelIds = ordered, modelConfigs = configOrder.associateWith { modelConfigs.getValue(it) })
}
