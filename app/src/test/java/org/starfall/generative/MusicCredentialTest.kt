package org.starfall.generative

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.integrations.security.SecretCipher
import org.starfall.generative.shared.model.*
import org.starfall.generative.shared.model.media.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MusicCredentialTest {
    @Test fun persistedMusicCredentialIsAnEnvelopeAndUnsafeImportsAreRejected() {
        installTestAndroidKeyStore()
        val encrypted = SecretCipher.encrypt("music-private-key")
        val profile = MediaRequestProfile("music", "Music", encryptedCredential = encrypted,
            chain = MediaRequestChain(steps = listOf(MediaRequestStep(endpoint = "https://music.example",
                headers = mapOf("Authorization" to "Bearer {{api_token}}"))),
                finalResponse = MediaFinalResponse(type = MediaResponseType.AUDIO)))
        val config = SystemToolConfig(musicRequestProfiles = mapOf("music" to profile), activeMusicRequestProfileId = "music")
        val settings = ToolSettings(system = mapOf("generate_music" to config)).validateMusicSecrets()
        val exported = Json.encodeToString(settings)
        assertFalse(exported.contains("music-private-key"))
        assertTrue(exported.contains("keystore:v1:"))
        assertEquals("music-private-key", SecretCipher.decryptStrict(profile.encryptedCredential))
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(system = mapOf("generate_music" to config.copy(musicRequestProfiles = mapOf(
                "music" to profile.copy(mode = MediaGenerationMode.DEFAULT))))).validateMusicSecrets()
        }
    }
}
