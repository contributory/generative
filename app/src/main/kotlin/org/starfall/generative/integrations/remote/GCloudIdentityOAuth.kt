package org.starfall.generative.integrations.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import org.starfall.generative.shared.model.remote.REMOTE_GOOGLE_CLOUD_SCOPE

/** Results from the Google Identity SDK before any Android UI resolution is launched. */
internal sealed interface GCloudIdentityAuthorization {
    data class NeedsResolution(val pendingIntent: PendingIntent) : GCloudIdentityAuthorization
    data class AccessToken(val value: String) : GCloudIdentityAuthorization
    data object MissingResolution : GCloudIdentityAuthorization
    data object MissingAccessToken : GCloudIdentityAuthorization
}

/** Encapsulates Google Identity SDK requests and result parsing for Cloud Shell authorization. */
internal class GCloudIdentityOAuth(private val context: Context) {
    fun authorize(onResult: (Result<GCloudIdentityAuthorization>) -> Unit) {
        Identity.getAuthorizationClient(context).authorize(authorizationRequest())
            .addOnSuccessListener { result ->
                val authorization = if (result.hasResolution()) {
                    result.pendingIntent?.let { GCloudIdentityAuthorization.NeedsResolution(it) }
                        ?: GCloudIdentityAuthorization.MissingResolution
                } else {
                    result.accessToken?.takeIf(String::isNotBlank)?.let { GCloudIdentityAuthorization.AccessToken(it) }
                        ?: GCloudIdentityAuthorization.MissingAccessToken
                }
                onResult(Result.success(authorization))
            }
            .addOnFailureListener { error -> onResult(Result.failure(error)) }
    }

    /** Returns null when Google completes without an access token; SDK parsing failures propagate. */
    fun accessTokenFromIntent(data: Intent?): String? =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            .accessToken?.takeIf(String::isNotBlank)

    companion object {
        /** Shared request definition for interactive authorization and silent token renewal. */
        fun authorizationRequest(): AuthorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(REMOTE_GOOGLE_CLOUD_SCOPE)))
            .build()
    }
}
