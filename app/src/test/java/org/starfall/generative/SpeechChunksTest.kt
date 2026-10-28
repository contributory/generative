package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.shared.media.speechChunks
import org.starfall.generative.shared.media.speechText

class SpeechChunksTest {
    @Test fun symbolsAndColonsAreNotSentToSpeechButAccentsAndSentenceBoundariesRemain() {
        assertEquals("Lưu ý Giá 12.5, rất tốt!", speechText("Lưu ý: Giá 12.5, rất tốt! ✅ ✨"))
        assertEquals("Résumé café tiếng Việt rõ ràng", speechText("Résumé: café ✅ — tiếng Việt ✨ **rõ ràng**"))
        assertEquals("", speechText(": ★ ✅ !!!"))
        assertEquals("你好。世界！", speechText("你好。世界！"))
    }

    @Test fun requestSizeUsesTheConfiguredSentenceCount() {
        for (limit in listOf(1, 3, 10, 15)) for (count in 1..35) {
            val sentences = (1..count).map { "Sentence $it." }
            val chunks = speechChunks(sentences.joinToString(" "), sentencesPerChunk = limit)
            assertEquals(sentences.joinToString(" "), chunks.joinToString(" "))
            chunks.forEachIndexed { index, chunk ->
                val actual = Regex("Sentence [0-9]+\\.").findAll(chunk).count()
                if (index < chunks.lastIndex) assertEquals(limit, actual)
                else assertTrue(actual in 1..limit)
            }
        }
        assertEquals(3, speechChunks((1..21).joinToString(" ") { "Sentence $it." }).size)
        assertThrows(IllegalArgumentException::class.java) { speechChunks("Text.", sentencesPerChunk = 0) }
    }

    @Test fun periodsAndNewlinesAreCoalescedAndDecimalsArePreserved() {
        assertEquals(listOf("One. Two", "Three. Four."), speechChunks("One.\nTwo\nThree. \n \nFour.\n", sentencesPerChunk = 2))
        assertEquals(listOf("A. B.", "C. D."), speechChunks("A.\n\nB.\r\nC. \nD.", sentencesPerChunk = 2))
        assertEquals(listOf("alpha beta", "gamma delta"), speechChunks("alpha\nbeta\ngamma\ndelta", sentencesPerChunk = 2))
        assertEquals(listOf("Value 12.5.", "Next."), speechChunks("Value 12.5.\nNext.", sentencesPerChunk = 1))
    }

    @Test fun longSentencesRespectCharacterLimitAndUnicodeBoundaries() {
        val text = ("Tiếng Việt rất rõ ràng. ").repeat(400).trim()
        val chunks = speechChunks(text, maxCharacters = 200)
        assertTrue(chunks.all { it.length <= 200 })
        assertEquals(text, chunks.joinToString(" "))
        val surrogateText = "a" + "\uD835\uDC00".repeat(20)
        val pieces = speechChunks(surrogateText, maxCharacters = 10)
        assertEquals(surrogateText, pieces.joinToString(""))
        assertTrue(pieces.none { Character.isLowSurrogate(it.first()) || Character.isHighSurrogate(it.last()) })
        assertTrue(speechChunks(" ").isEmpty())
    }
}
