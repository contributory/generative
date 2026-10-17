package org.starfall.multigateway.integrations.languagemodels.account

import android.content.Context
import org.starfall.multigateway.integrations.languagemodels.openaicodex.OpenAICodexAdapter
import org.starfall.multigateway.integrations.languagemodels.claudecode.ClaudeCodeAdapter
import org.starfall.multigateway.integrations.languagemodels.antigravity.AntigravityAdapter
import org.starfall.multigateway.integrations.languagemodels.githubcopilot.GitHubCopilotAdapter
import org.starfall.multigateway.shared.model.ProviderType
import org.starfall.multigateway.shared.files.AttachmentResolver

/** Single integration point between core services and removable account adapters. */
internal class AccountProviderAdapterRegistry(
    context: Context,
    attachments: AttachmentResolver
) {
    private val adapters: Map<ProviderType, AccountProviderAdapter> = listOf(
        OpenAICodexAdapter(context, attachments),
        ClaudeCodeAdapter(context, attachments),
        AntigravityAdapter(context, attachments),
        GitHubCopilotAdapter(context, attachments)
    ).associateBy { it.providerType }

    fun get(type: ProviderType): AccountProviderAdapter? = adapters[type]

    fun clearCredentials(type: ProviderType, providerId: String) {
        adapters[type]?.clearCredentials(providerId)
    }
}
