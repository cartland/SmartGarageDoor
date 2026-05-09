package com.chriscartland.garage.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TabNavigation.navigateToTab] back stack management.
 *
 * The back stack rule: Home is always at the bottom, non-Home tabs replace
 * each other on top. Stack is always [Home] or [Home, <tab>]. Max depth 2.
 */
class NavigateToTabTest {
    private fun backStack(vararg screens: Screen): MutableList<NavKey> = mutableListOf<NavKey>(*screens)

    @Test
    fun initialStateIsHome() {
        val stack = backStack(Screen.Home)
        assertEquals(listOf(Screen.Home), stack.toList())
    }

    @Test
    fun tapHistoryFromHome() {
        val stack = backStack(Screen.Home)
        TabNavigation.navigateToTab(stack, Screen.History)
        assertEquals(listOf(Screen.Home, Screen.History), stack.toList())
    }

    @Test
    fun tapSettingsFromHome() {
        val stack = backStack(Screen.Home)
        TabNavigation.navigateToTab(stack, Screen.Profile)
        assertEquals(listOf(Screen.Home, Screen.Profile), stack.toList())
    }

    @Test
    fun tapSettingsReplacesHistory() {
        val stack = backStack(Screen.Home, Screen.History)
        TabNavigation.navigateToTab(stack, Screen.Profile)
        assertEquals(listOf(Screen.Home, Screen.Profile), stack.toList())
    }

    @Test
    fun tapHistoryReplacesSettings() {
        val stack = backStack(Screen.Home, Screen.Profile)
        TabNavigation.navigateToTab(stack, Screen.History)
        assertEquals(listOf(Screen.Home, Screen.History), stack.toList())
    }

    @Test
    fun tapHomeFromHistoryPopsToHome() {
        val stack = backStack(Screen.Home, Screen.History)
        TabNavigation.navigateToTab(stack, Screen.Home)
        assertEquals(listOf(Screen.Home), stack.toList())
    }

    @Test
    fun tapHomeFromSettingsPopsToHome() {
        val stack = backStack(Screen.Home, Screen.Profile)
        TabNavigation.navigateToTab(stack, Screen.Home)
        assertEquals(listOf(Screen.Home), stack.toList())
    }

    @Test
    fun tapHomeWhenAlreadyOnHomeIsNoOp() {
        val stack = backStack(Screen.Home)
        TabNavigation.navigateToTab(stack, Screen.Home)
        assertEquals(listOf(Screen.Home), stack.toList())
    }

    @Test
    fun tapHistoryWhenAlreadyOnHistoryIsNoOp() {
        val stack = backStack(Screen.Home, Screen.History)
        TabNavigation.navigateToTab(stack, Screen.History)
        assertEquals(listOf(Screen.Home, Screen.History), stack.toList())
    }

    @Test
    fun tapSettingsWhenAlreadyOnSettingsIsNoOp() {
        val stack = backStack(Screen.Home, Screen.Profile)
        TabNavigation.navigateToTab(stack, Screen.Profile)
        assertEquals(listOf(Screen.Home, Screen.Profile), stack.toList())
    }

    @Test
    fun stackNeverExceedsDepthTwo() {
        val stack = backStack(Screen.Home)
        TabNavigation.navigateToTab(stack, Screen.History)
        TabNavigation.navigateToTab(stack, Screen.Profile)
        TabNavigation.navigateToTab(stack, Screen.History)
        TabNavigation.navigateToTab(stack, Screen.Profile)
        assert(stack.size <= 2) { "Stack depth ${stack.size} exceeds max 2" }
    }

    @Test
    fun homeAlwaysAtBottom() {
        val stack = backStack(Screen.Home)
        TabNavigation.navigateToTab(stack, Screen.History)
        assertEquals(Screen.Home, stack.first())
        TabNavigation.navigateToTab(stack, Screen.Profile)
        assertEquals(Screen.Home, stack.first())
        TabNavigation.navigateToTab(stack, Screen.Home)
        assertEquals(Screen.Home, stack.first())
    }

    @Test
    fun backFromHistoryRevealsHome() {
        val stack = backStack(Screen.Home, Screen.History)
        stack.removeAt(stack.lastIndex) // simulate system back
        assertEquals(listOf(Screen.Home), stack.toList())
    }

    @Test
    fun backFromSettingsRevealsHome() {
        val stack = backStack(Screen.Home, Screen.Profile)
        stack.removeAt(stack.lastIndex) // simulate system back
        assertEquals(listOf(Screen.Home), stack.toList())
    }

    @Test
    fun fullNavigationSequence() {
        val stack = backStack(Screen.Home)

        // Home → History
        TabNavigation.navigateToTab(stack, Screen.History)
        assertEquals(Screen.History, stack.last())

        // History → Settings (replaces, doesn't stack)
        TabNavigation.navigateToTab(stack, Screen.Profile)
        assertEquals(Screen.Profile, stack.last())
        assertEquals(2, stack.size)

        // Settings → Home (pop)
        TabNavigation.navigateToTab(stack, Screen.Home)
        assertEquals(Screen.Home, stack.last())
        assertEquals(1, stack.size)

        // Home → Settings → Back → Home
        TabNavigation.navigateToTab(stack, Screen.Profile)
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf(Screen.Home), stack.toList())
    }
}
