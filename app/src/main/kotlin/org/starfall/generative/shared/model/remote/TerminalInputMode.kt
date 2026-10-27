package org.starfall.generative.shared.model.remote

internal enum class TerminalInputMode {
    REMOTE,
    LOCAL;

    val usesLocalInput: Boolean get() = this == LOCAL
}
