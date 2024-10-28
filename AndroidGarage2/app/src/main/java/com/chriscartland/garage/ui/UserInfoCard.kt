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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
