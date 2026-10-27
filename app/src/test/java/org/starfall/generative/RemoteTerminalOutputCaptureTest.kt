package org.starfall.generative

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.starfall.generative.integrations.remote.CapturingTerminalInputStream
import org.starfall.generative.integrations.remote.RemoteTerminalOutputCapture

class RemoteTerminalOutputCaptureTest {
    @Test fun readTerminalOutputDoesNotStealBytesFromTheConsumer() {
        val data = "one\ntwo\nthree\nfour\n".toByteArray()
        val capture = RemoteTerminalOutputCapture()
        val input = CapturingTerminalInputStream(ByteArrayInputStream(data), capture)
        assertEquals("", capture.latestLines(5))
        val consumed = ByteArray(data.size)
        assertEquals(3, input.read(consumed, 2, 3))
        assertEquals("one", capture.latestLines(1))
        val rest = input.readBytes()
        assertEquals(String(data), String(consumed, 2, 3) + String(rest))
        assertEquals("three\nfour", capture.latestLines(2))
        assertEquals("one\ntwo\nthree\nfour", capture.latestLines(10))
    }

    @Test fun limitsMemoryAndKeepsRequestedTail() {
        val capture = RemoteTerminalOutputCapture(capacity = 10)
        val input = "one\ntwo\nthree\nfour".toByteArray()
        capture.append(input, 0, 4)
        capture.append(input, 4, input.size - 4)
        assertEquals("three\nfour", capture.latestLines(2))
        assertEquals("four", capture.latestLines(1))
        assertThrows(IllegalArgumentException::class.java) { capture.latestLines(0) }
    }

    @Test fun stripsAnsiColorAndTerminalTitleWithoutStrippingOutputText() {
        val capture = RemoteTerminalOutputCapture()
        val data = "\u001b]0;secret title\u0007\u001b[31mred\u001b[0m\r\nXin chào\n".toByteArray()
        capture.append(data, 0, data.size)
        assertEquals("red\nXin chào", capture.latestLines(5))
    }

    @Test fun singleByteReadsAlsoRecordUtf8Data() {
        val capture = RemoteTerminalOutputCapture()
        val input = CapturingTerminalInputStream(ByteArrayInputStream("đúng\n".toByteArray()), capture)
        while (input.read() != -1) { }
        assertEquals("đúng", capture.latestLines(1))
    }
}
