package org.starfall.generative.shared.model.auth

import kotlinx.serialization.SerialName

enum class AuthMethod {
    @SerialName("platform_default")
    PLATFORM_DEFAULT,
    @SerialName("none")
    NONE,
    @SerialName("query_param")
    QUERY_PARAM,
    @SerialName("bearer_token")
    BEARER_TOKEN,
    @SerialName("custom_header")
    CUSTOM_HEADER,
    @SerialName("oauth")
    OAUTH,
    @SerialName("other")
    OTHER
}
