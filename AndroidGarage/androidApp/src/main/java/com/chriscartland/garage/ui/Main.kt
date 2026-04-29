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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.chriscartland.garage.ui.settings.DiagnosticsScreen
import com.chriscartland.garage.ui.theme.AppTheme
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.usecase.AppLoggerViewModel
import com.chriscartland.garage.usecase.AuthViewModel
import com.chriscartland.garage.usecase.DoorViewModel
import kotlinx.serialization.Serializable
import java.time.Instant

@Composable
fun GarageApp(
    authViewModel: AuthViewModel,
    doorViewModel: DoorViewModel,
    appLoggerViewModel: AppLoggerViewModel,
) {
    AppTheme {
        AppNavigation(
            authViewModel = authViewModel,
            doorViewModel = doorViewModel,
            appLoggerViewModel = appLoggerViewModel,
        )
    }
}

private const val NAV_ANIM_DURATION = 300

/** Reusable tween with FastOutSlowIn easing for all nav animations. */
private inline fun <reified T> navTween() = tween<T>(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)

/**
 * Type-safe navigation routes.
 *
 * Each route is a @Serializable object — the compiler ensures route references
 * are valid and navigation arguments (if added later) are type-checked.
 * Used with Navigation 3's NavDisplay + entryProvider.
 */
sealed interface Screen {
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
fun AppNavigation(
    authViewModel: AuthViewModel,
    doorViewModel: DoorViewModel,
    appLoggerViewModel: AppLoggerViewModel,
) {
    val currentDoorEvent by doorViewModel.currentDoorEvent.collectAsState()
    val lastCheckInTime = currentDoorEvent.data?.lastCheckInTimeSeconds?.let {
        Instant.ofEpochSecond(it)
    }
    val isCheckInStale by doorViewModel.isCheckInStale.collectAsState()
    val doorColor = currentDoorEvent.data.color(LocalDoorStatusColorScheme.current, isStale = isCheckInStale)
    val onDoorColor = currentDoorEvent.data.onColor(LocalDoorStatusColorScheme.current, isStale = isCheckInStale)
    // Nav3: back stack is a simple mutable list of Screen objects.
    // Using remember (not rememberSaveable) because Screen objects aren't Bundle-saveable.
    // For tab navigation this is fine — process death just restarts on Home tab.
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }

    Scaffold(
        topBar = {
            val currentScreen = backStack.lastOrNull()
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
                actions = {
                    CheckInRow(
                        lastCheckIn = lastCheckInTime,
                        isCheckInStale = isCheckInStale,
                        pillColors = PillColors(
                            // Match the door color
                            backgroundColor = doorColor,
                            contentColor = onDoorColor,
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                },
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentScreen = backStack.lastOrNull(),
                onTabSelected = { screen -> TabNavigation.navigateToTab(backStack, screen) },
            )
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            transitionSpec = { fadeIn(navTween()) togetherWith fadeOut(navTween()) },
            popTransitionSpec = { fadeIn(navTween()) togetherWith fadeOut(navTween()) },
            predictivePopTransitionSpec = { fadeIn(navTween()) togetherWith fadeOut(navTween()) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<Screen>(),
                rememberViewModelStoreNavEntryDecorator<Screen>(),
            ),
            modifier = Modifier.padding(innerPadding),
            // Screen-padding convention: every nav entry gets
            // `padding(horizontal = 16.dp)` applied here. This is the
            // SINGLE source of horizontal screen padding — child
            // Composables must NOT re-apply it. PR #589 doubled the
            // Settings card to 32dp by adding a second 16dp wrapper
            // inside the screen's content; #593 was the fix.
            entryProvider = entryProvider {
                entry<Screen.Home> {
                    HomeContent(
                        authViewModel = authViewModel,
                        doorViewModel = doorViewModel,
                        appLoggerViewModel = appLoggerViewModel,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    )
                }
                entry<Screen.History> {
                    DoorHistoryContent(
                        doorViewModel = doorViewModel,
                        appLoggerViewModel = appLoggerViewModel,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    )
                }
                entry<Screen.Profile> {
                    ProfileContent(
                        authViewModel = authViewModel,
                        onNavigateToFunctionList = { backStack.add(Screen.FunctionList) },
                        onNavigateToDiagnostics = { backStack.add(Screen.Diagnostics) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    )
                }
                entry<Screen.FunctionList> {
                    FunctionListContent(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    )
                }
                entry<Screen.Diagnostics> {
                    DiagnosticsScreen(
                        onBack = {
                            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: Screen?,
    onTabSelected: (Screen) -> Unit,
) {
    NavigationBar {
        Tab.entries.forEach { tab ->
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
                selected = currentScreen == tab.screen,
                onClick = { onTabSelected(tab.screen) },
            )
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
        backStack: MutableList<Screen>,
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
