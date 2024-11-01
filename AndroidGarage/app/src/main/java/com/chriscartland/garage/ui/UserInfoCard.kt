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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.auth.DisplayName
import com.chriscartland.garage.auth.Email
import com.chriscartland.garage.auth.FirebaseIdToken
import com.chriscartland.garage.auth.User

@Composable
fun UserInfoCard(
    user: User?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("User Information", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Name: ${user?.name?.asString() ?: "Unknown"}")
            Text(text = "Email: ${user?.email?.asString() ?: "Unknown"}")
        }
        content()
        Spacer(modifier = Modifier.height(16.dp))
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
    UserInfoCard(user = User(
        name = DisplayName("Demo User"),
        email = Email("demo@example.com"),
        idToken = FirebaseIdToken("abc123", 1234567890),
    ))
}
