/*
 * Copyright 2021 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.chriscartland.garage.App
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorDataAge
import com.chriscartland.garage.model.LoadingState
import com.chriscartland.garage.model.ServerConfig
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.Date


class DoorViewModel(application: Application) : AndroidViewModel(application) {

    val app = application as App

    val appVersion = app.repository.appVersion

    val loadingConfig = app.repository.loadingConfig

    val doorData = app.repository.doorData

    val message = MediatorLiveData<String>()

    val lastCheckInTimeString = MediatorLiveData<String>()
    val checkInAge = MutableLiveData<DoorDataAge>()

    val lastChangeTimeString = MediatorLiveData<String>()
    val changeAge = MediatorLiveData<DoorDataAge>()

    val showRemoteButton = MediatorLiveData<Boolean>()

    val remoteButtonEnabled = MutableLiveData<Boolean>()

    val progressBarVisible = MediatorLiveData<Boolean>()

    val firebaseUser = MutableLiveData<FirebaseUser?>()

    var showOneTapUI = MutableLiveData<Boolean>().also {
        it.value = true
    }
    var oneTapSignInClient: SignInClient? = null
    var oneTapSignInRequest: BeginSignInRequest? = null

    var buildTimestamp: String? = null
        set(value) {
            field = value
            Log.d(TAG, "Setting buildTimestamp $value")
            refreshData()
        }

    fun shouldShowRemoteButton(): Boolean {
        val config = loadingConfig.value?.data
        val user = firebaseUser.value
        return config != null
                && config.remoteButtonEnabled
                && user != null
                && config.remoteButtonAuthorizedEmails?.contains(user.email) == true
    }

    fun refreshData() {
        Log.d(TAG, "refreshData")
        val bt = buildTimestamp
        if (bt == null) {
            Log.w(TAG, "Will not refresh data when buildTimestamp is null")
            return
        }
        app.repository.refreshData(app, bt)
    }

    fun enableRemoteButton() {
        Log.d(TAG, "enableRemoteButton")
        remoteButtonEnabled.value = true
    }

    fun disableRemoteButton() {
        Log.d(TAG, "disableRemoteButton")
        remoteButtonEnabled.value = false
    }

    fun showProgressBar() {
        Log.d(TAG, "showProgressBar")
        progressBarVisible.value = true
    }

    fun hideProgressBar() {
        Log.d(TAG, "hideProgressBar")
        progressBarVisible.value = false
    }

    fun onUpdateTime(now: Date) {
        updateTimeSinceLastCheckIn(now)
        updateTimeSinceLastChange(now)
    }

    private fun updateTimeSinceLastCheckIn(now: Date) {
        val lastCheckInTime = doorData.value?.lastCheckInTimeSeconds
        checkInAge.value = if (lastCheckInTime == null) {
            DoorDataAge(
                ageSeconds = 0,
                doorData = null
            )
        } else {
            DoorDataAge(
                ageSeconds = ((now.time / 1000) - lastCheckInTime).coerceAtLeast(0),
                doorData = doorData.value
            )
        }
    }

    private fun updateTimeSinceLastChange(now: Date) {
        val lastChangeTime = doorData.value?.lastChangeTimeSeconds
        changeAge.value = if (lastChangeTime == null) {
            DoorDataAge(
                ageSeconds = 0,
                doorData = null
            )
        } else {
            DoorDataAge(
                ageSeconds = ((now.time / 1000) - lastChangeTime).coerceAtLeast(0),
                doorData = doorData.value
            )
        }
    }

    fun handleOneTapSignIn(activity: Activity, data: Intent?) {
        try {
            val oneTapClient = oneTapSignInClient ?: return
            val credential = oneTapClient.getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken ?: return
            firebaseAuthWithGoogle(activity, idToken)
        } catch (e: ApiException) {
            Log.e(TAG, "ApiException: handleOneTapSignIn ${e.message}")
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> {
                    Log.d(TAG, "One-tap dialog was closed.")
                    // Don't re-prompt the user.
                    showOneTapUI.value = false
                }
                CommonStatusCodes.NETWORK_ERROR -> {
                    Log.d(TAG, "One-tap encountered a network error.")
                    showOneTapUI.value = true
                }
                else -> {
                    Log.d(
                        TAG, "Couldn't get credential from result." +
                                " (${e.localizedMessage})")
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(activity: Activity, idToken: String) {
        Log.d(TAG, "firebaseAuthWithGoogle")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        Firebase.auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = Firebase.auth.currentUser
                    firebaseUser.value = user
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    firebaseUser.value = null
                }
            }
    }

    fun signOut() {
        Firebase.auth.signOut()
        oneTapSignInClient?.signOut()
        firebaseUser.value = null
    }

    private fun onServerConfigChanged(config: ServerConfig?) {
        Log.d(TAG, "handleConfigData: ${config?.toString()}")
        val bt = config?.buildTimestamp
        if (bt.isNullOrEmpty()) {
            Log.d(TAG, "Not a valid config. buildTimestamp is null or empty.")
            buildTimestamp = null
            return
        }
        Log.d(TAG, "onServerConfigChanged: Setting buildTimestamp: $bt")
        buildTimestamp = bt
    }

    init {
        Log.d(TAG, "init")
        message.addSource(doorData) { doorData ->
            Log.d(TAG, "Updating message")
            message.value = doorData?.message ?: ""
        }
        lastCheckInTimeString.addSource(doorData) { doorData ->
            Log.d(TAG, "Updating lastCheckInTimeString")
            lastCheckInTimeString.value = doorData?.toLastCheckInTimeString()
        }
        lastChangeTimeString.addSource(doorData) { doorData ->
            Log.d(TAG, "Updating lastChangeTimeString")
            lastChangeTimeString.value = doorData?.toLastChangeTimeString()
        }
        showRemoteButton.addSource(firebaseUser) { firebaseUser ->
            showRemoteButton.value = shouldShowRemoteButton()
        }
        showRemoteButton.addSource(loadingConfig) { (value, state) ->
            showRemoteButton.value = shouldShowRemoteButton()
        }
        loadingConfig.observeForever { loadingConfig ->
            val configData = loadingConfig.data
            val state = loadingConfig.loading
            Log.d(TAG, "configData: ${configData}")
            when (state) {
                LoadingState.NO_DATA -> {}
                LoadingState.LOADING_DATA -> {}
                LoadingState.LOADED_DATA -> {
                    onServerConfigChanged(configData)
                }
            }
        }
    }

    companion object {
        val TAG: String = DoorViewModel::class.java.simpleName
    }
}

private fun DoorData.toLastCheckInTimeString(): String {
    val lastCheckInTime = this.lastCheckInTimeSeconds ?: return ""
    return DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastCheckInTime * 1000)).toString()
}

private fun DoorData.toLastChangeTimeString(): String {
    val lastChangeTime = this.lastChangeTimeSeconds ?: return ""
    return DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastChangeTime * 1000)).toString()
}
