package org.starfall.multigateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.multigateway.data.model.DEFAULT_REASONING_EFFORTS
import org.starfall.multigateway.data.model.ModelConfiguration
import org.starfall.multigateway.data.model.REASONING_EFFORT_ACTIVE
import org.starfall.multigateway.data.model.SELECTABLE_REASONING_EFFORTS
import org.starfall.multigateway.data.model.supportedReasoningEfforts
import org.starfall.multigateway.ui.chat.reasoningEffortBurstDuration
import org.starfall.multigateway.ui.chat.reasoningEffortEnabled
import org.starfall.multigateway.ui.chat.reasoningEffortIndex
import org.starfall.multigateway.ui.chat.reasoningEffortOptions
import org.starfall.multigateway.ui.chat.reasoningEffortTier

class ReasoningEffortControlTest {
    private fun efforts(options: List<org.starfall.multigateway.ui.chat.ReasoningEffortOption>) = options.map { it.effort }
    private fun tiers(options: List<org.starfall.multigateway.ui.chat.ReasoningEffortOption>) =
        options.indices.map { reasoningEffortTier(it, options) }

    @Test fun disabledValuesAreRecognized() {
        assertFalse(reasoningEffortEnabled("none"))
        assertFalse(reasoningEffortEnabled(" OFF "))
        assertTrue(reasoningEffortEnabled(null))
        assertTrue(reasoningEffortEnabled("medium"))
    }

    @Test fun everyModelStartsWithLowMediumAndHigh() {
        assertEquals(listOf("low", "medium", "high"), DEFAULT_REASONING_EFFORTS)
        assertEquals(DEFAULT_REASONING_EFFORTS, ModelConfiguration().supportedReasoningEfforts)
        assertEquals(
            listOf("none", null, "low", "medium", "high"),
            efforts(reasoningEffortOptions(DEFAULT_REASONING_EFFORTS))
        )
    }

    @Test fun sliderOnlyShowsTheLevelsTheModelMarks() {
        val marked = listOf("minimal", "medium", "max")
        val options = reasoningEffortOptions(marked)
        assertEquals(listOf("none", null, "minimal", "medium", "max"), efforts(options))
        assertEquals(listOf("None", "Default", "Minimal", "Medium", "Max"), options.map { it.label })
        assertEquals(listOf("minimal", "low", "medium", "high", "xhigh", "max"),
            ModelConfiguration(reasoningEfforts = SELECTABLE_REASONING_EFFORTS.reversed()).supportedReasoningEfforts)
    }

    @Test fun labelsAreNeverRenamedPerProvider() {
        val all = SELECTABLE_REASONING_EFFORTS
        val levels = reasoningEffortOptions(all).filter { it.effort in all }
        assertEquals(listOf("Minimal", "Low", "Medium", "High", "X-High", "Max"), levels.map { it.label })
    }

    @Test fun unsupportedSavedValuesShowAsDefault() {
        val options = reasoningEffortOptions(DEFAULT_REASONING_EFFORTS)
        assertEquals(1, reasoningEffortIndex(null, options))
        assertEquals(0, reasoningEffortIndex("none", options))
        assertEquals(0, reasoningEffortIndex("off", options))
        assertEquals(2, reasoningEffortIndex("LOW", options))
        assertEquals(4, reasoningEffortIndex(" HIGH ", options))
        for (stale in listOf("xhigh", "max", "minimal", "unknown", REASONING_EFFORT_ACTIVE)) {
            assertEquals(1, reasoningEffortIndex(stale, options))
        }
    }

    @Test fun colourFollowsTheTwoHighestLevelsTheModelSupports() {
        // None, Default, then three levels: the lowest keeps its normal colour.
        assertEquals(listOf(0, 1, 2, 5, 6), tiers(reasoningEffortOptions(DEFAULT_REASONING_EFFORTS)))
        // Two levels: purple then red.
        assertEquals(listOf(0, 1, 5, 6), tiers(reasoningEffortOptions(supportedEfforts = listOf("high", "max"))))
        // Six levels: only the top two are tinted.
        assertEquals(listOf(0, 1, 2, 3, 4, 4, 5, 6),
            tiers(reasoningEffortOptions(supportedEfforts = SELECTABLE_REASONING_EFFORTS)))
        // Moving the marks moves the colours with them.
        val low = reasoningEffortOptions(supportedEfforts = listOf("minimal", "low"))
        assertEquals(listOf(0, 1, 5, 6), tiers(low))
    }

    @Test fun aSingleLevelOnlyKeepsPurpleAndDefaultStaysUncoloured() {
        val options = reasoningEffortOptions(supportedEfforts = listOf("medium"))
        assertEquals(listOf(null, "medium"), efforts(options).filter { it != "none" })
        assertEquals(listOf(0, 1, 5), tiers(options))
        assertTrue(tiers(options).none { it == 6 })
    }

    @Test fun aThinkingModelWithoutLevelsGetsAnActiveStepLeftOfDefault() {
        val options = reasoningEffortOptions(supportedEfforts = emptyList())
        assertEquals(listOf("none", REASONING_EFFORT_ACTIVE, null), efforts(options))
        assertEquals(listOf("None", "Active", "Default"), options.map { it.label })
        assertEquals(listOf(0, 5, 1), tiers(options))
        assertEquals(1, reasoningEffortIndex(REASONING_EFFORT_ACTIVE, options))
        assertEquals(2, reasoningEffortIndex(null, options))
    }

    @Test fun activeIsOnlyOfferedWhenThereAreNoLevels() {
        assertFalse(efforts(reasoningEffortOptions(DEFAULT_REASONING_EFFORTS)).contains(REASONING_EFFORT_ACTIVE))
        assertFalse(efforts(reasoningEffortOptions(supportedEfforts = listOf("low"))).contains(REASONING_EFFORT_ACTIVE))
    }

    @Test fun maximumEffectsLastLongerThanThePurpleOne() {
        assertTrue(reasoningEffortBurstDuration(6) > reasoningEffortBurstDuration(5))
    }
}
