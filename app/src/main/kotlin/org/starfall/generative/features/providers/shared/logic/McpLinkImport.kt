package org.starfall.generative.features.providers.shared.logic

import java.util.UUID
import org.starfall.generative.shared.model.McpAuthorization
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.auth.CredentialLoadState

/** A changed endpoint invalidates cached tools and secrets; unchanged endpoints keep their settings. */
internal fun mergeMcpLink(existing: McpInfo?, name: String, url: String): McpInfo {
    if (existing == null) return McpInfo(id = UUID.randomUUID().toString(), name = name, url = url)
    val endpointChanged = existing.url != url
    return existing.copy(
        name = name,
        url = url,
        auth = if (endpointChanged) McpAuthorization() else existing.auth,
        headers = if (endpointChanged) null else existing.headers,
        cachedTools = if (endpointChanged) null else existing.cachedTools,
        credentialState = CredentialLoadState.LOADED
    )
}
