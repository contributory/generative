package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.home.chat.logic.markdown.stableMarkdownPrefixLength

class StableMarkdownPrefixTest {
    private fun offsetOf(text: String, marker: String): Int = text.indexOf(marker).also { assertTrue(it >= 0) }

    @Test fun emptyAndSingleBlockHaveNothingToFreeze() {
        assertEquals(0, stableMarkdownPrefixLength(""))
        assertEquals(0, stableMarkdownPrefixLength("hello"))
    }

    @Test fun blankLineSeparatedBlocksFreezeUpToTheLastBlockStart() {
        assertEquals(offsetOf("a\n\nb", "b"), stableMarkdownPrefixLength("a\n\nb"))
        assertEquals(offsetOf("a\n\nb\n\nc", "c"), stableMarkdownPrefixLength("a\n\nb\n\nc"))
    }

    @Test fun trailingBlankLinesWithoutNextBlockDoNotCut() {
        assertEquals(0, stableMarkdownPrefixLength("a\n\n"))
    }

    @Test fun blankLineInsideClosedFenceIsIgnored() {
        val text = "a\n\n```kotlin\nx\n\ny\n```\n\nb"
        assertEquals(offsetOf(text, "b"), stableMarkdownPrefixLength(text))
    }

    @Test fun unclosedFenceStartsTheTail() {
        val text = "a\n\n```kotlin\nx\n\ny"
        assertEquals(offsetOf(text, "```kotlin"), stableMarkdownPrefixLength(text))
    }

    @Test fun longerClosingTildeFenceClosesTheBlock() {
        val text = "~~~\nx\n\n~~~~\n\nb"
        assertEquals(offsetOf(text, "b"), stableMarkdownPrefixLength(text))
    }

    @Test fun looseListIsNeverSplit() {
        assertEquals(0, stableMarkdownPrefixLength("- a\n\n- b\n\n- c"))
        assertEquals(0, stableMarkdownPrefixLength("1. a\n\n2. b\n\n3. c"))
    }

    @Test fun paragraphAfterListStartsNewBlock() {
        val text = "- a\n\ntext"
        assertEquals(offsetOf(text, "text"), stableMarkdownPrefixLength(text))
    }

    @Test fun indentedLineContinuesListItem() {
        val text = "1. a\n\n   continued\n\nnext"
        assertEquals(offsetOf(text, "next"), stableMarkdownPrefixLength(text))
    }

    @Test fun htmlBlockIsNotCut() {
        assertEquals(0, stableMarkdownPrefixLength("<div>\n\nx\n\ny"))
    }

    @Test fun tableFollowedByParagraph() {
        val text = "| a | b |\n|---|---|\n| 1 | 2 |\n\ntext"
        assertEquals(offsetOf(text, "text"), stableMarkdownPrefixLength(text))
    }

    @Test fun crlfLineEndings() {
        val text = "a\r\n\r\nb"
        assertEquals(offsetOf(text, "b"), stableMarkdownPrefixLength(text))
    }

    @Test fun vietnameseText() {
        val text = "Xin chào\n\nThế giới"
        assertEquals(offsetOf(text, "Thế"), stableMarkdownPrefixLength(text))
    }

    @Test fun unfinishedListMarkerIsUndecided() {
        // "1" may still become "1. item", which would extend the list instead of starting a block.
        assertEquals(0, stableMarkdownPrefixLength("- a\n\n1"))
        assertEquals(0, stableMarkdownPrefixLength("- a\n\n12"))
        // "-" and "1." are list markers already; the list continues.
        assertEquals(0, stableMarkdownPrefixLength("- a\n\n-"))
        assertEquals(0, stableMarkdownPrefixLength("- a\n\n1."))
    }

    @Test fun completedListAfterUndecidedMarkerStillStaysTogether() {
        val text = "- a\n\n1. b\n\ntext"
        assertEquals(offsetOf(text, "text"), stableMarkdownPrefixLength(text))
    }

    @Test fun bareListMarkerIsAListItem() {
        assertEquals(0, stableMarkdownPrefixLength("- a\n\n-\n\n- c"))
    }

    private val samples = listOf(
        "Introduction\n\n## Heading\n- [x] Task\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n```kotlin\n  val x = 1\n```\n[link](https://example.com)",
        "Intro with \$x^2\$ math\n\n\$\$\n\\frac{a}{b}\n\$\$\n\nNext paragraph.\n\n- one\n- two\n\n  more\n\n1. first\n2. second\n\n> quote\n> more\n\n---\n\nEnd.",
        "~~~\nx\n\n~~~~\n\nb\n\n```\nunclosed\n\nstill code",
        "Xin chào\r\n\r\nThế giới\r\n\r\n- việc một\r\n- việc hai\r\n\r\n<div>\r\n\r\nhtml\r\n\r\ntail",
        "1. a\n\n   continued\n\n2. b\n\n   ```\n   code\n\n   more\n   ```\n\nfinal\n"
    )

    @Test fun everyPrefixCutsOnlyAtBlockStarts() {
        for (document in samples) {
            var previous = 0
            for (length in 0..document.length) {
                val prefix = document.take(length)
                val cut = stableMarkdownPrefixLength(prefix)
                // A cut never moves backwards when more text arrives.
                assertTrue("cut moved back at length $length", cut >= previous)
                // A cut points at the start of a non-blank line inside the prefix.
                assertTrue("cut outside prefix at length $length", cut == 0 || (cut < length && !prefix[cut].isWhitespace()))
                // The tail after a cut starts a new block, so the next cut is inside the tail.
                val tail = document.substring(cut)
                assertTrue(stableMarkdownPrefixLength(tail) < tail.length || tail.isEmpty())
                previous = cut
            }
        }
    }
}
