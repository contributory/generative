package org.starfall.multigateway.shared.model.generation

import org.starfall.multigateway.shared.model.StoredMessage

/** True when any answer in the conversation used a tool; connection handshakes do not count. */
fun List<StoredMessage>.hasToolCalls(): Boolean =
    any { message -> message.activeVersion.toolActivity.any { !it.name.endsWith(": connect") } }
