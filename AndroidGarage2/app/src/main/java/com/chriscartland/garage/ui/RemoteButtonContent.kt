package com.chriscartland.garage.ui

import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.viewmodel.DoorViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration

@Composable
fun RemoteButtonContent(
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(),
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    val context = LocalContext.current as ComponentActivity
    RemoteButtonContent(
        modifier = modifier,
        onSubmit = { viewModel.pushRemoteButton(context) },
        buttonColors = buttonColors,
    )
}

enum class RemoteButtonState {
    READY, // Default state. Button press moves to ARMING state.
    ARMING, // Force a pause before sending the request. Then go to ARMED.
    ARMED, // One more press will trigger action. No action moves to TIMEOUT state.
    TIMEOUT, // Disable action. Move to READY state after a brief delay.
    COOLDOWN, // After button is pressed. Move to READY state after 10 seconds.
}

@Composable
fun RemoteButtonContent(
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val coroutineScope = rememberCoroutineScope()
        var buttonState by remember { mutableStateOf(RemoteButtonState.READY) }
        var countdown by remember { mutableIntStateOf(10) }
        var job: Job? by remember { mutableStateOf(null) }

        Button(
            enabled = when (buttonState) {
                RemoteButtonState.READY -> true
                RemoteButtonState.ARMING -> false
                RemoteButtonState.ARMED -> true
                RemoteButtonState.TIMEOUT -> false
                RemoteButtonState.COOLDOWN -> false
            },
            onClick = {
                when (buttonState) {
                    RemoteButtonState.READY -> {
                        // READY -> ARMED
                        buttonState = RemoteButtonState.ARMING
                        // Wait a few seconds before ARMED.
                        // If the user does not press the button again within a few seconds,
                        // move to TIMEOUT state.
                        // After the timeout, delay a few seconds, then move to READY state.
                        job?.cancel()
                        job = coroutineScope.launch {
                            countdown = 2
                            while (countdown > 0) {
                                delay(Duration.ofSeconds(1))
                                countdown--
                            }
                            buttonState = RemoteButtonState.ARMED
                            delay(Duration.ofSeconds(5))
                            buttonState = RemoteButtonState.TIMEOUT
                            countdown = 3
                            while (countdown > 0) {
                                delay(Duration.ofSeconds(1))
                                countdown--
                            }
                            buttonState = RemoteButtonState.READY
                        }
                    }
                    RemoteButtonState.ARMING -> {} // Do nothing.
                    RemoteButtonState.ARMED -> {
                        // ARMED -> COOLDOWN
                        buttonState = RemoteButtonState.COOLDOWN
                        // Submit the button press, wait 10 seconds, then move to READY state.
                        job?.cancel()
                        job = coroutineScope.launch {
                            onSubmit()
                            countdown = 10
                            while (countdown > 0) {
                                delay(Duration.ofSeconds(1))
                                countdown--
                            }
                            buttonState = RemoteButtonState.READY
                        }
                    }
                    RemoteButtonState.TIMEOUT -> {} // Do nothing.
                    RemoteButtonState.COOLDOWN -> {} // Do nothing.
                }
            },
            modifier = Modifier.size(256.dp),
            shape = CircleShape,
            colors = when (buttonState) {
                RemoteButtonState.ARMED -> ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White,
                )
                else -> buttonColors
            },
            contentPadding = PaddingValues(0.dp), // Remove default padding
        ) {
            Text(
                text = when (buttonState) {
                    RemoteButtonState.READY -> "Garage\nRemote\nButton"
                    RemoteButtonState.ARMING -> "Tap again\nto move\nthe garage door\n\n$countdown"
                    RemoteButtonState.ARMED -> "PUSH BUTTON"
                    RemoteButtonState.TIMEOUT -> "Button not pressed"
                    RemoteButtonState.COOLDOWN -> "Button pushed!\n\nCooldown...\n$countdown"
                },
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
        onSubmit = {},
    )
}
