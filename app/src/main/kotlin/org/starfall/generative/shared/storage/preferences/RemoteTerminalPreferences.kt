package org.starfall.generative.shared.storage.preferences

import android.content.SharedPreferences
import org.starfall.generative.shared.model.remote.TerminalInputMode

internal const val REMOTE_TERMINAL_UI_PREFERENCES = "remote_terminal_ui"
internal const val REMOTE_TERMINAL_FONT_SIZE_KEY = "font_dp"
internal const val REMOTE_TERMINAL_FORCE_MOUSE_KEY = "force_mouse"

internal fun remoteTerminalInputModePreferenceKey(machineId: String) = "input_mode:$machineId"
internal fun remoteTerminalImePreferenceKey(machineId: String) = "local_ime:$machineId"

internal data class RemoteTerminalInputPreferences(val mode: TerminalInputMode, val imeEnabled: Boolean)

internal fun SharedPreferences.remoteTerminalInputPreferences(machineId: String): RemoteTerminalInputPreferences {
    val stored = getString(remoteTerminalInputModePreferenceKey(machineId), null)
    // Older versions stored IME as a third (local) mode, or used local_ime with LOCAL.
    val mode = if (stored == "IME" || stored == TerminalInputMode.LOCAL.name) {
        TerminalInputMode.LOCAL
    } else TerminalInputMode.REMOTE
    if (stored == "IME") {
        // Normalize once so an old local_ime=false cannot override the explicit legacy IME mode,
        // and subsequent IME toggles remain independent of routing.
        edit().putString(remoteTerminalInputModePreferenceKey(machineId), mode.name)
            .putBoolean(remoteTerminalImePreferenceKey(machineId), true).apply()
    }
    return RemoteTerminalInputPreferences(
        mode,
        getBoolean(remoteTerminalImePreferenceKey(machineId), mode == TerminalInputMode.LOCAL)
    )
}
