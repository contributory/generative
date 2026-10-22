package org.starfall.generative.shared.storage.preferences

internal const val REMOTE_TERMINAL_UI_PREFERENCES = "remote_terminal_ui"
internal const val REMOTE_TERMINAL_FONT_SIZE_KEY = "font_dp"
internal const val REMOTE_TERMINAL_FORCE_MOUSE_KEY = "force_mouse"

internal fun remoteTerminalInputModePreferenceKey(machineId: String) = "input_mode:$machineId"
internal fun remoteTerminalImePreferenceKey(machineId: String) = "local_ime:$machineId"
