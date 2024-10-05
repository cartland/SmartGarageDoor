package com.chriscartland.garage.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.viewmodel.DoorViewModel

@Composable
fun ProfileContent(
    viewModel: DoorViewModel = hiltViewModel()
) {
    val context = LocalContext.current as ComponentActivity
    val idToken by viewModel.idToken.collectAsState()
    LazyColumn {
        item {
            Text("ID Token: ${idToken.take(20)}")
        }
        item {
            Button(onClick = { viewModel.seamlessSignIn(context) }) {
                Text("Seamless sign in")
            }
        }
        item {
            Button(onClick = { viewModel.signInWithGoogle(context) }) {
                Text("Sign in with Google")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileContentPreview() {
    ProfileContent()
}
