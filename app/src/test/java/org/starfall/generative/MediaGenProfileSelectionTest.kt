package org.starfall.generative

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.model.resolveMediaProfile
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.media.*

class MediaGenProfileSelectionTest {
    @Test fun anEmptyLibraryProvidesProtectedDefaultForEachMediaKind() {
        val settings = SystemToolConfig()
        assertEquals(DEFAULT_IMAGE_GEN_PROFILE_ID, settings.mediaProfile(false, "").id)
        assertEquals(DEFAULT_VIDEO_GEN_PROFILE_ID, settings.mediaProfile(true, "").id)
        assertEquals(MediaGenerationMode.DEFAULT, settings.mediaProfile(false, "").mode)
        assertEquals(1, settings.mediaProfiles(false).size)
        assertEquals(1, settings.mediaProfiles(true).size)
    }

    @Test fun customOnlyLegacyLibraryStillStartsWithBuiltInDefault() {
        val old = MediaRequestProfile("custom", "Custom")
        val settings = SystemToolConfig(imageRequestProfiles = mapOf(old.id to old))
        assertEquals(listOf(DEFAULT_IMAGE_GEN_PROFILE_ID, "custom"), settings.mediaProfiles(false).keys.toList())
        assertEquals("custom", settings.mediaProfile(false, old.id).id)
        assertEquals(DEFAULT_IMAGE_GEN_PROFILE_ID, settings.mediaProfile(false, "missing").id)
    }

    @Test fun eachModelSelectsItsOwnProfileAndMissingSelectionsFallBack() {
        val custom = MediaRequestProfile("custom", "Proxy", mode = MediaGenerationMode.CUSTOM)
        val anotherDefault = MediaRequestProfile("extra-default", "Other default", mode = MediaGenerationMode.DEFAULT)
        val config = SystemToolConfig().withMediaProfiles(false, mapOf(custom.id to custom, anotherDefault.id to anotherDefault))
        val settings = ToolSettings(system = mapOf("generate_image" to config))
        assertEquals(DEFAULT_IMAGE_GEN_PROFILE_ID, settings.resolveMediaProfile(false, ModelConfiguration()).id)
        assertEquals("custom", settings.resolveMediaProfile(false, ModelConfiguration(mediaRequestProfileId = "custom")).id)
        assertEquals("extra-default", settings.resolveMediaProfile(false, ModelConfiguration(mediaRequestProfileId = "extra-default")).id)
        assertEquals(DEFAULT_IMAGE_GEN_PROFILE_ID, settings.resolveMediaProfile(false, ModelConfiguration(mediaRequestProfileId = "removed")).id)
    }

    @Test fun profileKindsAndOptionsSurviveLegacySerializableSettings() {
        val profile = MediaRequestProfile("default-hd", "HD", mode = MediaGenerationMode.DEFAULT,
            options = buildJsonObject { put("quality", "high") }, disabledParams = setOf("size"))
        val encoded = Json.encodeToString(SystemToolConfig().withMediaProfiles(false, mapOf(profile.id to profile)))
        val restored = Json.decodeFromString<SystemToolConfig>(encoded)
        assertEquals(profile, restored.mediaProfile(false, "default-hd"))
        assertEquals(MediaGenerationMode.CUSTOM,
            Json.decodeFromString<MediaRequestProfile>("""{"id":"legacy","name":"Old"}""").mode)
    }
}
