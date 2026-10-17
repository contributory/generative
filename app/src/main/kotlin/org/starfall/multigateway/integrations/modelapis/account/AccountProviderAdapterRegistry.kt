package org.starfall.multigateway.integrations.modelapis.account

import android.content.Context
import org.starfall.multigateway.integrations.modelapis.openaicodex.OpenAICodexAdapter
import org.starfall.multigateway.integrations.modelapis.claudecode.ClaudeCodeAdapter
import org.starfall.multigateway.integrations.modelapis.antigravity.AntigravityAdapter
import org.starfall.multigateway.integrations.modelapis.githubcopilot.GitHubCopilotAdapter
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.files.AttachmentResolver

/** Single integration point between core services and removable account adapters. */
internal class AccountProviderAdapterRegistry(
    context: Context,
    attachments: AttachmentResolver
) {
    private val adapters: Map<ModelApiType, AccountProviderAdapter> = listOf(
        OpenAICodexAdapter(context, attachments),
        ClaudeCodeAdapter(context, attachments),
        AntigravityAdapter(context, attachments),
        GitHubCopilotAdapter(context, attachments)
    ).associateBy { it.providerType }

    fun get(type: ModelApiType): AccountProviderAdapter? = adapters[type]

    fun clearCredentials(type: ModelApiType, providerId: String) {
        adapters[type]?.clearCredentials(providerId)
    }
}
