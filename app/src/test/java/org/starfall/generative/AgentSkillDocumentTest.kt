package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.shared.model.AgentSkill
import org.starfall.generative.shared.model.agentSkillDocument
import org.starfall.generative.shared.model.parseAgentSkillDocument

class AgentSkillDocumentTest {
    @Test fun aWholePastedSkillMdParsesAndPreservesOptionalYamlAndMarkdown() {
        val pasted = """---
name: review-code
description: Review code when requested
license: MIT
compatibility: Requires git
metadata:
  author: reviewer
  version: "1.0"
allowed-tools: Read
---

# Code Review

## Instructions

1. Check tests.
2. Inspect changes.
""".trimIndent()
        val skill = parseAgentSkillDocument(pasted, preserveEmptyExtras = true)
        assertNotNull(skill)
        assertEquals("review-code", skill!!.name)
        assertEquals("Review code when requested", skill.description)
        assertEquals("# Code Review\n\n## Instructions\n\n1. Check tests.\n2. Inspect changes.", skill.content)
        assertTrue(skill.extraFrontmatter!!.contains("license: MIT"))
        assertTrue(skill.extraFrontmatter!!.contains("  author: reviewer"))
        assertTrue(skill.extraFrontmatter!!.contains("allowed-tools: Read"))
        assertEquals(skill, parseAgentSkillDocument(agentSkillDocument(skill), preserveEmptyExtras = true))
    }

    @Test fun oneDocumentRoundTripsBasicAndMultiLineDescription() {
        val simple = AgentSkill("document", "document", "## Instructions\nDo it", "A small skill")
        assertEquals(simple, parseAgentSkillDocument(agentSkillDocument(simple)))
        val multiline = simple.copy(description = "First line\nSecond line")
        assertEquals(multiline, parseAgentSkillDocument(agentSkillDocument(multiline)))
    }

    @Test fun invalidPastedDocumentsCannotBeSaved() {
        assertNull(parseAgentSkillDocument("# Markdown only"))
        assertNull(parseAgentSkillDocument("---\nname: Wrong Name\ndescription: do things\n---\nText"))
        assertNull(parseAgentSkillDocument("---\nname: test\ndescription: \n---\nText"))
        assertNull(parseAgentSkillDocument("---\nname: test\ndescription: do things\n---\n"))
        assertNull(parseAgentSkillDocument("---\nname: test\nname: other\ndescription: do things\n---\nText"))
        assertNull(parseAgentSkillDocument("---\nname: renamed\ndescription: do things\n---\nText", expectedName = "old"))
    }

    @Test fun clearingOptionalYamlIsAnExplicitEdit() {
        val edited = parseAgentSkillDocument("---\nname: example\ndescription: Example skill\n---\n\n# Example", preserveEmptyExtras = true)
        assertNotNull(edited)
        assertEquals("", edited!!.extraFrontmatter)
        assertFalse(agentSkillDocument(edited).contains("license:"))
        assertEquals(null, parseAgentSkillDocument(agentSkillDocument(edited))?.extraFrontmatter)
    }
}
