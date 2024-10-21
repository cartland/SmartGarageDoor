package com.chriscartland.garage.remotebutton

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.auth.AuthRepository
import com.chriscartland.garage.auth.AuthState
import com.chriscartland.garage.auth.FirebaseIdToken
import com.chriscartland.garage.door.DoorRepository
import com.chriscartland.garage.internet.IdToken
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.util.Date
import javax.inject.Inject

interface RemoteButtonViewModel {
    val requestStatus: StateFlow<RequestStatus>
    fun pushRemoteButton()
    fun resetRemoteButton()
}

@HiltViewModel
class RemoteButtonViewModelImpl @Inject constructor(
    // Remote button repository focused on sending the request over the Internet.
    private val pushRepository: PushRepository,
    // Watch the door status, because we consider the request delivered when the door moves.
    private val doorRepository: DoorRepository,
    // Need the auth token to send a request.
    private val authRepository: AuthRepository,
) : ViewModel(), RemoteButtonViewModel {
    // Listen to network events and door status updates.
    private val _requestStatus = MutableStateFlow(RequestStatus.NONE)
    override val requestStatus: StateFlow<RequestStatus> = _requestStatus

    init {
        setupRequestStateMachine()
    }

    /**
     * State machine for [requestStatus].
     */
    private fun setupRequestStateMachine() {
        listenToButtonRepository()
        listenToDoorPosition()
        listenToRequestTimeouts()
    }

    /**
     * Listen to button pushes and update [requestStatus].
     *
     * When [PushStatus] becomes [PushStatus.SENDING], then [RequestStatus] is SENDING.
     *
     * When [PushStatus] becomes [PushStatus.IDLE]:
     *   - if the [RequestStatus] is SENDING, [RequestStatus] becomes SENT.
     *   - otherwise [RequestStatus] becomes NONE (reset state machine).
     */
    private fun listenToButtonRepository() {
        viewModelScope.launch(Dispatchers.IO) {
            pushRepository.status.collect { sendStatus ->
                val old = _requestStatus.value
                _requestStatus.value = when (sendStatus) {
                    PushStatus.SENDING -> {
                        RequestStatus.SENDING
                    }
                    PushStatus.IDLE -> {
                        when (old) {
                            RequestStatus.SENDING -> RequestStatus.SENT
                            // All others -> NONE
                            RequestStatus.NONE -> RequestStatus.NONE
                            RequestStatus.SENT -> RequestStatus.NONE
                            RequestStatus.RECEIVED -> RequestStatus.NONE
                            RequestStatus.SENDING_TIMEOUT -> RequestStatus.NONE
                            RequestStatus.SENT_TIMEOUT -> RequestStatus.NONE
                        }
                    }
                }
                Log.d(TAG, "ButtonRequestStateMachine network: old $old -> " +
                        "new ${_requestStatus.value.name}")
            }
        }
    }

    /**
     * Listen to door position changes. Assume any change means the request was received.
     *
     * When the door moves:
     *   - If [RequestStatus] is NONE, ignore the door movement.
     *   - Otherwise, [RequestStatus] becomes [RequestStatus.RECEIVED].
     */
    private fun listenToDoorPosition() {
        viewModelScope.launch(Dispatchers.IO) {
            doorRepository.currentDoorPosition.collect {
                val old = _requestStatus.value
                when (_requestStatus.value) {
                    RequestStatus.NONE -> {} // Do nothing.
                    // All others -> RECEIVED
                    RequestStatus.SENDING ->
                        _requestStatus.value = RequestStatus.RECEIVED
                    RequestStatus.SENDING_TIMEOUT ->
                        _requestStatus.value = RequestStatus.RECEIVED
                    RequestStatus.SENT ->
                        _requestStatus.value = RequestStatus.RECEIVED
                    RequestStatus.SENT_TIMEOUT ->
                        _requestStatus.value = RequestStatus.RECEIVED
                    RequestStatus.RECEIVED ->
                        _requestStatus.value = RequestStatus.RECEIVED
                }
                Log.d(TAG, "ButtonRequestStateMachine door: old $old -> " +
                        "new ${_requestStatus.value.name}")
            }
        }
    }

    /**
     * State machine to handle timeouts.
     *
     * Track coroutine job to ensure only 1 is running at a time.
     */
    private fun listenToRequestTimeouts() {
        var job: Job? = null // Job to track coroutines.
        val mutex = Mutex()
        viewModelScope.launch(Dispatchers.IO) {
            requestStatus.collect {
                when (it) {
                    RequestStatus.NONE -> {
                        job?.cancel()
                    } // Do nothing.
                    RequestStatus.SENDING -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            // Check to make sure state has not changed.
                            if (_requestStatus.value != RequestStatus.SENDING) {
                                Log.wtf(TAG, "ButtonRequestStatus unexpectedly changed")
                            } else {
                                _requestStatus.value = RequestStatus.SENDING_TIMEOUT
                            }
                        }
                        mutex.unlock()
                    }
                    RequestStatus.SENT -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.SENT) {
                                Log.wtf(TAG, "ButtonRequestStatus unexpectedly changed")
                            } else {
                                _requestStatus.value = RequestStatus.SENT_TIMEOUT
                            }
                        }
                        mutex.unlock()
                    }
                    RequestStatus.RECEIVED -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.RECEIVED) {
                                Log.wtf(TAG, "ButtonRequestStatus unexpectedly changed")
                            }
                            _requestStatus.value = RequestStatus.NONE
                        }
                        mutex.unlock()
                    }
                    RequestStatus.SENDING_TIMEOUT -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.SENDING_TIMEOUT) {
                                Log.wtf(TAG, "ButtonRequestStatus unexpectedly changed")
                            }
                            _requestStatus.value = RequestStatus.NONE
                        }
                        mutex.unlock()
                    }
                    RequestStatus.SENT_TIMEOUT -> {
                        mutex.lock()
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            if (_requestStatus.value != RequestStatus.SENT_TIMEOUT) {
                                Log.wtf(TAG, "ButtonRequestStatus unexpectedly changed")
                            }
                            _requestStatus.value = RequestStatus.NONE
                        }
                        mutex.unlock()
                    }
                }
                Log.d(TAG, "ButtonRequestStateMachine timeouts: " +
                        _requestStatus.value.name
                )
            }
        }
    }

    /**
     * Push the button.
     *
     * Requires an authenticated user.
     */
    override fun pushRemoteButton() {
        Log.d(TAG, "pushRemoteButton")
        viewModelScope.launch(Dispatchers.IO) {
            val authState = authRepository.authState.value
            if (authState !is AuthState.Authenticated) {
                Log.e(TAG, "Not authenticated")
                return@launch
            }
            val idToken = ensureFreshIdToken(authState)
            Log.d(TAG, "pushRemoteButton: Pushing remote button: $idToken")
            pushRepository.push(
                idToken = IdToken(idToken.asString()),
                buttonAckToken = createButtonAckToken(Date()),
            )
        }
    }

    /**
     * Ensure the ID token is fresh.
     */
    private suspend fun ensureFreshIdToken(authState: AuthState.Authenticated): FirebaseIdToken {
        Log.d(TAG, "refreshIdToken")
        return if (authState.user.idToken.exp > System.currentTimeMillis()) {
            Log.d(TAG, "freshIdToken: Using cached token")
            authState.user.idToken
        } else {
            val newAuthState = authRepository.refreshFirebaseAuthState()
            if (newAuthState !is AuthState.Authenticated) {
                Log.w(TAG, "freshIdToken: Not authenticated")
                authState.user.idToken
            } else {
                Log.d(TAG, "freshIdToken: New token")
                newAuthState.user.idToken
            }
        }
    }

    override fun resetRemoteButton() {
        _requestStatus.value = RequestStatus.NONE
    }
}

enum class RequestStatus {
    NONE, // Not sending a request.
    SENDING, // Sending request over the network.
    SENDING_TIMEOUT, // Cannot reach server.
    SENT, // Server acknowledged.
    SENT_TIMEOUT, // Door did not move.
    RECEIVED, // Door moved.
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class RemoteButtonViewModelModule {
    @Binds
    abstract fun bindRemoteButtonViewModel(
        remoteButtonViewModel: RemoteButtonViewModelImpl,
    ): RemoteButtonViewModel
}

private const val TAG = "RemoteButtonViewModel"
