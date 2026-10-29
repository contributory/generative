package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.home.chat.logic.retainChatStreams
import org.starfall.generative.features.home.chat.model.*

class ChatStreamRetentionTest {
    private fun stream(id: String, frozen: Boolean) = ActiveChatStreamState(
        conversationId = id, messageId = "$id-message", generationId = 1, revision = 1,
        status = ChatStreamStatus.STREAMING, content = StreamTextSnapshot.of("visible"),
        reasoning = StreamTextSnapshot.of(), stopSignal = StreamFreezeSignal().also { if (frozen) it.freeze() },
        lastCommitMetrics = null, cumulativeMetrics = StreamMetricsSnapshot()
    )

    @Test fun onlyMountedFrozenSnapshotSurvivesAlongsideUnrelatedLiveStreams() {
        val current = stream("current", true)
        val obsolete = stream("obsolete", true)
        val active = stream("active", false)
        val retained = retainChatStreams(mapOf("current" to current, "obsolete" to obsolete, "active" to active), "current")
        assertEquals(setOf("current", "active"), retained.keys)
        assertSame(current, retained["current"])
        assertSame(active, retained["active"])
        assertEquals(setOf("active"), retainChatStreams(retained, null).keys)
    }
}
