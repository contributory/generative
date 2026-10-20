package org.starfall.multigateway.shared.model.logging

/** Kinds of debug log the user can switch on or off individually in App settings. */
enum class DebugLogCategory(val value: String) {
    NETWORK("network"),
    PAYLOAD("payload"),
    TOOLS("tools"),
    TOKENS("tokens"),
    ERRORS("errors");

    companion object {
        val all: Set<DebugLogCategory> = entries.toSet()

        fun fromValue(value: String?): DebugLogCategory? = entries.firstOrNull { it.value == value }

        /** Stored scopes; a missing setting means every category is on. */
        fun fromStored(values: Set<String>?): Set<DebugLogCategory> =
            if (values == null) all else values.mapNotNull(::fromValue).toSet()
    }
}

enum class DebugLogLevel { INFO, WARN, ERROR }
