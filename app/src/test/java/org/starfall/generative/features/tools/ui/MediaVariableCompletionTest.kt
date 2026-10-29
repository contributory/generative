package org.starfall.generative.features.tools.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.shared.model.media.MediaRequestStep

class MediaVariableCompletionTest {
    @Test fun onlyCurrentUnfinishedOrCursorEditedTokenIsCompleted() {
        val text = "{{prompt}} & {{mo}} after"
        val cursor = text.indexOf("}} after")
        val token = mediaVariableToken(text, cursor)!!
        assertEquals("mo", token.prefix)
        val result = completeMediaVariable(TextFieldValue(text, TextRange(cursor)), token,
            MediaVariableCandidate("model", 0))
        assertEquals("{{prompt}} & {{model}} after", result.text)
        assertEquals(result.text.indexOf("}} after") + 2, result.selection.end)
        assertNull(mediaVariableToken(text, "{{prompt}}".length))
        assertNull(mediaVariableToken("plain text", 5))
        assertNull(mediaVariableToken("{{prompt}} rest", "{{prompt}} rest".length))
    }

    @Test fun unfinishedTokensPreserveAdjacentContentAndDoNotDuplicateBraces() {
        val raw = "prefix/{{st?x=1"
        val cursor = raw.indexOf('?')
        val token = mediaVariableToken(raw, cursor)!!
        val completed = completeMediaVariable(TextFieldValue(raw, TextRange(cursor)), token,
            MediaVariableCandidate("step1.url", 0))
        assertEquals("prefix/{{step1.url}}?x=1", completed.text)
        assertEquals("prefix/{{step1.url}}".length, completed.selection.end)

        val surrounded = "before {{pr}} and {{mo}}"
        val atPrefix = surrounded.indexOf("}} and")
        val fixed = completeMediaVariable(TextFieldValue(surrounded, TextRange(atPrefix)),
            mediaVariableToken(surrounded, atPrefix)!!, MediaVariableCandidate("prompt", 0))
        assertEquals("before {{prompt}} and {{mo}}", fixed.text)
    }

    @Test fun pathCompletionPlacesCaretBeforeClosingBraces() {
        val text = "https://api.example/jobs/{{step1.resp}}"
        val cursor = text.indexOf("}}")
        val candidate = MediaVariableCandidate("step1.response.body.", 0, pathHint = "path")
        val output = completeMediaVariable(TextFieldValue(text, TextRange(cursor)),
            mediaVariableToken(text, cursor)!!, candidate)
        assertEquals("https://api.example/jobs/{{step1.response.body.}}", output.text)
        assertEquals(output.text.indexOf("}}"), output.selection.end)
    }

    @Test fun onlyPreviousStepsAndExistingJsonPathsAreSuggested() {
        val step = MediaRequestStep(
            headers = mapOf("X-Request-Id" to "abc"),
            body = """{"data":{"job":{"id":"123"}},"inputs":[{"name":"a"}]}"""
        )
        val testedPaths = discoverMediaJsonPaths(Json.parseToJsonElement("""{"data":[{"url":"https://example.org"}]}"""))
        val candidates = mediaVariableCandidates(2, listOf(step), mapOf(1 to testedPaths))
        val names = candidates.map { it.value }
        assertTrue("step1.request.headers.X-Request-Id" in names)
        assertTrue("step1.request.body.data.job.id" in names)
        assertTrue("step1.request.body.inputs[0].name" in names)
        assertTrue("step1.response.body.data[0].url" in names)
        assertTrue("step1.response.body." in names)
        assertFalse(names.any { it.startsWith("step2.") })
        assertTrue(mediaVariableMatches(candidates, "step1.resp.body").isEmpty())
        assertEquals("step1.response.body.data[0].url",
            mediaVariableMatches(candidates, "step1.response.body.data[0].u").single().value)
        assertFalse(mediaVariableCandidates(1).any { it.value.startsWith("step1.") })
    }

    @Test fun malformedOrClosedTokensDoNotTriggerSuggestions() {
        assertNull(mediaVariableToken("{{prompt}}", 10))
        assertNull(mediaVariableToken("{{first}} {{second}}", 9))
        assertNull(mediaVariableToken("{{foo\nbar", 9))
        assertNull(mediaVariableToken("{{foo}} {other}", 14))
    }
}
