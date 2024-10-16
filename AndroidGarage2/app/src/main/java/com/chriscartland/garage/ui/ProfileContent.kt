package com.chriscartland.garage.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.model.User

@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModelImpl = hiltViewModel(),
) {
    val context = LocalContext.current as ComponentActivity
    val authState by viewModel.authState.collectAsState()
    ProfileContent(
        user = when (val it = authState) {
            is AuthState.Authenticated -> it.user
            AuthState.Unauthenticated -> null
        },
        modifier = modifier,
        signIn = { viewModel.signInWithGoogle(context) },
        signOut = { viewModel.signOut() }
    )
}

@Composable
fun ProfileContent(
    user: User?,
    modifier: Modifier = Modifier,
    signIn: () -> Unit,
    signOut: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (user == null) {
            item {
                Button(onClick = signIn) {
                    Text("Sign In")
                }
            }
        } else {
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(text = "Name: ${user?.name?.asString() ?: "Unknown"}")
                        Text(text = "Email: ${user?.email?.asString() ?: "Unknown"}")
                    }
                }
            }
            item {
                Button(onClick = signOut) {
                    Text("Sign out")
                }
            }
        }
        // Snooze notifications.
        if (APP_CONFIG.snoozeNotificationsOption) {
            item {
                SnoozeNotificationCard(
                    text = "Snooze notifications",
                    snoozeText = "Snooze",
                    saveText = "Save",
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileContentPreview() {
    ProfileContent()
}
