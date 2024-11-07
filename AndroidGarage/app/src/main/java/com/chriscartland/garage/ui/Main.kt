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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chriscartland.garage.applogger.AppLoggerViewModelImpl
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.fcm.FCMRegistration
import com.chriscartland.garage.remotebutton.RemoteButtonViewModelImpl
import com.chriscartland.garage.ui.theme.AppTheme

@Composable
fun GarageApp() {
    AppTheme {
        AppNavigation()
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object History : Screen("history", "History", Icons.Filled.DateRange)
    data object Profile : Screen("profile", "User", Icons.Filled.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    doorViewModel: DoorViewModelImpl = hiltViewModel(),
    authViewModel: AuthViewModelImpl = hiltViewModel(),
    buttonViewModel: RemoteButtonViewModelImpl = hiltViewModel(),
    appLoggerViewModel: AppLoggerViewModelImpl = hiltViewModel(),
) {
    var isTimeWithoutFcmTooLong by remember { mutableStateOf(false) }
    var isFirstValue by remember { mutableStateOf(true) }
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
        FCMRegistration(viewModel = doorViewModel)
    }
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garage") },
            )
        },
        bottomBar = {
            BottomNavigationBar(navController, currentRoute = currentRoute(navController))
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            Modifier
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeContent(
                    viewModel = doorViewModel,
                    authViewModel = authViewModel,
                    buttonViewModel = buttonViewModel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    onOldCheckInChanged = { isOld ->
                        isTimeWithoutFcmTooLong = isOld
                    },
                )
            }
            composable(Screen.History.route) {
                DoorHistoryContent(
                    viewModel = doorViewModel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    onOldCheckInChanged = { isOld ->
                        isTimeWithoutFcmTooLong = isOld
                    },
                )
            }
            composable(Screen.Profile.route) {
                ProfileContent(
                    viewModel = authViewModel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun currentRoute(navController: NavController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}

@Composable
fun BottomNavigationBar(navController: NavController, currentRoute: String?) {
    val items = listOf(Screen.Home, Screen.History, Screen.Profile)

    NavigationBar {
        items.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.label,
                    )
                },
                label = {
                    Text(
                        text = screen.label,
                    )
                },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        // Prevent multiple copies of the same destination
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
