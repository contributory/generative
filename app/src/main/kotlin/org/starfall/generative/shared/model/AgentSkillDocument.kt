package org.starfall.generative.shared.model

/** The editable SKILL.md representation, with its required YAML frontmatter and Markdown body. */
fun agentSkillDocument(skill: AgentSkill): String = buildString {
    appendLine("---")
    appendLine("name: ${skill.name}")
    if ('\n' in skill.description) {
        appendLine("description: |-")
        skill.description.lines().forEach { appendLine("  $it") }
    } else {
        appendLine("description: \"${skill.description.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
    skill.extraFrontmatter?.trimEnd()?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
    appendLine("---")
    appendLine()
    appendLine(skill.content.trim())
}

/** Parse a complete SKILL.md without interpreting the Markdown body as YAML. */
fun parseAgentSkillDocument(
    document: String,
    expectedName: String? = null,
    preserveEmptyExtras: Boolean = false
): AgentSkill? {
    val lines = document.removePrefix("\uFEFF").lines()
    if (lines.firstOrNull()?.trim() != "---") return null
    val closing = lines.drop(1).indexOfFirst { it.trim() == "---" }
        .takeIf { it >= 0 }?.plus(1) ?: return null
    val metadata = mutableMapOf<String, String>()
    val extras = mutableListOf<String>()
    var index = 1
    val fieldPattern = Regex("^([A-Za-z][A-Za-z0-9_-]*):\\s*(.*)$")
    while (index < closing) {
        val line = lines[index]
        val match = fieldPattern.matchEntire(line)
        if (match == null) {
            extras += line
            index++
            continue
        }
        val key = match.groupValues[1]
        val raw = match.groupValues[2].trim()
        index++
        val block = mutableListOf<String>()
        if (raw in listOf("|", "|-", ">", ">-")) {
            while (index < closing && (lines[index].startsWith(' ') || lines[index].startsWith('\t'))) {
                block += lines[index]
                index++
            }
        }
        if (key == "name" || key == "description") {
            if (metadata.containsKey(key)) return null
            val value = if (raw in listOf("|", "|-", ">", ">-")) {
                val trimmed = block.map { it.trimStart() }
                if (raw.startsWith('>')) trimmed.joinToString(" ") else trimmed.joinToString("\n")
            } else when {
                raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"') ->
                    raw.substring(1, raw.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                raw.length >= 2 && raw.startsWith('\'') && raw.endsWith('\'') ->
                    raw.substring(1, raw.length - 1).replace("''", "'")
                else -> raw
            }
            metadata[key] = value
        } else {
            extras += line
            extras += block
        }
    }
    val name = metadata["name"] ?: return null
    val description = metadata["description"]?.trim().orEmpty()
    val content = lines.drop(closing + 1).joinToString("\n").trim()
    if (!isValidAgentSkillName(name) || (expectedName != null && name != expectedName) ||
        description.isBlank() || description.length > 1024 || content.isBlank()) return null
    val extraText = extras.joinToString("\n").trim('\n')
    return AgentSkill(
        id = name, name = name, description = description, content = content,
        extraFrontmatter = extraText.takeIf { it.isNotBlank() || preserveEmptyExtras }
    )
}
