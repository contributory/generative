package org.starfall.generative.integrations.remote

import java.io.InputStream

/** A bounded copy of the output already consumed by a terminal reader (UI or tool). */
internal class RemoteTerminalOutputCapture(private val capacity: Int = 256 * 1024) {
    init { require(capacity > 0) }

    private val bytes = ByteArray(capacity)
    private var start = 0
    private var size = 0

    @Synchronized
    fun append(source: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= source.size - length)
        if (length == 0) return
        val retained = minOf(length, capacity)
        val sourceStart = offset + length - retained
        if (retained == capacity) {
            source.copyInto(bytes, 0, sourceStart, sourceStart + capacity)
            start = 0
            size = capacity
            return
        }
        val overflow = (size + retained - capacity).coerceAtLeast(0)
        start = (start + overflow) % capacity
        size = minOf(capacity, size + retained)
        val end = (start + size - retained) % capacity
        val first = minOf(retained, capacity - end)
        source.copyInto(bytes, end, sourceStart, sourceStart + first)
        if (first < retained) source.copyInto(bytes, 0, sourceStart + first, sourceStart + retained)
    }

    @Synchronized
    private fun snapshot(): ByteArray = ByteArray(size).also { result ->
        val first = minOf(size, capacity - start)
        bytes.copyInto(result, 0, start, start + first)
        if (first < size) bytes.copyInto(result, first, 0, size - first)
    }

    /** The latest output lines, without terminal control sequences; never consumes SSH data. */
    fun latestLines(count: Int): String {
        require(count > 0)
        val raw = snapshot().toString(Charsets.UTF_8)
        val text = OSC.replace(raw, "").let { CSI.replace(it, "") }
            .replace(OTHER_ESCAPES, "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .filter { it == '\n' || it == '\t' || it >= ' ' }
        val lines = text.split('\n').let { if (text.endsWith('\n')) it.dropLast(1) else it }
        return lines.takeLast(count).joinToString("\n")
    }

    private companion object {
        val OSC = Regex("\\u001b\\][^\\u0007\\u001b]*(?:\\u0007|\\u001b\\\\)")
        val CSI = Regex("\\u001b\\[[0-?]*[ -/]*[@-~]")
        val OTHER_ESCAPES = Regex("\\u001b(?:[()][A-Za-z0-9]|[@-_])")
    }
}

/** Copies bytes as the existing reader consumes them; never competes for the stream. */
internal class CapturingTerminalInputStream(
    private val source: InputStream,
    private val capture: RemoteTerminalOutputCapture
) : InputStream() {
    override fun read(): Int = source.read().also { value ->
        if (value >= 0) capture.append(byteArrayOf(value.toByte()), 0, 1)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, len).also { read ->
        if (read > 0) capture.append(b, off, read)
    }

    override fun available(): Int = source.available()
    override fun close() = source.close()
}
