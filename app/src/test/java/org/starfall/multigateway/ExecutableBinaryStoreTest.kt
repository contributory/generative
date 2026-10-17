package org.starfall.multigateway

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.multigateway.features.remotemachines.shared.ExecutableBinaryStore
import java.io.File

@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class ExecutableBinaryStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStore() {
        context.getSharedPreferences("executable_binaries", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "binaries").deleteRecursively()
    }

    @Test
    fun adoptedBinarySurvivesAStoreRecreation() = runBlocking {
        val legacyFile = File(context.filesDir, "remote-machines/bin/legacy-tool").apply {
            parentFile?.mkdirs()
            writeText("#!/system/bin/sh\necho ok")
        }

        val firstStore = ExecutableBinaryStore(context)
        firstStore.adoptLegacy("legacy-tool", legacyFile)

        val restoredStore = ExecutableBinaryStore(context)
        val binary = restoredStore.binaries.value.single()
        assertEquals("legacy-tool", binary.name)
        assertTrue(File(binary.path).isFile)
        assertTrue(File(binary.path).canExecute())
    }
}
