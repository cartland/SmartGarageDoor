/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.chriscartland.garage.ui.settings.DiagnosticsScreen
import com.chriscartland.garage.ui.theme.AppTheme
import com.chriscartland.garage.ui.theme.Spacing
import kotlinx.serialization.Serializable

@Composable
fun GarageApp() {
    // ProvideAppWindowSizeClass installs `LocalAppWindowSizeClass` for the
    // whole app. Adaptive layout decisions (current screen-width cap; future
    // single-pane vs. two-pane branching) read from that local — never from
    // `LocalConfiguration.current.screenWidthDp` directly. See
    // `AppWindowSizeClass.kt` for the rationale + the lint that enforces it
    // (`checkNoLocalConfigurationDimensionReads`).
    ProvideAppWindowSizeClass {
        AppTheme {
            AppNavigation()
        }
    }
}

private const val NAV_ANIM_DURATION = 300

/** Reusable tween with FastOutSlowIn easing for all nav animations. */
private inline fun <reified T> navTween() = tween<T>(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)

/**
 * Type-safe navigation routes.
 *
 * Each route is a `@Serializable NavKey` object so it can ride through the
 * `rememberNavBackStack()` save/restore cycle (process death, rotation,
 * window-size class change, etc.). Both annotations are load-bearing:
 * `@Serializable` for kotlinx-serialization, `NavKey` for Nav3 to identify it
 * as a destination key.
 */
sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Profile : Screen

    @Serializable
    data object FunctionList : Screen

    @Serializable
    data object Diagnostics : Screen
}

/**
 * Navigation tab definition linking a screen to its UI metadata.
 */
enum class Tab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
) {
    Home(Screen.Home, "Home", Icons.Filled.Home),
    History(Screen.History, "History", Icons.Filled.DateRange),
    Profile(Screen.Profile, "Settings", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    // Nav3: `rememberNavBackStack` is the saveable back stack. Survives
    // configuration changes (rotation, window size, dark mode, locale,
    // font scale) AND process death. The serialized form rides through
    // `SavedStateHolder` via `NavKeySerializer`, which requires every
    // `Screen` subtype to be both `@Serializable` and a `NavKey` (see
    // `Screen` above).
    //
    // Pre-PR-A used `remember { mutableStateListOf(Screen.Home) }`, which
    // reset to `[Home]` on every Activity recreate. The cost was deliberate:
    // tab nav, no deep links, "starts on Home after rotation" was acceptable.
    // Now that the multi-pane future requires a saveable back stack (panes
    // need to query historical entries, and a Window-size-class transition
    // triggers Activity recreation we can't lose state through), saveable
    // is the new floor.
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(Screen.Home)

    Scaffold(
        topBar = {
            // Back stack is `List<NavKey>`; narrow to our app's `Screen` type
            // for the title + back-icon decision.
            val currentScreen = backStack.lastOrNull() as? Screen
            val isSubScreen = currentScreen is Screen.FunctionList ||
                currentScreen is Screen.Diagnostics
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            is Screen.FunctionList -> "Function list"
                            is Screen.Diagnostics -> "Diagnostics"
                            else -> "Garage"
                        },
                    )
                },
                navigationIcon = {
                    if (isSubScreen) {
                        IconButton(onClick = {
                            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Hide the bottom-nav chrome entirely on layouts with no
            // visible tabs (Expanded). Empty NavigationBar still claims
            // ~80dp of vertical space, which we want to recover for the
            // 3-pane dashboard.
            if (currentAppLayoutMode().visibleTabs.isNotEmpty()) {
                BottomNavigationBar(
                    currentScreen = backStack.lastOrNull() as? Screen,
                    onTabSelected = { screen -> TabNavigation.navigateToTab(backStack, screen) },
                )
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            transitionSpec = { fadeIn(navTween()) togetherWith fadeOut(navTween()) },
            popTransitionSpec = { fadeIn(navTween()) togetherWith fadeOut(navTween()) },
            predictivePopTransitionSpec = { fadeIn(navTween()) togetherWith fadeOut(navTween()) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ),
            modifier = Modifier.padding(innerPadding),
            // Screen-layout convention (single source of truth):
            //   Each nav entry is wrapped in `RouteContent { ... }` which
            //   applies (a) `Modifier.widthIn(max = ContentWidth.Standard)`
            //   to cap content width on tablets/landscape, and (b) horizontal
            //   centering via Box(TopCenter). Each entry then attaches the
            //   provided modifier and adds `Modifier.padding(horizontal = Spacing.Screen)`
            //   for the 16dp gutter.
            //
            //   Child Composables MUST NOT re-apply the width cap, the
            //   centering, or the screen padding. PR #589 doubled the
            //   Settings card to 32dp by adding a second 16dp wrapper
            //   inside the screen's content; #593 was the fix.
            //
            //   When the future two-pane experiment lands behind a runtime
            //   toggle, the toggle branches at this layer: single-pane uses
            //   `RouteContent`, two-pane uses a different wrapper. Screens
            //   stay layout-agnostic.
            entryProvider = entryProvider {
                // All adaptive decisions (size-class branching, merged
                // routes, per-screen wrapper choice) live in the single
                // `RouteEntryFor` dispatch table below — these `entry<>`
                // blocks are uniform call sites.
                val mode = currentAppLayoutMode()
                val onPopBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
                val routeFor: @Composable (Screen) -> Unit = { screen ->
                    RouteEntryFor(
                        screen = screen,
                        mode = mode,
                        onNavigateToFunctionList = { backStack.add(Screen.FunctionList) },
                        onNavigateToDiagnostics = { backStack.add(Screen.Diagnostics) },
                        onBack = { onPopBack() },
                    )
                }
                entry<Screen.Home> { routeFor(Screen.Home) }
                entry<Screen.History> { routeFor(Screen.History) }
                entry<Screen.Profile> { routeFor(Screen.Profile) }
                entry<Screen.FunctionList> { routeFor(Screen.FunctionList) }
                entry<Screen.Diagnostics> { routeFor(Screen.Diagnostics) }
            },
        )
    }
}

/**
 * Bottom navigation. All adaptive decisions (which tabs are visible,
 * which tab is highlighted when a "merged" route is on the back stack)
 * are read from [AppLayoutMode] — this Composable does not branch on
 * size class directly.
 */
@Composable
fun BottomNavigationBar(
    currentScreen: Screen?,
    onTabSelected: (Screen) -> Unit,
    mode: AppLayoutMode = currentAppLayoutMode(),
) {
    val effectiveScreen = mode.canonicalScreen(currentScreen)
    NavigationBar {
        mode.visibleTabs.forEach { tab ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                    )
                },
                selected = effectiveScreen == tab.screen,
                onClick = { onTabSelected(tab.screen) },
            )
        }
    }
}

/**
 * Single dispatch table from `(Screen, AppLayoutMode)` → rendered route.
 *
 * **The only place in the app that maps a screen to its concrete route
 * wrapper + body.** Both the bottom nav and the entry provider read
 * from [AppLayoutMode] for their respective decisions (visibility,
 * highlight, render); this function is where the render side lives.
 *
 * Adding a screen → add one `entry<Screen.X>` in `entryProvider` plus
 * one `Screen.X ->` arm here. Adding a layout mode → the sealed-type
 * `when` on `mode is AppLayoutMode.Wide` becomes exhaustive over the
 * new mode and the compiler forces an update at every consumer.
 *
 * Merged routes (e.g. `Screen.History` → `Screen.Home` on Wide) are
 * resolved at the top via [AppLayoutMode.canonicalScreen] so a single
 * arm renders both back-stack entries.
 */
@Composable
private fun RouteEntryFor(
    screen: Screen,
    mode: AppLayoutMode,
    onNavigateToFunctionList: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onBack: () -> Unit,
) {
    val canonicalScreen = mode.canonicalScreen(screen) ?: screen
    when (canonicalScreen) {
        Screen.Home -> {
            when (mode) {
                AppLayoutMode.Expanded -> {
                    // 3-pane dashboard. The Settings slot's body depends on
                    // the original (non-canonicalized) `screen`: Profile/Home
                    // → ProfileContent, FunctionList → FunctionListContent,
                    // Diagnostics → DiagnosticsScreen. Each back-stack entry
                    // (Home, FunctionList, Diagnostics, Profile) renders its
                    // own copy of the 3-pane; NavDisplay shows only the top
                    // entry, so the cross-fade between entries visually
                    // reads as the Settings pane swapping while Home and
                    // History stay still.
                    ThreePaneRouteContent { routeModifier ->
                        ThreePaneDashboardContent(
                            modifier = routeModifier.padding(horizontal = Spacing.Screen),
                            homePane = { paneModifier ->
                                HomeContent(modifier = paneModifier)
                            },
                            historyPane = { paneModifier ->
                                DoorHistoryContent(modifier = paneModifier)
                            },
                            settingsPane = { paneModifier ->
                                when (screen) {
                                    Screen.FunctionList -> FunctionListContent(modifier = paneModifier)
                                    Screen.Diagnostics ->
                                        // DiagnosticsScreen owns its own
                                        // horizontal padding (LazyColumn
                                        // contentPadding) — pass paneModifier
                                        // unmodified.
                                        DiagnosticsScreen(onBack = onBack, modifier = paneModifier)
                                    else -> ProfileContent(
                                        onNavigateToFunctionList = onNavigateToFunctionList,
                                        onNavigateToDiagnostics = onNavigateToDiagnostics,
                                        modifier = paneModifier,
                                    )
                                }
                            },
                        )
                    }
                }
                AppLayoutMode.Wide -> {
                    // Pull-to-refresh is per-pane: pulling Home refreshes door
                    // status only; pulling History refreshes recent events only.
                    // Each pane behaves like its own screen for refresh,
                    // matching what the user experiences on phone.
                    DashboardRouteContent { routeModifier ->
                        HomeDashboardContent(
                            modifier = routeModifier.padding(horizontal = Spacing.Screen),
                            homePane = { paneModifier ->
                                HomeContent(modifier = paneModifier)
                            },
                            historyPane = { paneModifier ->
                                DoorHistoryContent(modifier = paneModifier)
                            },
                        )
                    }
                }
                AppLayoutMode.Compact -> {
                    RouteContent { routeModifier ->
                        HomeContent(
                            modifier = routeModifier.padding(horizontal = Spacing.Screen),
                        )
                    }
                }
            }
        }
        Screen.History -> {
            // Reached only on Compact (Wide + Expanded merge History to Home).
            RouteContent { routeModifier ->
                DoorHistoryContent(
                    modifier = routeModifier.padding(horizontal = Spacing.Screen),
                )
            }
        }
        Screen.Profile -> {
            // Reached on Compact and Wide (Expanded merges Profile to Home).
            RouteContent { routeModifier ->
                ProfileContent(
                    onNavigateToFunctionList = onNavigateToFunctionList,
                    onNavigateToDiagnostics = onNavigateToDiagnostics,
                    modifier = routeModifier.padding(horizontal = Spacing.Screen),
                )
            }
        }
        Screen.FunctionList -> {
            // Reached on Compact and Wide (Expanded merges FunctionList to Home).
            RouteContent { routeModifier ->
                FunctionListContent(
                    modifier = routeModifier.padding(horizontal = Spacing.Screen),
                )
            }
        }
        Screen.Diagnostics -> {
            // Reached on Compact and Wide (Expanded merges Diagnostics to Home).
            RouteContent { routeModifier ->
                // Diagnostics provides its own horizontal padding inside
                // its LazyColumn (Spacing.Screen) — no padding here.
                DiagnosticsScreen(
                    onBack = onBack,
                    modifier = routeModifier,
                )
            }
        }
    }
}

/**
 * Navigate to a tab screen.
 *
 * Home is always at the bottom of the stack. Non-Home tabs replace each other
 * on top of Home. The stack is always `[Home]` or `[Home, <tab>]` (max depth 2).
 *
 * - Tap Home → pop to Home
 * - Tap current tab → no-op
 * - Tap different tab → replace non-Home tab on top
 * - Back from non-Home → reveals Home
 * - Back from Home → exits app
 */
object TabNavigation {
    fun navigateToTab(
        backStack: MutableList<NavKey>,
        screen: Screen,
    ) {
        when {
            screen is Screen.Home -> {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }
            backStack.lastOrNull() == screen -> {
                // Already on this tab, no-op.
            }
            else -> {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                backStack.add(screen)
            }
        }
    }
}
