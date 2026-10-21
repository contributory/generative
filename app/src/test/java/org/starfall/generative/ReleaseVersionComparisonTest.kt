package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.features.about.logic.compareReleaseVersions

class ReleaseVersionComparisonTest {
    @Test fun commitCountOrdersBuildsOfTheSameBaseVersion() {
        assertTrue(compareReleaseVersions("1.0.2-120", "1.0.2-130") < 0)
        assertTrue(compareReleaseVersions("1.0.2-130", "1.0.2-120") > 0)
    }

    @Test fun branchSuffixIsIgnored() {
        assertEquals(0, compareReleaseVersions("1.0.2-130dev", "1.0.2-130"))
        assertTrue(compareReleaseVersions("1.0.2-118dev", "1.0.2-120") < 0)
    }

    @Test fun higherBaseVersionWinsOverCommitCount() {
        assertTrue(compareReleaseVersions("1.0.2-900", "1.0.3-5") < 0)
    }

    @Test fun tagsWithoutCommitCountStillCompare() {
        assertTrue(compareReleaseVersions("v1.0.1", "1.0.2-10") < 0)
        assertTrue(compareReleaseVersions("1.0.2", "1.0.2-10") < 0)
    }
}
