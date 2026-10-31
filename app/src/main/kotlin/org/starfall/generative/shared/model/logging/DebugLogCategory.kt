package org.starfall.generative.shared.model.logging

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DebugLogCategorySerializer::class)
enum class DebugLogCategory(val value: String) {
    NETWORK("network"),
    SYSTEM("system");

    companion object {
        val all: Set<DebugLogCategory> = entries.toSet()
        private val formerNetwork = setOf("network", "payload", "model", "auth", "mcp", "speech")
        private val formerSystem = setOf("tools", "tokens", "errors", "generation", "storage")

        fun fromValue(value: String?): DebugLogCategory? = when (value) {
            "network", "payload", "model", "auth", "mcp", "speech" -> NETWORK
            "system", "tools", "tokens", "errors", "generation", "storage" -> SYSTEM
            else -> null
        }

        /** Preserve previously selected sources when migrating older per-feature logging settings. */
        fun fromStored(values: Set<String>?): Set<DebugLogCategory> {
            if (values == null) return all
            return buildSet {
                if (values.any { it in formerNetwork || it == "errors" }) add(NETWORK)
                if (values.any { it in formerSystem || it == "system" }) add(SYSTEM)
            }
        }
    }
}

/** Android Logcat priorities: TRACE uses the native VERBOSE priority. */
@Serializable
enum class DebugLogLevel(val priority: Int) {
    TRACE(Log.VERBOSE), DEBUG(Log.DEBUG), INFO(Log.INFO), WARN(Log.WARN), ERROR(Log.ERROR);

    companion object {
        fun fromStored(value: String?): DebugLogLevel = entries.firstOrNull { it.name == value } ?: WARN
    }
}

/** Older backup manifests may still contain the retired per-feature category names. */
object DebugLogCategorySerializer : KSerializer<DebugLogCategory> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DebugLogCategory", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DebugLogCategory) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): DebugLogCategory =
        DebugLogCategory.fromValue(decoder.decodeString()) ?: DebugLogCategory.SYSTEM
}
