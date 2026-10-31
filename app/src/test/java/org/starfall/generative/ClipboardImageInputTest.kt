package org.starfall.generative

import android.content.ClipData
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.starfall.generative.features.home.chat.logic.input.clipboardImageUris

@RunWith(RobolectricTestRunner::class)
class ClipboardImageInputTest {
    private val png = Uri.parse("content://photos/first.png")
    private val jpeg = Uri.parse("content://photos/second.jpg")
    private val document = Uri.parse("content://documents/file.pdf")

    @Test fun imageMetadataAcceptsMultipleUrisAndDeduplicates() {
        val clip = ClipData("images", arrayOf("image/png"), ClipData.Item(png))
        clip.addItem(ClipData.Item(jpeg))
        clip.addItem(ClipData.Item(png))
        assertEquals(listOf(png, jpeg), clipboardImageUris(clip) { null })
    }

    @Test fun resolvedMimeAcceptsImageWithinTextOrMixedClipboard() {
        val clip = ClipData("mixed", arrayOf("text/plain"), ClipData.Item("Hello"))
        clip.addItem(ClipData.Item(png))
        clip.addItem(ClipData.Item(document))
        assertEquals(listOf(png), clipboardImageUris(clip) {
            if (it == png) "image/png" else "application/pdf"
        })
    }

    @Test fun plainTextAndNonImageUrisRemainUnconsumed() {
        val clip = ClipData("text", arrayOf("text/plain"), ClipData.Item("Hello"))
        clip.addItem(ClipData.Item(document))
        assertTrue(clipboardImageUris(clip) { "application/pdf" }.isEmpty())
    }

    @Test fun imageClipDoesNotImportAnExplicitlyNonImageUri() {
        val clip = ClipData("mixed", arrayOf("image/png", "application/pdf"), ClipData.Item(png))
        clip.addItem(ClipData.Item(document))
        assertEquals(listOf(png), clipboardImageUris(clip) {
            if (it == png) "image/png" else "application/pdf"
        })
    }

    @Test fun inaccessibleProviderDoesNotCrashDetection() {
        val clip = ClipData("images", arrayOf("image/png"), ClipData.Item(png))
        assertEquals(listOf(png), clipboardImageUris(clip) { throw SecurityException("Provider denied") })
    }
}
