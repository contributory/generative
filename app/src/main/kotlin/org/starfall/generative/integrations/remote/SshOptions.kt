package org.starfall.generative.integrations.remote

import com.jcraft.jsch.JSch

/** Keys handled by dedicated Remote Machine fields and therefore not accepted as extra options. */
private val RESERVED_SSH_OPTIONS = setOf(
    "stricthostkeychecking",
    "preferredauthentications",
    "compression.s2c",
    "compression.c2s"
)

/**
 * Validates options before they reach JSch. JSch silently accepts unknown keys in some versions,
 * which would otherwise make a typo look like a saved setting that has no effect.
 */
internal fun normalizeSshOptions(options: Map<String, String>): Map<String, String> {
    val known = JSch.getConfig().keys.toList()
        .associateBy { it.lowercase() }
    val normalized = linkedMapOf<String, String>()
    options.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val value = rawValue.trim()
        if (key.isBlank() && value.isBlank()) return@forEach
        require(key.isNotBlank()) { "SSH option names cannot be empty." }
        require(value.isNotBlank()) { "SSH option '$key' cannot have an empty value." }
        val canonical = known[key.lowercase()]
            ?: error("Unsupported JSch SSH option: $key")
        require(canonical.lowercase() !in RESERVED_SSH_OPTIONS) {
            "SSH option '$canonical' is controlled by a dedicated machine setting."
        }
        require(normalized.put(canonical, value) == null) {
            "SSH option '$canonical' is listed more than once."
        }
    }
    return normalized
}
