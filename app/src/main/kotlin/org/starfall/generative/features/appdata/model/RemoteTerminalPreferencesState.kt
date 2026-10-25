package org.starfall.generative.features.appdata.model

import kotlinx.serialization.Serializable

@Serializable
data class RemoteTerminalPreferencesState(
    val ints: Map<String, Int> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val strings: Map<String, String> = emptyMap()
)
