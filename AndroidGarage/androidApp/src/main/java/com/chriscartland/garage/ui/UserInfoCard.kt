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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.auth.DisplayName
import com.chriscartland.garage.auth.Email
import com.chriscartland.garage.auth.FirebaseIdToken
import com.chriscartland.garage.auth.User
import com.chriscartland.garage.settings.AppSettingsViewModel
import com.chriscartland.garage.settings.AppSettingsViewModelImpl

@Composable
fun UserInfoCard(
    user: User?,
    modifier: Modifier = Modifier,
    signIn: () -> Unit = {},
    signOut: () -> Unit = {},
    settingsViewModel: AppSettingsViewModel = hiltViewModel<AppSettingsViewModelImpl>(),
    colors: CardColors = CardDefaults.cardColors(),
) {
    val startUserCardExpanded by settingsViewModel.profileUserCardExpanded.collectAsState()
    UserInfoCard(
        user = user,
        modifier = modifier,
        signIn = signIn,
        signOut = signOut,
        startExpanded = startUserCardExpanded,
        onExpandedChange = {
            settingsViewModel.setProfileUserCardExpanded(it)
        },
        colors = colors,
    )
}

@Composable
fun UserInfoCard(
    user: User?,
    modifier: Modifier = Modifier,
    signIn: () -> Unit = {},
    signOut: () -> Unit = {},
    startExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit = {},
    colors: CardColors = CardDefaults.cardColors(),
) {
    ExpandableColumnCard(
        title = "User",
        modifier = modifier,
        startExpanded = startExpanded,
        onExpandedChange = onExpandedChange,
        colors = colors,
    ) {
        Text(text = "Name: ${user?.name?.asString() ?: "Unknown"}")
        Text(text = "Email: ${user?.email?.asString() ?: "Unknown"}")
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = if (user == null) {
                    signIn
                } else {
                    signOut
                },
            ) {
                Text(
                    if (user == null) {
                        "Sign In"
                    } else {
                        "Sign out"
                    },
                )
            }
        }
    }
}

@Preview
@Composable
fun UserInfoNoUserPreview() {
    UserInfoCard(user = null)
}

@Preview
@Composable
fun UserInfoCardPreview() {
    UserInfoCard(
        user = User(
            name = DisplayName("Demo User"),
            email = Email("demo@example.com"),
            idToken = FirebaseIdToken("abc123", 1234567890),
        ),
    )
}
