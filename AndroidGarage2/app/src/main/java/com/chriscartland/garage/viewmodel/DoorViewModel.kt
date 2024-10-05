package com.chriscartland.garage.viewmodel

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.FetchOnViewModelInit
import com.chriscartland.garage.internet.IdToken
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.repository.AuthRepository
import com.chriscartland.garage.repository.GarageRepository
import com.chriscartland.garage.repository.Result
import com.chriscartland.garage.repository.dataOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DoorViewModel @Inject constructor(
    private val garageRepository: GarageRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    suspend fun fetchBuildTimestampCached(): String? =
        garageRepository.fetchServerConfigCached()?.buildTimestamp

    val idToken: StateFlow<String> = authRepository.idToken

    fun seamlessSignIn(activityContext: ComponentActivity) {
        activityContext.lifecycleScope.launch(Dispatchers.IO) {
            authRepository.seamlessSignIn(activityContext)
        }
    }

    fun signInWithGoogle(activityContext: ComponentActivity) {
        activityContext.lifecycleScope.launch(Dispatchers.IO) {
            authRepository.signInWithGoogle(activityContext)
        }
    }

    private val _currentDoorEvent = MutableStateFlow<Result<DoorEvent?>>(
        Result.Loading(null), // Initial data.
    )
    val currentDoorEvent: StateFlow<Result<DoorEvent?>> = _currentDoorEvent.asStateFlow()

    private val _recentDoorEvents = MutableStateFlow<Result<List<DoorEvent>>>(
        Result.Loading(listOf()), // Initial data.
    )
    val recentDoorEvents = _recentDoorEvents.asStateFlow()

    init {
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
            FetchOnViewModelInit.No -> { /* Do nothing */ }
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

    fun pushRemoteButton(activityContext: ComponentActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("pushRemoteButton", "Pushing remote button: ${idToken.value}")
            if (idToken.value.isEmpty()) { // TODO: Sync across views
                authRepository.seamlessSignIn(activityContext)
            }
            garageRepository.pushRemoteButton(IdToken(idToken.value))
        }
    }
}
