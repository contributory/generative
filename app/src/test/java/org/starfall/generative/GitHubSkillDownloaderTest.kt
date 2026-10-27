package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.starfall.generative.integrations.github.GitHubSkillLocation
import org.starfall.generative.integrations.github.parseGitHubSkillUrl

class GitHubSkillDownloaderTest {
    @Test fun acceptsRepositoryRootAndGitHubSkillFolder() {
        assertEquals(GitHubSkillLocation("owner", "repo", null, emptyList()),
            parseGitHubSkillUrl("https://github.com/owner/repo"))
        assertEquals(GitHubSkillLocation("owner", "repo", "main", listOf("skills", "writer")),
            parseGitHubSkillUrl("https://github.com/owner/repo/tree/main/skills/writer"))
    }

    @Test fun acceptsDirectSkillMdBlobAndRawUrl() {
        val expected = GitHubSkillLocation("org", "project", "develop", listOf("agent-skills", "writer"))
        assertEquals(expected,
            parseGitHubSkillUrl("https://github.com/org/project/blob/develop/agent-skills/writer/SKILL.md"))
        assertEquals(expected,
            parseGitHubSkillUrl("https://raw.githubusercontent.com/org/project/develop/agent-skills/writer/SKILL.md"))
    }

    @Test fun rejectsUnsafeOrIrrelevantLinks() {
        listOf(
            "https://evil.example/owner/repo/blob/main/SKILL.md",
            "http://github.com/owner/repo/blob/main/SKILL.md",
            "https://github.com.evil.example/owner/repo",
            "https://user:password@github.com/owner/repo",
            "https://github.com:444/owner/repo",
            "https://github.com/owner/repo/blob/main/README.md",
            "https://github.com/owner/repo/blob/main/../SKILL.md",
            "https://github.com/owner/repo?token=secret",
            "https://raw.githubusercontent.com/owner/repo/main/README.md",
            "https://github.com/owner/repo/tree/main/../../private",
            "https://github.com/owner/repo/issues/1",
            "https://github.com/owner",
            "not a URL"
        ).forEach { assertNull("Accepted unsafe link: $it", parseGitHubSkillUrl(it)) }
    }
}
