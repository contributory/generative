package org.starfall.generative.features.appdata.model

import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup

/** Parsed backup. Null collections mean that the file did not contain that data category. */
data class FullAppDataExport(
    val providers: List<ModelProvider>? = null,
    val providerGroups: List<ProviderGroup>? = null,
    val mcpServers: List<McpInfo>? = null,
    val speechServices: List<SpeechService>? = null,
    val conversations: List<Conversation>? = null
)
