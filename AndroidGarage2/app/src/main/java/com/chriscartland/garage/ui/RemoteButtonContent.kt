package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.remotebutton.RemoteButtonViewModelImpl
import com.chriscartland.garage.remotebutton.RequestStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration

@Composable
fun RemoteButtonContent(
    modifier: Modifier = Modifier,
    viewModel: RemoteButtonViewModelImpl = hiltViewModel(),
    authViewModel: AuthViewModelImpl = hiltViewModel(),
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    remoteRequestStatus: RequestStatus = RequestStatus.NONE,
) {
    val authState by authViewModel.authState.collectAsState()
    RemoteButtonContent(
        modifier = modifier,
        onSubmit = {
            when (authState) {
                is AuthState.Authenticated -> {
                    viewModel.pushRemoteButton()
                }
                AuthState.Unauthenticated -> {
                    // Do nothing.
                }
                AuthState.Unknown -> {
                    // Do nothing.
                }
            }
        },
        buttonColors = buttonColors,
        remoteRequestStatus = remoteRequestStatus,
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
    onArming: () -> Unit = {},
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
    ),
    remoteRequestStatus: RequestStatus = RequestStatus.NONE,
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
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1f)
                .widthIn(max = 256.dp)
                .shadow(4.dp, CircleShape),
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
                        onArming()
                        // Wait a few seconds before ARMED.
                        // If the user does not press the button again within a few seconds,
                        // move to TIMEOUT state.
                        // After the timeout, delay a few seconds, then move to READY state.
                        job?.cancel()
                        job = coroutineScope.launch {
                            countdown = 1
                            while (countdown > 0) {
                                delay(Duration.ofMillis(500))
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
            shape = CircleShape,
            colors = buttonColors,
            contentPadding = PaddingValues(0.dp), // Remove default padding
        ) {
            Text(
                text = when (buttonState) {
                    RemoteButtonState.READY -> "Garage\nRemote\nButton"
                    RemoteButtonState.ARMING -> "Preparing..."
                    RemoteButtonState.ARMED -> "Tap Again\nTo Send Command"
                    RemoteButtonState.TIMEOUT -> "Button not pressed"
                    RemoteButtonState.COOLDOWN -> "Button pushed!"
                },
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (buttonState != RemoteButtonState.READY
            || remoteRequestStatus != RequestStatus.NONE) {
            ButtonRequestIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .widthIn(max = 256.dp),
                remoteRequestStatus = remoteRequestStatus,
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
