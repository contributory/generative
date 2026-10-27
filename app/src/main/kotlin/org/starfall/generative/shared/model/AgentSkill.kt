package org.starfall.generative.shared.model

import kotlinx.serialization.Serializable

/** A reusable set of instructions that can be inserted into the chat composer. */
@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val content: String
)

/** A separately managed collection of reusable agent skills. */
@Serializable
data class AgentSkills(
    val skills: List<AgentSkill> = defaultAgentSkills()
)

const val DEFAULT_AGENT_SKILL_ID = "create-skill"

val DEFAULT_AGENT_SKILL = AgentSkill(
    id = DEFAULT_AGENT_SKILL_ID,
    name = "Create Skill",
    content = "Create a reusable skill with clear instructions."
)

fun defaultAgentSkills(): List<AgentSkill> = listOf(DEFAULT_AGENT_SKILL)
