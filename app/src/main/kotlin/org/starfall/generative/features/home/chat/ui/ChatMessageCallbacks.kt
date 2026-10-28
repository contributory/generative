package org.starfall.generative.features.home.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Keeps LazyColumn message-card event parameters referentially stable when the parent
 * recomposes at a stream/tool boundary. Actions still dispatch using the latest state.
 */
@Composable
internal fun rememberChatAction(action: () -> Unit): () -> Unit {
    val latest = rememberUpdatedState(action)
    return remember { { latest.value() } }
}

@Composable
internal fun <T> rememberChatValueAction(action: (T) -> Unit): (T) -> Unit {
    val latest = rememberUpdatedState(action)
    return remember { { value: T -> latest.value(value) } }
}
