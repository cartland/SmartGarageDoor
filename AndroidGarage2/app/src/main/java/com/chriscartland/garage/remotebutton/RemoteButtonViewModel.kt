package com.chriscartland.garage.remotebutton

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.auth.AuthRepository
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.FirebaseIdToken
import com.chriscartland.garage.door.DoorRepository
import com.chriscartland.garage.internet.IdToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class RemoteButtonViewModelImpl @Inject constructor(
    private val doorRepository: DoorRepository,
    private val remoteButtonRepository: RemoteButtonRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    // Listen to network events and door status updates.
    private val _buttonRequestStatus = MutableStateFlow(ButtonRequestStatus.NONE)
    val buttonRequestStatus: StateFlow<ButtonRequestStatus> = _buttonRequestStatus

    init {
        setupButtonRequestStateMachine()
    }

    private fun setupButtonRequestStateMachine() {
        // Listen to button pushes and update the UI accordingly.
        viewModelScope.launch(Dispatchers.IO) {
            remoteButtonRepository.pushStatus.collect { sendStatus ->
                val old = _buttonRequestStatus.value
                _buttonRequestStatus.value = when (sendStatus) {
                    PushStatus.SENDING -> {
                        ButtonRequestStatus.SENDING
                    }
                    PushStatus.IDLE -> {
                        when (old) {
                            ButtonRequestStatus.NONE -> ButtonRequestStatus.NONE
                            ButtonRequestStatus.SENDING -> ButtonRequestStatus.SENT
                            ButtonRequestStatus.SENT -> ButtonRequestStatus.NONE
                            ButtonRequestStatus.RECEIVED -> ButtonRequestStatus.NONE
                            ButtonRequestStatus.SENDING_TIMEOUT -> ButtonRequestStatus.NONE
                            ButtonRequestStatus.SENT_TIMEOUT -> ButtonRequestStatus.NONE
                        }
                    }
                }
                Log.d("DoorViewModel", "ButtonRequestStateMachine network: old $old -> " +
                        "new ${_buttonRequestStatus.value.name}")
            }
        }
        // Listen to door events. Assume any change in door status means the request was received.
        viewModelScope.launch(Dispatchers.IO) {
            doorRepository.currentDoorEvent.collect {
                val old = _buttonRequestStatus.value
                when (_buttonRequestStatus.value) {
                    ButtonRequestStatus.NONE -> {} // Do nothing.
                    ButtonRequestStatus.SENDING -> {
                        _buttonRequestStatus.value = ButtonRequestStatus.RECEIVED
                    }
                    ButtonRequestStatus.SENT -> {
                        _buttonRequestStatus.value = ButtonRequestStatus.RECEIVED
                    }
                    ButtonRequestStatus.RECEIVED -> {} // Do nothing.
                    ButtonRequestStatus.SENDING_TIMEOUT -> {
                        _buttonRequestStatus.value = ButtonRequestStatus.RECEIVED
                    }
                    ButtonRequestStatus.SENT_TIMEOUT -> {
                        _buttonRequestStatus.value = ButtonRequestStatus.RECEIVED
                    }
                }
                Log.d("DoorViewModel", "ButtonRequestStateMachine door: old $old -> " +
                        "new ${_buttonRequestStatus.value.name}")
            }
        }
        // Listen to the status for timeouts.
        var job: Job? = null
        viewModelScope.launch(Dispatchers.IO) {
            buttonRequestStatus.collect {
                when (it) {
                    ButtonRequestStatus.NONE -> {
                        job?.cancel()
                    } // Do nothing.
                    ButtonRequestStatus.SENDING -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _buttonRequestStatus.value = ButtonRequestStatus.SENDING_TIMEOUT
                        }
                    }
                    ButtonRequestStatus.SENT -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _buttonRequestStatus.value = ButtonRequestStatus.SENT_TIMEOUT
                        }
                    }
                    ButtonRequestStatus.RECEIVED -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _buttonRequestStatus.value = ButtonRequestStatus.NONE
                        }
                    }
                    ButtonRequestStatus.SENDING_TIMEOUT -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _buttonRequestStatus.value = ButtonRequestStatus.NONE
                        }
                    }
                    ButtonRequestStatus.SENT_TIMEOUT -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _buttonRequestStatus.value = ButtonRequestStatus.NONE
                        }
                    }
                }
                Log.d("DoorViewModel", "ButtonRequestStateMachine timeouts: " +
                        _buttonRequestStatus.value.name
                )
            }
        }
    }

    fun pushRemoteButton() {
        Log.d(TAG, "pushRemoteButton")
        viewModelScope.launch(Dispatchers.IO) {
            val authState = authRepository.authState.value
            if (authState !is AuthState.Authenticated) {
                Log.e(TAG, "Not authenticated")
                return@launch
            }
            val idToken = refreshIdToken(authState)
            Log.d(TAG, "pushRemoteButton: Pushing remote button: $idToken")
            remoteButtonRepository.pushRemoteButton(
                idToken = IdToken(idToken.asString()),
                buttonAckToken = createButtonAckToken(Date()),
            )
        }
    }

    private suspend fun refreshIdToken(authState: AuthState.Authenticated): FirebaseIdToken {
        Log.d(TAG, "refreshIdToken")
        return if (authState.user.idToken.exp > System.currentTimeMillis()) {
            Log.d(TAG, "freshIdToken: Using cached token")
            authState.user.idToken
        } else {
            val newAuthState = authRepository.refreshFirebaseAuthState()
            if (newAuthState !is AuthState.Authenticated) {
                Log.d(TAG, "freshIdToken: Not authenticated")
                authState.user.idToken
            } else {
                Log.d(TAG, "freshIdToken: New token")
                newAuthState.user.idToken
            }
        }
    }

    fun resetRemoteButton() {
        _buttonRequestStatus.value = ButtonRequestStatus.NONE
    }
}

enum class ButtonRequestStatus {
    NONE, // Not sending a request.
    SENDING, // Sending request over the network.
    SENDING_TIMEOUT, // Cannot reach server.
    SENT, // Server acknowledged.
    SENT_TIMEOUT, // Door did not move.
    RECEIVED, // Door moved.
}

private const val TAG = "RemoteButtonViewModel"