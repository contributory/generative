package org.starfall.generative.shared.model.remote

internal enum class TerminalInputMode {
    REMOTE,
    LOCAL,
    IME;

    val usesLocalInput: Boolean get() = this != REMOTE
}
