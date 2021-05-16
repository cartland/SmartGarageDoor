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
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.chriscartland.garage.App
import com.chriscartland.garage.repository.FirestoreDocumentReferenceLiveData
import com.chriscartland.garage.MainActivity
import com.chriscartland.garage.model.AppVersion
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorState
import com.chriscartland.garage.model.ServerConfig
import com.chriscartland.garage.model.toDoorData
import com.chriscartland.garage.model.toServerConfig
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.ktx.Firebase
import java.util.Date


class DoorViewModel(val app: App) : AndroidViewModel(app) {

    val appVersion = app.repository.appVersion

    fun updatePackageVersion(packageManager: PackageManager, packageName: String) =
        app.repository.updatePackageVersion(packageManager, packageName)


    enum class State {
        DEFAULT,
        LOADING_DATA,
        LOADED_DATA
    }

    val configDataState: MediatorLiveData<Pair<ServerConfig?, State>> = MediatorLiveData()
    private val configDataFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(
            null
        )
    fun setConfigDataDocumentReference(documentReference: DocumentReference?) {
        Log.d(TAG, "setServerConfigDocumentReference")
        configDataFirestore.documentReference = documentReference
        configDataState.value = Pair(
            null,
            State.LOADING_DATA
        )
    }

    val doorDataState: MediatorLiveData<Pair<DoorData?, State>> = MediatorLiveData()
    private val doorStatusFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(
            null
        )
    fun setDoorStatusDocumentReference(documentReference: DocumentReference?) {
        Log.d(TAG, "setDoorStatusDocumentReference")
        doorStatusFirestore.documentReference = documentReference
        doorDataState.value = Pair(
            null,
            State.LOADING_DATA
        )
    }

    lateinit var statusColorMap: Map<DoorState, Pair<String, Int>>
    val statusTitle = MutableLiveData<String>()
    val message = MutableLiveData<String>()

    val lastCheckInTimeString = MutableLiveData<String>()
    private fun DoorData.toLastCheckInTimeString(): String {
        val lastCheckInTime = this.lastCheckInTimeSeconds ?: return ""
        return DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastCheckInTime * 1000)).toString()
    }

    val lastChangeTimeString = MutableLiveData<String>()
    private fun DoorData.toLastChangeTimeString(): String {
        val lastChangeTime = this.lastChangeTimeSeconds ?: return ""
        return DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastChangeTime * 1000)).toString()
    }

    val showRemoteButton = MediatorLiveData<Boolean>()

    val remoteButtonEnabled = MutableLiveData<Boolean>()

    fun enableRemoteButton() {
        Log.d(TAG, "enableRemoteButton")
        remoteButtonEnabled.value = true
    }

    fun disableRemoteButton() {
        Log.d(TAG, "disableRemoteButton")
        remoteButtonEnabled.value = false
    }

    val progressBarVisible = MediatorLiveData<Boolean>()

    fun showProgressBar() {
        Log.d(TAG, "showProgressBar")
        progressBarVisible.value = true
    }

    fun hideProgressBar() {
        Log.d(TAG, "hideProgressBar")
        progressBarVisible.value = false
    }

    val firebaseUser = MutableLiveData<FirebaseUser?>()

    var showOneTapUI = MutableLiveData<Boolean>().also {
        it.value = true
    }
    var oneTapSignInClient: SignInClient? = null
    var oneTapSignInRequest: BeginSignInRequest? = null

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

    init {
        Log.d(TAG, "init")
        doorDataState.value = Pair(
            null,
            State.DEFAULT
        )
        doorDataState.addSource(doorStatusFirestore) { value ->
            Log.d(TAG, "Received Firestore update for DoorData")
            val doorData = value?.toDoorData() ?: return@addSource
            doorDataState.value = Pair(doorData,
                State.LOADED_DATA
            )
            val state = doorData.state ?: DoorState.UNKNOWN
            val (title, color) = statusColorMap[state] ?: return@addSource
            statusTitle.value = title
            message.value = doorData.message ?: ""
            lastCheckInTimeString.value = doorData?.toLastCheckInTimeString()
            lastChangeTimeString.value = doorData?.toLastChangeTimeString()
        }
        configDataState.value = Pair(
            null,
            State.DEFAULT
        )
        configDataState.addSource(configDataFirestore) { value ->
            Log.d(TAG, "Received Firestore update for ServerConfig")
            configDataState.value = Pair(value?.toServerConfig(),
                State.LOADED_DATA
            )
        }
        showRemoteButton.addSource(firebaseUser) { firebaseUser ->
            showRemoteButton.value = shouldShowRemoteButton()
        }
        showRemoteButton.addSource(configDataState) { (value, state) ->
            showRemoteButton.value = shouldShowRemoteButton()
        }
    }

    fun shouldShowRemoteButton(): Boolean {
        val config = configDataState.value?.first
        val user = firebaseUser.value
        return config != null
                && config.remoteButtonEnabled
                && user != null
                && config.remoteButtonAuthorizedEmails?.contains(user.email) == true
    }

    companion object {
        val TAG: String = DoorViewModel::class.java.simpleName
    }
}