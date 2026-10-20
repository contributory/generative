package org.starfall.generative.features.appdata.model

import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup

/** Parsed provider backup, including optional records supported by newer export versions. */
data class ProvidersExport(
    val providers: List<ModelProvider>,
    /** Null identifies legacy exports that did not include group records. */
    val groups: List<ProviderGroup>?,
    /** Speech services use provider-backed credentials and travel with provider data. */
    val speechServices: List<SpeechService> = emptyList()
)
