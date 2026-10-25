package org.starfall.generative.features.home.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamResponseTextSplitterTest {
    @Test
    fun ordinaryTextIsReturnedExactly() {
        val result = parse("Xin chào, thế giới!\n")
        assertEquals("Xin chào, thế giới!\n", result.content)
        assertEquals("", result.reasoning)
    }

    @Test
    fun thinkWrapperSeparatesReasoningAndTrimsOnlyTheVisiblePrefix() {
        val result = parse("<think>Planning the answer</think>\n  The answer")
        assertEquals("Planning the answer", result.reasoning)
        assertEquals("The answer", result.content)
    }

    @Test
    fun markerSplitsAcrossEveryPossiblePairOfFragments() {
        val input = "<think>reasoning\nline</think>\r\nvisible\ntext"
        val expected = StreamResponseText("visible\ntext", "reasoning\nline")

        for (split in 0..input.length) {
            assertEquals("split at $split", expected, parseWithSplit(input, split))
        }
    }

    @Test
    fun incompleteOpeningTagRemainsLiteralVisibleText() {
        assertEquals(StreamResponseText("<thi", ""), parse("<thi"))
    }

    @Test
    fun unfinishedReasoningAndPartialClosingTagAreRetainedAtFinalization() {
        assertEquals(StreamResponseText("", "secret</thi"), parse("<think>secret</thi"))
    }

    @Test
    fun resumedContentDoesNotTreatAContinuationAsANewThinkWrapper() {
        val splitter = StreamResponseTextSplitter(
            initialContentLength = "Existing".length,
            alreadyInsideVisibleContent = true
        )
        val appended = splitter.append(" <think>literal")
        val final = splitter.finish()

        assertEquals(" <think>literal", appended.content + final.content)
        assertEquals("", appended.reasoning + final.reasoning)
        assertEquals("Existing".length + " <think>literal".length, splitter.visibleContentLength)
    }

    private fun parse(input: String): StreamResponseText {
        val splitter = StreamResponseTextSplitter()
        val appended = splitter.append(input)
        val final = splitter.finish()
        return StreamResponseText(appended.content + final.content, appended.reasoning + final.reasoning)
    }

    private fun parseWithSplit(input: String, split: Int): StreamResponseText {
        val splitter = StreamResponseTextSplitter()
        val first = splitter.append(input.substring(0, split))
        val second = splitter.append(input.substring(split))
        val final = splitter.finish()
        return StreamResponseText(
            first.content + second.content + final.content,
            first.reasoning + second.reasoning + final.reasoning
        )
    }
}
