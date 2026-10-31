package org.starfall.generative.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class SavedInstruction(val id: String, val name: String, val content: String, val role: ChatRole = ChatRole.SYSTEM)

@Serializable
data class InstructionLibrary(
    /** Stored JSON keeps the original `prompts` key so existing libraries still load. */
    @SerialName("prompts") val instructions: List<SavedInstruction> = emptyList(),
    val selectedIds: Set<String> = emptySet()
) {
    val activeInstructions: List<SavedInstruction> get() = instructions.filter { it.id in selectedIds }
    /** The library as one chat sees it: its own instruction selection, or the default when it has none. */
    fun forChat(chatSelectedIds: Set<String>?): InstructionLibrary =
        if (chatSelectedIds == null) this else copy(selectedIds = chatSelectedIds)
    fun systemPrompt(at: ZonedDateTime = ZonedDateTime.now()): String =
        activeInstructions.filter { it.role == ChatRole.SYSTEM }
            .joinToString("\n\n") { it.content.renderDateTime(at) }

    fun roleMessages(at: ZonedDateTime = ZonedDateTime.now()): List<StoredMessage> =
        activeInstructions.filter { it.role != ChatRole.SYSTEM }.map {
            StoredMessage("instruction_${it.id}", it.role,
                listOf(MessageVersion(content = it.content.renderDateTime(at))))
        }
    companion object {
        fun fromLegacy(instruction: String): InstructionLibrary = if (instruction.isBlank()) InstructionLibrary() else InstructionLibrary(
            listOf(SavedInstruction("legacy-system-prompt", "Default system instruction", instruction)), setOf("legacy-system-prompt"))
    }
}

private fun String.renderDateTime(at: ZonedDateTime): String =
    if ("{{datetime}}" in this) replace("{{datetime}}", at.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    else this
