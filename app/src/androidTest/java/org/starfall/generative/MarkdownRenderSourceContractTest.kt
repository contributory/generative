package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot

/** Regression tests for the source model used by the unified measured Markdown render path. */
@RunWith(AndroidJUnit4::class)
class MarkdownRenderSourceContractTest {
    @Test
    fun snapshotMaterializationPreservesFullResponseBeyondMarkdownParserLimit() {
        val source = "HEAD_MARKER\n" + "x".repeat(140_000) + "\nTAIL_MARKER"
        val snapshot = StreamTextSnapshot.of(source)

        val materialized = snapshot.materialize()
        assertEquals(source.length, materialized.length)
        assertTrue(materialized.startsWith("HEAD_MARKER"))
        assertTrue(materialized.endsWith("TAIL_MARKER"))
    }

    @Test
    fun snapshotSlicesPreserveTheTailAcrossTheParserBoundary() {
        val source = "a".repeat(128_000) + "VISIBLE_TAIL_MARKER" + "z".repeat(4_000)
        val snapshot = StreamTextSnapshot.of(source)
        val tail = snapshot.slice(128_000, source.length)

        assertTrue(tail.startsWith("VISIBLE_TAIL_MARKER"))
        assertEquals(source.length - 128_000, tail.length)
    }
}
