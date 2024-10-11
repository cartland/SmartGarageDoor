package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.viewmodel.DoorViewModel

@Composable
fun RemoteButtonContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(),
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    RemoteButtonContent(
        modifier = modifier,
        onClick = { viewModel.pushRemoteButton() },
        buttonColors = buttonColors,
    )
}

@Composable
fun RemoteButtonContent(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(256.dp),
            shape = CircleShape,
            colors = buttonColors,
            contentPadding = PaddingValues(0.dp), // Remove default padding
        ) {
            Text(
                text = "Garage\nRemote\nButton",
                color = buttonColors.contentColor,
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RemoteButtonContentPreview() {
    RemoteButtonContent(
        onClick = {},
    )
}
