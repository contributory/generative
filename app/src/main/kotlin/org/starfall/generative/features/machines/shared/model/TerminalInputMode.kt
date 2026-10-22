package org.starfall.generative.features.machines.shared.model

internal enum class TerminalInputMode {
    REMOTE,
    LOCAL,
    IME;

    val usesLocalInput: Boolean get() = this != REMOTE
}
