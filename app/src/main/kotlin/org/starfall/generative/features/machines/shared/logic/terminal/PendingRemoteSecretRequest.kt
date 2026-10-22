package org.starfall.generative.features.machines.shared.logic.terminal

import kotlinx.coroutines.CompletableDeferred
import org.starfall.generative.shared.model.remote.RemoteSecretRequest

internal class PendingRemoteSecretRequest(
    val request: RemoteSecretRequest,
    val answer: CompletableDeferred<String?>
)
