package org.starfall.multigateway.shared.ui.logging

import androidx.annotation.StringRes
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.model.logging.DebugLogCategory

@StringRes
fun DebugLogCategory.labelRes(): Int = when (this) {
    DebugLogCategory.NETWORK -> R.string.debug_log_category_network
    DebugLogCategory.PAYLOAD -> R.string.debug_log_category_payload
    DebugLogCategory.TOOLS -> R.string.debug_log_category_tools
    DebugLogCategory.TOKENS -> R.string.debug_log_category_tokens
    DebugLogCategory.ERRORS -> R.string.debug_log_category_errors
}
