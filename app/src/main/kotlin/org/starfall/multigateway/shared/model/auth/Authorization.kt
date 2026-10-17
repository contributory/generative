package org.starfall.multigateway.shared.model.auth

import kotlinx.serialization.Serializable

@Serializable
data class Authorization(
    val method: AuthMethod = AuthMethod.PLATFORM_DEFAULT,
    val key: String? = null,
    val value: String? = null,
    val oauthAccountId: String? = null
) {
    // OAuth stores only an opaque adapter marker here; real tokens never enter provider config.
    val token: String
        get() = when {
            method in listOf(AuthMethod.OAUTH, AuthMethod.NONE) -> ""
            value != null -> value
            method in listOf(AuthMethod.BEARER_TOKEN, AuthMethod.CUSTOM_HEADER, AuthMethod.QUERY_PARAM) -> ""
            else -> key.orEmpty() // Legacy API keys were stored in this field.
        }
}
