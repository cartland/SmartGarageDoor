package com.chriscartland.garage.viewmodel

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.FetchOnViewModelInit
import com.chriscartland.garage.internet.IdToken
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.model.Result
import com.chriscartland.garage.model.User
import com.chriscartland.garage.model.dataOrNull
import com.chriscartland.garage.repository.GarageRepository
import com.chriscartland.garage.repository.PushButtonStatus
import com.chriscartland.garage.repository.RemoteButtonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class DoorViewModel @Inject constructor(
    private val garageRepository: GarageRepository,
    private val remoteButtonRepository: RemoteButtonRepository,
) : ViewModel() {
    // Listen to network events and door status updates.
    private val _remoteButtonRequestStatus = MutableStateFlow(RemoteButtonRequestStatus.NONE)
    val remoteButtonRequestStatus: StateFlow<RemoteButtonRequestStatus> = _remoteButtonRequestStatus

    suspend fun fetchBuildTimestampCached(): String? =
        garageRepository.buildTimestamp()

//    val user: StateFlow<User?> = authRepository.user

    private val _currentDoorEvent = MutableStateFlow<Result<DoorEvent?>>(
        Result.Loading(null), // Initial data.
    )
    val currentDoorEvent: StateFlow<Result<DoorEvent?>> = _currentDoorEvent

    private val _recentDoorEvents = MutableStateFlow<Result<List<DoorEvent>>>(
        Result.Loading(listOf()), // Initial data.
    )
    val recentDoorEvents: StateFlow<Result<List<DoorEvent>>> = _recentDoorEvents

//    fun signInSeamlessly(activityContext: ComponentActivity) {
//        activityContext.lifecycleScope.launch(Dispatchers.IO) {
//            authRepository.signInSeamlessly(activityContext)
//        }
//    }
//
//    fun signInWithDialog(activityContext: ComponentActivity) {
//        activityContext.lifecycleScope.launch(Dispatchers.IO) {
//            authRepository.signInWithDialog(activityContext)
//        }
//    }
//
//    fun signOut() {
//        viewModelScope.launch(Dispatchers.IO) {
//            authRepository.signOut()
//        }
//    }
//
//    fun handleSignInWithIntent(data: Intent?) {
//        viewModelScope.launch(Dispatchers.IO) {
//            authRepository.handleSignInWithIntent(data)
//        }
//    }

    init {
        Log.d("DoorViewModel", "init")
        viewModelScope.launch(Dispatchers.IO) {
            garageRepository.currentDoorEvent.collect {
                _currentDoorEvent.value = Result.Complete(it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            garageRepository.recentDoorEvents.collect {
                _recentDoorEvents.value = Result.Complete(it)
            }
        }
        // Decide whether to fetch with network data when ViewModel is initialized
        when (APP_CONFIG.fetchOnViewModelInit) {
            FetchOnViewModelInit.Yes -> {
                fetchCurrentDoorEvent()
                fetchRecentDoorEvents()
            }

            FetchOnViewModelInit.No -> { /* Do nothing */
            }
        }
        setupButtonRequestStateMachine()
    }

    private fun setupButtonRequestStateMachine() {
        // Listen to network requests and update the UI accordingly.
        viewModelScope.launch(Dispatchers.IO) {
            remoteButtonRepository.pushButtonStatus.collect { sendStatus ->
                val old = _remoteButtonRequestStatus.value
                _remoteButtonRequestStatus.value = when (sendStatus) {
                    PushButtonStatus.SENDING -> {
                        RemoteButtonRequestStatus.SENDING
                    }
                    PushButtonStatus.IDLE -> {
                        when (old) {
                            RemoteButtonRequestStatus.NONE -> RemoteButtonRequestStatus.NONE
                            RemoteButtonRequestStatus.SENDING -> RemoteButtonRequestStatus.SENT
                            RemoteButtonRequestStatus.SENT -> RemoteButtonRequestStatus.NONE
                            RemoteButtonRequestStatus.RECEIVED -> RemoteButtonRequestStatus.NONE
                            RemoteButtonRequestStatus.SENDING_TIMEOUT -> RemoteButtonRequestStatus.NONE
                            RemoteButtonRequestStatus.SENT_TIMEOUT -> RemoteButtonRequestStatus.NONE
                        }
                    }
                }
                Log.d("DoorViewModel", "ButtonRequestStateMachine network: old $old -> " +
                        "new ${_remoteButtonRequestStatus.value.name}")
            }
        }
        // Listen to door events. Assume any change in door status means the request was received.
        viewModelScope.launch(Dispatchers.IO) {
            currentDoorEvent.collect {
                val old = _remoteButtonRequestStatus.value
                when (_remoteButtonRequestStatus.value) {
                    RemoteButtonRequestStatus.NONE -> {} // Do nothing.
                    RemoteButtonRequestStatus.SENDING -> {
                        _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.RECEIVED
                    }
                    RemoteButtonRequestStatus.SENT -> {
                        _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.RECEIVED
                    }
                    RemoteButtonRequestStatus.RECEIVED -> {} // Do nothing.
                    RemoteButtonRequestStatus.SENDING_TIMEOUT -> {
                        _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.RECEIVED
                    }
                    RemoteButtonRequestStatus.SENT_TIMEOUT -> {
                        _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.RECEIVED
                    }
                }
                Log.d("DoorViewModel", "ButtonRequestStateMachine door: old $old -> " +
                        "new ${_remoteButtonRequestStatus.value.name}")
            }
        }
        // Listen to the status for timeouts.
        var job: Job? = null
        viewModelScope.launch(Dispatchers.IO) {
            remoteButtonRequestStatus.collect {
                val old = _remoteButtonRequestStatus.value
                when (it) {
                    RemoteButtonRequestStatus.NONE -> {
                        job?.cancel()
                    } // Do nothing.
                    RemoteButtonRequestStatus.SENDING -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.SENDING_TIMEOUT
                        }
                    }
                    RemoteButtonRequestStatus.SENT -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.SENT_TIMEOUT
                        }
                    }
                    RemoteButtonRequestStatus.RECEIVED -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.NONE
                        }
                    }
                    RemoteButtonRequestStatus.SENDING_TIMEOUT -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.NONE
                        }
                    }
                    RemoteButtonRequestStatus.SENT_TIMEOUT -> {
                        job?.cancel()
                        job = viewModelScope.launch {
                            delay(Duration.ofSeconds(10))
                            _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.NONE
                        }
                    }
                }
                Log.d("DoorViewModel", "ButtonRequestStateMachine timeouts: " +
                        _remoteButtonRequestStatus.value.name
                )
            }
        }
    }

    fun fetchCurrentDoorEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            _currentDoorEvent.value = Result.Loading(_currentDoorEvent.value.dataOrNull())
            garageRepository.fetchCurrentDoorEvent()
        }
    }

    fun fetchRecentDoorEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            _recentDoorEvents.value = Result.Loading(_recentDoorEvents.value.dataOrNull())
            garageRepository.fetchRecentDoorEvents()
        }
    }

    fun pushRemoteButton(user: User) {
        Log.d("DoorViewModel", "pushRemoteButton")
        viewModelScope.launch(Dispatchers.IO) {
            val idToken = user.idToken
            Log.d("DoorViewModel", "pushRemoteButton: Pushing remote button: $idToken")
            remoteButtonRepository.pushRemoteButton(
                idToken = IdToken(idToken.asString()),
                buttonAckToken = remoteButtonRepository.createButtonAckToken(),
            )
        }
    }

    fun resetRemoteButton() {
        _remoteButtonRequestStatus.value = RemoteButtonRequestStatus.NONE
    }
}

enum class RemoteButtonRequestStatus {
    NONE, // Not sending a request.
    SENDING, // Sending request over the network.
    SENDING_TIMEOUT, // Cannot reach server.
    SENT, // Server acknowledged.
    SENT_TIMEOUT, // Door did not move.
    RECEIVED, // Door moved.
}
