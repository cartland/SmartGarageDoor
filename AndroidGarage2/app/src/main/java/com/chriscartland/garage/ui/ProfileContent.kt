package com.chriscartland.garage.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.model.User
import com.chriscartland.garage.viewmodel.DoorViewModel

@Composable
fun ProfileContent(
    viewModel: DoorViewModel = hiltViewModel()
) {
    val context = LocalContext.current as ComponentActivity
    val user by viewModel.user.collectAsState()
    ProfileContent(
        user = user,
        signInSeamlessly = { viewModel.signInSeamlessly(context) },
        signInWithDialog = { viewModel.signInWithDialog(context) },
        signOut = { viewModel.signOut() }
    )
}

@Composable
fun ProfileContent(
    user: User?,
    signInSeamlessly: () -> Unit,
    signInWithDialog: () -> Unit,
    signOut: () -> Unit,
) {
    LazyColumn {
        item {
            Button(onClick = signInSeamlessly) {
                Text("Sign In (Seamless)")
            }
        }
        item {
            Button(onClick = signInWithDialog) {
                Text("Sign In (Dialog)")
            }
        }
        item {
            Button(onClick = signOut) {
                Text("Sign out")
            }
        }
        item {
            Text(text = "ID Token: ${user?.idToken?.asString()
                ?.split(".")?.map{ it.take(6) }
                ?.joinToString("...") ?: "Unknown"}")
        }
        item {
            Text(text = "Email: ${user?.email?.asString() ?: "Unknown"}")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileContentPreview() {
    ProfileContent()
}
