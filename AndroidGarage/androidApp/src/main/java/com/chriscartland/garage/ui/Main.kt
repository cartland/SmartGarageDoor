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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chriscartland.garage.applogger.AppLoggerViewModel
import com.chriscartland.garage.auth.AuthViewModel
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.door.DoorViewModel
import com.chriscartland.garage.fcm.FCMRegistration
import com.chriscartland.garage.remotebutton.RemoteButtonViewModel
import com.chriscartland.garage.ui.theme.AppTheme
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
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

/**
 * Type-safe navigation routes.
 *
 * Each route is a @Serializable object — the compiler ensures route references
 * are valid and navigation arguments (if added later) are type-checked.
 */
object Route {
    @Serializable
    data object Home

    @Serializable
    data object History

    @Serializable
    data object Profile
}

/**
 * Navigation tab definition linking a route to its UI metadata.
 */
enum class Tab(
    val route: Any,
    val label: String,
    val icon: ImageVector,
) {
    Home(Route.Home, "Home", Icons.Filled.Home),
    History(Route.History, "History", Icons.Filled.DateRange),
    Profile(Route.Profile, "Settings", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    doorViewModel: DoorViewModel,
    appLoggerViewModel: AppLoggerViewModel,
) {
    val component = rememberAppComponent()
    val buttonViewModel: RemoteButtonViewModel = viewModel { component.remoteButtonViewModel }
    var isOld by remember { mutableStateOf(false) }
    val currentDoorEvent by doorViewModel.currentDoorEvent.collectAsState()
    val lastCheckInTime = currentDoorEvent.data?.lastCheckInTimeSeconds?.let {
        Instant.ofEpochSecond(it)
    }
    val doorColor = currentDoorEvent.data.color(LocalDoorStatusColorScheme.current)
    val onDoorColor = currentDoorEvent.data.onColor(LocalDoorStatusColorScheme.current)
    var isTimeWithoutFcmTooLong by remember { mutableStateOf(false) }
    var isFirstValue by remember { mutableStateOf(true) }
    DurationSince(lastCheckInTime) { duration ->
        isOld = lastCheckInTime != null && duration > OLD_DURATION_FOR_DOOR_CHECK_IN
        LaunchedEffect(isOld) {
            isTimeWithoutFcmTooLong = isOld
        }
    }
    LaunchedEffect(isTimeWithoutFcmTooLong) {
        if (isTimeWithoutFcmTooLong) {
            appLoggerViewModel.log(AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM)
        } else if (!isFirstValue) {
            // Do not log the first time if everything is ok.
            appLoggerViewModel.log(AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE)
        }
        isFirstValue = false
    }
    trace("FCMRegistration") {
        // Register for FCM notifications.
        FCMRegistration()
    }
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Garage")
                },
                actions = {
                    CheckInRow(
                        lastCheckIn = lastCheckInTime,
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
            BottomNavigationBar(navController)
        },
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Route.Home,
            Modifier
                .padding(innerPadding),
        ) {
            composable<Route.Home> {
                HomeContent(
                    authViewModel = authViewModel,
                    doorViewModel = doorViewModel,
                    appLoggerViewModel = appLoggerViewModel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                )
            }
            composable<Route.History> {
                DoorHistoryContent(
                    doorViewModel = doorViewModel,
                    appLoggerViewModel = appLoggerViewModel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                )
            }
            composable<Route.Profile> {
                ProfileContent(
                    authViewModel = authViewModel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
                selected = currentDestination?.hasRoute(tab.route::class) == true,
                onClick = {
                    navController.navigate(tab.route) {
                        // Prevent multiple copies of the same destination
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
