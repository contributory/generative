package org.starfall.generative

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.navigation.AppDestination
import org.starfall.generative.navigation.CHAT_PAGE
import org.starfall.generative.navigation.MENU_PAGE
import org.starfall.generative.navigation.homePagerSwipeEnabled
import org.starfall.generative.navigation.homePagerBackToChatEnabled

/** Page gestures are handled by the same pager used for tab navigation. */
class NavigationBackdropTest {
    @Test
    fun chatAllowsNativePagerOnlyWhenBodyGestureRegionIsNotInTheHitPath() {
        // Pager must stay enabled for true finger-following swipes on Chat AppBar.
        // The Chat-body region claims only leftward gestures; SideDrawer gets rightward.
        assertTrue(homePagerSwipeEnabled(CHAT_PAGE, AppDestination.FEATURES.route))
        assertTrue(homePagerSwipeEnabled(CHAT_PAGE, AppDestination.PROVIDERS.route))
    }

    @Test
    fun menuRootCanSwipeToChatButAnyNestedPageCannot() {
        assertTrue(homePagerSwipeEnabled(MENU_PAGE, AppDestination.FEATURES.route))
        val nested = listOf(
            AppDestination.USER_PROFILE.route,
            AppDestination.APP_SETTINGS.route,
            AppDestination.PROVIDERS.route,
            "providers/edit/id",
            AppDestination.CHAT_OPTIONS.route,
            AppDestination.MCP.route,
            AppDestination.SPEECH.route,
            AppDestination.TOOLS_EDITOR.route,
            AppDestination.REMOTE_MACHINES.route,
            "terminal/id",
            AppDestination.STORAGE.route,
            AppDestination.ICONS.route,
            AppDestination.APP_DATA.route,
            AppDestination.ABOUT.route
        )
        nested.forEach { assertFalse("Nested page must not page to Chat: $it", homePagerSwipeEnabled(MENU_PAGE, it)) }
    }

    @Test
    fun backFromMenuRootUsesSamePagerTransitionAsRightSwipe() {
        assertTrue(homePagerBackToChatEnabled(MENU_PAGE, AppDestination.FEATURES.route))
        assertFalse(homePagerBackToChatEnabled(CHAT_PAGE, AppDestination.FEATURES.route))
        assertFalse(homePagerBackToChatEnabled(MENU_PAGE, AppDestination.PROVIDERS.route))
        assertFalse(homePagerBackToChatEnabled(MENU_PAGE, "providers/edit/id"))
        assertFalse(homePagerBackToChatEnabled(MENU_PAGE, null))
    }

    @Test
    fun menuPagerIsLockedUntilItsRootIsReady() {
        assertFalse(homePagerSwipeEnabled(MENU_PAGE, null))
    }
}
