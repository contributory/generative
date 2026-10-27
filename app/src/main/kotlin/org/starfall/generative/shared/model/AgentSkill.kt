package org.starfall.generative.shared.model

import kotlinx.serialization.Serializable

/** A reusable Agent Skill whose instructions are loaded by the agent on activation. */
@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val content: String,
    val description: String = "",
    /** Optional YAML frontmatter, excluding the required name and description. Null retains existing metadata. */
    val extraFrontmatter: String? = null
)

/** A separately managed collection of reusable agent skills. */
@Serializable
data class AgentSkills(
    val skills: List<AgentSkill> = defaultAgentSkills()
)

const val DEFAULT_AGENT_SKILL_ID = "create-skill"

// UI-only fallback before preferences are loaded. In the running app the authoritative
// create-skill instructions are read from assets/agent-skills/create-skill/SKILL.md.
val DEFAULT_AGENT_SKILL = AgentSkill(
    id = DEFAULT_AGENT_SKILL_ID,
    name = "create-skill",
    content = "Create a reusable skill with clear instructions.",
    description = "Create a reusable Agent Skill when the user asks for a new skill."
)

fun defaultAgentSkills(): List<AgentSkill> = listOf(DEFAULT_AGENT_SKILL)

private val AGENT_SKILL_NAME_PATTERN = Regex("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?")

fun isValidAgentSkillName(name: String): Boolean =
    name.length in 1..64 &&
        !name.contains("--") &&
        AGENT_SKILL_NAME_PATTERN.matches(name)

/** Converts an editor label or legacy skill name to the directory-safe Agent Skills name. */
fun normalizeAgentSkillName(value: String): String =
    value.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .replace(Regex("-{2,}"), "-")
        .take(64)
        .trim('-')
        .ifBlank { "new-skill" }
