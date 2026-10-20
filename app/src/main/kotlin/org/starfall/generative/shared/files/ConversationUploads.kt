package org.starfall.generative.shared.files

import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.Conversation

/**
 * Names of the app-private files the user attached to this conversation, across every edited version of
 * their messages. User-owned documents (`content://` URIs) are never included: the app does not own them.
 *
 * Files are stored once per content hash, so a file the user re-attached from Storage may also be something
 * the assistant or a tool produced here. Those are left out, because they are generated output rather than
 * an upload.
 */
internal fun Conversation.uploadedFileNames(files: ToolFiles): Set<String> {
    val uploaded = messages.filter { it.role == ChatRole.USER }
        .flatMap { message -> message.versions.flatMap { it.files } }
        .mapNotNull(files::nameOf)
    val produced = messages.filter { it.role != ChatRole.USER }
        .flatMap { message -> message.versions.flatMap { version -> version.files + version.toolActivity.flatMap { it.files } } }
        .mapNotNull(files::nameOf)
        .toSet()
    return uploaded.toSet() - produced
}
