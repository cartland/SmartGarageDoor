package com.chriscartland.garage.viewmodel

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.FetchOnViewModelInit
import com.chriscartland.garage.internet.IdToken
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.model.User
import com.chriscartland.garage.repository.FirebaseAuthRepository
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
    private val authRepository: FirebaseAuthRepository,
) : ViewModel() {

    suspend fun fetchBuildTimestampCached(): String? =
        garageRepository.fetchServerConfigCached()?.buildTimestamp

    val user: StateFlow<User?> = authRepository.user

    fun signInSeamlessly(activityContext: ComponentActivity) {
        activityContext.lifecycleScope.launch(Dispatchers.IO) {
            authRepository.signInSeamlessly(activityContext)
        }
    }

    fun signInWithDialog(activityContext: ComponentActivity) {
        activityContext.lifecycleScope.launch(Dispatchers.IO) {
            authRepository.signInWithDialog(activityContext)
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.signOut()
        }
    }

    fun handleOneTapSignIn(activity: ComponentActivity, data: Intent?) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            authRepository.handleSignIn(activity, data)
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
            val idToken = user.value?.idToken ?: return@launch
            Log.d("pushRemoteButton", "Pushing remote button: $idToken")
            if (idToken.asString().isEmpty()) { // TODO: Sync across views
//                authRepository.seamlessSignIn(activityContext)
            }
            garageRepository.pushRemoteButton(IdToken(idToken.asString()))
        }
    }
}