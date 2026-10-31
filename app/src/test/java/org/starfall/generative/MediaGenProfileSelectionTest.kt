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
    @Test fun independentCustomSelectionClearsModelAndNeverOverridesModelMetadata() {
        for (video in listOf(false, true)) {
            val custom = MediaRequestProfile("standalone", "Standalone", requiresModel = false)
            val required = MediaRequestProfile("bound", "Bound")
            val configured = SystemToolConfig(providerId = "provider", modelId = "media-model")
                .withMediaProfiles(video, mapOf(custom.id to custom, required.id to required))
            val standalone = configured.selectMediaProfile(video, custom)
            assertEquals("", standalone.providerId)
            assertEquals("", standalone.modelId)
            assertEquals(custom.id, standalone.selectedMediaProfile(video, null).id)
            assertEquals(MediaGenerationMode.CUSTOM, standalone.selectedMediaProfile(video, null).mode)
            val settings = ToolSettings(system = mapOf(
                (if (video) "generate_video" else "generate_image") to standalone
            ))
            assertEquals(custom.id, settings.resolveMediaProfile(video, null).id)
            assertEquals(DEFAULT_IMAGE_GEN_PROFILE_ID.takeUnless { video } ?: DEFAULT_VIDEO_GEN_PROFILE_ID,
                settings.resolveMediaProfile(video, ModelConfiguration(mediaRequestProfileId = custom.id)).id)
            assertEquals(required.id,
                settings.resolveMediaProfile(video, ModelConfiguration(mediaRequestProfileId = required.id)).id)
            val roundTrip = Json.decodeFromString<SystemToolConfig>(Json.encodeToString(standalone))
            assertEquals(custom, roundTrip.mediaProfile(video, custom.id))
            assertEquals("", roundTrip.modelId)
            val pickedModel = standalone.selectMediaModel(video, "another", "another-model")
            assertEquals("another", pickedModel.providerId)
            assertEquals("another-model", pickedModel.modelId)
            assertEquals(MediaGenerationMode.DEFAULT, pickedModel.selectedMediaProfile(video, null).mode)
            assertEquals(if (video) DEFAULT_VIDEO_GEN_PROFILE_ID else DEFAULT_IMAGE_GEN_PROFILE_ID,
                if (video) pickedModel.activeVideoRequestProfileId else pickedModel.activeImageRequestProfileId)
        }
    }

    @Test fun modelBoundSelectionRequiresModelAndLegacyProfilesRemainBound() {
        val required = MediaRequestProfile("required", "Required")
        val independent = required.copy(id = "standalone", requiresModel = false)
        val empty = SystemToolConfig().withMediaProfiles(false, mapOf(required.id to required, independent.id to independent))
        assertTrue(required.requiresModel)
        assertFalse(independent.requiresModel)
        assertThrows(IllegalArgumentException::class.java) { empty.selectMediaProfile(false, required) }
        val configured = empty.selectMediaModel(false, "p", "image-model")
        assertEquals(required.id, configured.selectMediaProfile(false, required).activeImageRequestProfileId)
        val standalone = configured.selectMediaProfile(false, independent)
        assertEquals(DEFAULT_IMAGE_GEN_PROFILE_ID,
            standalone.withMediaProfiles(false, standalone.mediaProfiles(false) +
                (independent.id to independent.copy(requiresModel = true))).activeImageRequestProfileId)
        assertTrue(Json.decodeFromString<MediaRequestProfile>("""{"id":"old","name":"Old"}""").requiresModel)
    }

}
