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

package com.chriscartland.garage

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.ktx.Firebase


class DoorViewModel : ViewModel() {

    enum class State {
        DEFAULT,
        LOADING_DATA,
        LOADED_DATA
    }

    val appVersion: MutableLiveData<AppVersion> = MutableLiveData<AppVersion>()

    val configData: MediatorLiveData<Pair<ServerConfig?, State>> = MediatorLiveData()
    private val configDataFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(null)
    fun setConfigDataDocumentReference(documentReference: DocumentReference?) {
        Log.d(TAG, "setServerConfigDocumentReference")
        configDataFirestore.documentReference = documentReference
        configData.value = Pair(null, State.LOADING_DATA)
    }

    val doorData: MediatorLiveData<Pair<DoorData?, State>> = MediatorLiveData()
    private val doorStatusFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(null)
    fun setDoorStatusDocumentReference(documentReference: DocumentReference?) {
        Log.d(TAG, "setDoorStatusDocumentReference")
        doorStatusFirestore.documentReference = documentReference
        doorData.value = Pair(null, State.LOADING_DATA)
    }

    fun updatePackageVersion(packageManager: PackageManager, packageName: String) {
        Log.d(MainActivity.TAG, "updatePackageVersionUI")
        packageManager.getPackageInfo(packageName, 0).let {
            val newAppVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                AppVersion(
                    versionCode = it.longVersionCode,
                    versionName = it.versionName
                )
            } else {
                AppVersion(
                    versionCode = it.versionCode.toLong(),
                    versionName = it.versionName
                )
            }
            appVersion.value = newAppVersion
        }
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

    val firebaseUser = MutableLiveData<FirebaseUser?>()

    private var googleSignInClient: GoogleSignInClient? = null

    fun updateGoogleSignInClient(googleSignInClient: GoogleSignInClient) {
        this.googleSignInClient = googleSignInClient
    }

    fun handleActivitySignIn(activity: Activity, data: Intent?) {
        Log.d(TAG, "handleActivitySignIn")
        if (data == null) {
            return
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java) ?: return
            val idToken = account.idToken ?: return
            firebaseAuthWithGoogle(activity, idToken)
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
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

    fun getSignInIntent() = googleSignInClient?.signInIntent

    fun signOut() {
        Firebase.auth.signOut()
        googleSignInClient?.signOut()
        firebaseUser.value = null
    }

    init {
        Log.d(TAG, "init")
        doorData.value = Pair(null, State.DEFAULT)
        doorData.addSource(doorStatusFirestore) { value ->
            Log.d(TAG, "Received Firestore update for DoorData")
            doorData.value = Pair(value?.toDoorData(), State.LOADED_DATA)
        }
        configData.value = Pair(null, State.DEFAULT)
        configData.addSource(configDataFirestore) { value ->
            Log.d(TAG, "Received Firestore update for ServerConfig")
            configData.value = Pair(value?.toServerConfig(), State.LOADED_DATA)
        }
        showRemoteButton.addSource(firebaseUser) { firebaseUser ->
            showRemoteButton.value = shouldShowRemoteButton()
        }
        showRemoteButton.addSource(configData) { (value, state) ->
            showRemoteButton.value = shouldShowRemoteButton()
        }
    }

    fun shouldShowRemoteButton(): Boolean {
        val config = configData.value?.first
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
