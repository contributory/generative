package org.starfall.generative

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.database.AppDatabase
import org.starfall.generative.database.entities.SpeechServiceEntity
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.database.SpeechRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SpeechServiceStorageTest {
    @Test fun speechOptionsSurviveRepositoryReloadAndReordering() = runBlocking {
        installTestAndroidKeyStore()
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val service = SpeechService("s", "Voice", icon = "https://example.com/voice.png", provider = "p", modelId = "tts", voice = "Kore",
                instructions = "Speak calmly", responseFormat = "wav", languageCode = "vi-VN",
                extraBody = buildJsonObject { put("custom", "value") })
            SpeechRepository(db).saveService(service)
            assertEquals(service, SpeechRepository(db).getById("s"))
            SpeechRepository(db).reorderServices(listOf("s"))
            assertEquals(service.copy(sortOrder = 0), SpeechRepository(db).getById("s"))
        } finally { db.close() }
    }

    @Test fun standaloneEnginesKeepEncryptedKeysAndVoiceIdentity() = runBlocking {
        installTestAndroidKeyStore()
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            org.starfall.generative.shared.model.SpeechEngine.entries.filter { it.standalone }.forEach { engine ->
                val service = SpeechService(engine.name, "Standalone", provider = "", engine = engine,
                    modelId = "engine-model", voice = "voice-id", voiceName = "Friendly name", apiKey = "private-key")
                SpeechRepository(db).saveService(service)
                assertEquals(service, SpeechRepository(db).getById(service.id))
                val entity = db.speechServiceDao().getServiceById(service.id)!!
                val auth = db.speechServiceDao().getAuth(entity.authId!!)!!
                assertNotEquals(service.apiKey, auth.encryptedApiKey)
                assertTrue(auth.encryptedApiKey.startsWith("keystore:v1:"))
            }
        } finally { db.close() }
    }

    @Test fun legacyRowsWithoutOptionsKeepDefaults() = runBlocking {
        installTestAndroidKeyStore()
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            SpeechRepository(db).saveService(SpeechService("s", "Old", provider = "system", voice = "Default", speed = 1f, pitch = 1f, sortOrder = 0))
            val service = SpeechRepository(db).getById("s")!!
            assertEquals("mp3", service.responseFormat)
            assertEquals("", service.instructions)
            assertTrue(service.extraBody.isEmpty())
        } finally { db.close() }
    }
}
