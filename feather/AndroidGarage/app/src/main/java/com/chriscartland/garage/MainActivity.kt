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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.chriscartland.garage.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import java.util.Date


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var doorViewModel: DoorViewModel
    private lateinit var authViewModel: AuthViewModel

    private var fcmState = FCMState.DEFAULT
        set(value) {
            field = value
            Log.d(TAG, fcmState.name)
        }

    enum class FCMState {
        DEFAULT,
        SUBSCRIBE_TO_DOOR_OPEN_IN_PROGRESS,
        SUBSCRIBE_TO_DOOR_OPEN_SUCCESS,
        SUBSCRIBE_TO_DOOR_OPEN_FAILED
    }

    private val h: Handler = Handler(Looper.getMainLooper())
    private var checkInRunnable: Runnable? = null
    private var changeRunnable: Runnable? = null
    private var buttonRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        doorViewModel = ViewModelProvider(this).get(DoorViewModel::class.java)
        doorViewModel.doorData.observe(this, Observer { (doorData, state) ->
            when (state) {
                DoorViewModel.State.DEFAULT -> {
                    handleDoorChanged(DoorData(message = getString(R.string.missing_config)))
                }
                DoorViewModel.State.LOADING_DATA -> {
                    handleDoorChanged(DoorData(message = getString(R.string.loading_data)))
                }
                DoorViewModel.State.LOADED_DATA -> {
                    handleDoorChanged(doorData ?: DoorData())
                }
            }
        })
        doorViewModel.configData.observe(this, Observer { (configData, state) ->
            when (state) {
                DoorViewModel.State.DEFAULT -> {}
                DoorViewModel.State.LOADING_DATA -> {}
                DoorViewModel.State.LOADED_DATA -> {
                    handleConfigData(configData)
                }
            }
        })
        doorViewModel.setConfigDataDocumentReference(
            Firebase.firestore.collection("configCurrent").document("current")
        )
        doorViewModel.appVersion.observe(this, Observer { appVersion ->
            val textView = binding.versionCodeTextView
            textView.text = getString(
                R.string.version_code_string,
                appVersion.versionName ?: "",
                appVersion.versionCode ?: 0L
            )
        })
        doorViewModel.updatePackageVersion(packageManager, packageName)

        authViewModel = ViewModelProvider(this).get(AuthViewModel::class.java)
        authViewModel.firebaseUser.observe(this, Observer<FirebaseUser?> {
            updateUserUI()
        })
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        authViewModel.updateGoogleSignInClient(googleSignInClient)

        resetButton() // TODO: Manage button UI in XML.
    }

    fun onSignInClicked(view: View) {
        Log.d(TAG, "onSignInClicked")
        authViewModel.getSignInIntent()?.let {
            startActivityForResult(it, RC_SIGN_IN)
        }
    }

    fun onSignOutClicked(view: View) {
        Log.d(TAG, "onSignOutClicked")
        authViewModel.signOut()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult")
        if (requestCode == RC_SIGN_IN) {
            authViewModel.handleActivitySignIn(this, data)
        }
    }

    fun onPushButton(view: View) {
        Log.d(TAG, "onPushButton")
        val config: ServerConfig? = doorViewModel.configData.value?.first
        if (config == null) {
            Log.e(TAG, "Cannot push button without server configuration")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.button_confirmation_title)
            .setMessage(R.string.button_confirmation_message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.confirm_push_button,
                { dialog, whichButton ->
                    pushRemoteButton(this, config)
                })
            .setNegativeButton(R.string.cancel_push_button, null).show()
    }

    private fun pushRemoteButton(context: Context, config: ServerConfig) {
        Log.d(TAG, "pushRemoteButton")
        disableButtonTemporarily()
        Firebase.auth.currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val idToken = task.result?.token
                if (idToken == null) {
                    Log.e(TAG, "No ID Token");
                    return@addOnCompleteListener
                }
                sendRemoteButtonRequest(context, config, idToken)
            }
        }
    }

    private fun sendRemoteButtonRequest(context: Context, config: ServerConfig, idToken: String) {
        Log.d(TAG, "sendRemoteButtonRequest")
        val queue = Volley.newRequestQueue(context)
        val buildTimestamp = config.remoteButtonBuildTimestamp
        val host = config.host
        val path = config.path
        val key = config.remoteButtonPushKey
        val buttonAckToken = createButtonAckToken()
        val url = "$host/$path?buildTimestamp=$buildTimestamp&buttonAckToken=$buttonAckToken"
        if (buildTimestamp.isNullOrEmpty()) {
            Log.e(TAG, "pushRemoteButton: No remoteButtonBuildTimestamp")
        }
        if (host.isNullOrEmpty() == null) { Log.e(TAG, "pushRemoteButton: No host") }
        if (path.isNullOrEmpty()) { Log.e(TAG, "pushRemoteButton: No path") }
        if (key.isNullOrEmpty()) { Log.e(TAG, "pushRemoteButton: No key") }
        if (idToken.isNullOrEmpty()) { Log.e(TAG, "pushRemoteButton: No ID token") }
        if (buttonAckToken.isNullOrEmpty()) { Log.e(TAG, "pushRemoteButton: No buttonAckToken") }
        Log.d(TAG, "url: $url, key: $key, idToken: $idToken")
        val stringRequest = object : StringRequest(
            Method.POST,
            url,
            Response.Listener<String>
            { response ->
                Log.d(TAG, "Network request success. Response: $response")
            },
            Response.ErrorListener {
                Log.e(TAG, "Network error: ${it.message ?: it.localizedMessage}")
            }) {
            override fun getHeaders(): Map<String, String> {
                val params: MutableMap<String, String> = HashMap()
                params["X-RemoteButtonPushKey"] = key ?: ""
                params["X-AuthTokenGoogle"] = idToken ?: ""
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun createButtonAckToken(): String {
        val now = Date()
        val humandReadable = DateFormat.format("yyyy-MM-dd hh:mm:ss a", now).toString()
        val timestampMillis = now.time
        val appVersion = doorViewModel.appVersion.value
        val buttonAckTokenData = "android-$appVersion-$humandReadable-$timestampMillis"
        val re = Regex("[^a-zA-Z0-9-_.]")
        val filtered = re.replace(buttonAckTokenData, ".")
        return filtered
    }

    private fun disableButtonTemporarily() {
        Log.d(TAG, "disableButtonTemporarily")
        showProgressBar()
        binding.button.isEnabled = false
        binding.button.setBackgroundColor(getColor(R.color.almost_black_blue))
        buttonRunnable?.let {
            h.removeCallbacks(it)
        }
        buttonRunnable = object : Runnable {
            override fun run() {
                resetButton()
                resetProgressBar()
            }
        }
        buttonRunnable?.let {
            val buttonDelay = 30 * 1000L // 30 seconds.
            h.postDelayed(it, buttonDelay)
        }
    }

    private fun resetButton() {
        Log.d(TAG, "resetButton")
        binding.button.isEnabled = true
        binding.button.setBackgroundColor(getColor(R.color.red))
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    private fun updateUserUI() {
        val currentUser = authViewModel.firebaseUser.value
        binding.signInButton.visibility = if (currentUser == null) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.signOutButton.visibility = if (currentUser != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.userEmail.visibility = if (currentUser == null) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.userEmail.text = if (currentUser == null) {
            ""
        } else {
            currentUser.email
        }
        updateButtonUI()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        checkInRunnable?.let {
            h.removeCallbacks(it)
        }
        changeRunnable?.let {
            h.removeCallbacks(it)
        }
        buttonRunnable?.let {
            h.removeCallbacks(it)
        }
        resetProgressBar()
        resetButton()
    }

    private fun handleConfigData(config: ServerConfig?) {
        Log.d(TAG, "handleConfigData: ${config?.toString()}")
        val buildTimestamp = config?.buildTimestamp
        if (buildTimestamp.isNullOrEmpty()) {
            Log.d(TAG, "Not a valid config. buildTimestamp is null or empty.")
            doorViewModel.setDoorStatusDocumentReference(null)
            return
        }
        doorViewModel.setDoorStatusDocumentReference(
            Firebase.firestore.collection("eventsCurrent").document(buildTimestamp)
        )
        registerDoorOpenNotifications(buildTimestamp)
        updateButtonUI()
    }

    private fun updateButtonUI() {
        val config = doorViewModel.configData.value?.first
        val currentUser = authViewModel.firebaseUser.value
        binding.button.visibility = if (
            config != null
            && config.remoteButtonEnabled
            && currentUser != null
            && config.remoteButtonAuthorizedEmails?.contains(currentUser.email) == true
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun registerDoorOpenNotifications(buildTimestamp: String) {
        Log.d(TAG, "registerDoorOpenNotifications")
        val newFcmTopic = buildTimestamp.toDoorOpenFcmTopic()
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val oldFcmTopic = sharedPref.getString(FCM_DOOR_OPEN_TOPIC, "")
        if (oldFcmTopic != null && newFcmTopic != oldFcmTopic) {
            Firebase.messaging.unsubscribeFromTopic(oldFcmTopic)
        }
        with (sharedPref.edit()) {
            putString(FCM_DOOR_OPEN_TOPIC, newFcmTopic)
            apply()
        }
        Log.i(TAG, "Old FCM Topic: $oldFcmTopic, New FCM Topic: $newFcmTopic")
        fcmState = FCMState.SUBSCRIBE_TO_DOOR_OPEN_IN_PROGRESS
        Firebase.messaging.subscribeToTopic(newFcmTopic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    fcmState = FCMState.SUBSCRIBE_TO_DOOR_OPEN_SUCCESS
                } else {
                    fcmState = FCMState.SUBSCRIBE_TO_DOOR_OPEN_FAILED
                    Log.e(TAG, task.exception.toString())
                }

            }
        Firebase.messaging.token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (token == null) {
                    Log.d(TAG, "Fetching FCM registration token null")
                } else {
                    Log.d(FCMService.TAG, "FCM Instance Token: $token")
                }
            }
    }

    private fun handleDoorChanged(doorData: DoorData) {
        Log.d(TAG, "handleDoorChanged")
        resetProgressBar()
        updateStatusTitle(doorData)
        updateStatusMessage(doorData)
        updateLastCheckInTime(doorData)
        updateLastChangeTime(doorData)
        updateTimeSinceLastCheckIn(doorData)
        updateTimeSinceLastChange(doorData)
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun resetProgressBar() {
        binding.progressBar.visibility = View.GONE
    }

    private fun updateStatusTitle(doorData: DoorData) {
        val data = getStatusTitleAndColor(doorData, this)
        val textView = binding.statusTitle
        textView.text = data.first
        textView.setBackgroundColor(data.second)
    }

    private fun updateStatusMessage(doorData: DoorData) {
        val textView = binding.statusMessage
        textView.text = doorData.message
    }

    private fun updateLastCheckInTime(doorData: DoorData) {
        val lastCheckInTime = doorData.lastCheckInTimeSeconds
        val textView = binding.lastCheckInTime
        if (lastCheckInTime != null) {
            val lastCheckInTimeString =
                DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastCheckInTime * 1000))
            textView.text = getString(R.string.last_check_in_time, lastCheckInTimeString)
        } else {
            textView.text = ""
        }
    }

    private fun updateTimeSinceLastCheckIn(doorData: DoorData) {
        val lastCheckInTime = doorData.lastCheckInTimeSeconds
        val textView = binding.timeSinceLastCheckIn
        if (lastCheckInTime == null) {
            textView.text = ""
            checkInRunnable?.let {
                h.removeCallbacks(it)
            }
            return
        }
        checkInRunnable?.let {
            h.removeCallbacks(it)
        }
        checkInRunnable = object : Runnable {
            override fun run() {
                val now = Date()
                val s = ((now.time / 1000) - lastCheckInTime).coerceAtLeast(0)
                val timeSinceLastCheckInString =
                    String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
                textView.text = getString(R.string.time_since_last_check_in, timeSinceLastCheckInString)
                if (s > CHECK_IN_THRESHOLD_SECONDS) {
                    textView.setBackgroundColor(getColor(R.color.color_door_error))
                } else {
                    textView.setBackgroundColor(getColor(R.color.black))
                }
                h.postDelayed(this, 1000)
            }
        }
        checkInRunnable?.let {
            it.run()
            h.postDelayed(it, 1000)
        }
    }

    private fun updateLastChangeTime(doorData: DoorData) {
        val lastChangeTime = doorData.lastChangeTimeSeconds
        val textView = binding.lastChangeTime
        if (lastChangeTime != null) {
            val lastChangeTimeString =
                DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastChangeTime * 1000))
            textView.text = getString(R.string.last_change_time, lastChangeTimeString)
        } else {
            textView.text = null
        }
    }

    private fun updateTimeSinceLastChange(doorData: DoorData) {
        val lastChangeTime = doorData.lastChangeTimeSeconds
        val textView = binding.timeSinceLastChange
        if (lastChangeTime == null) {
            textView.text = ""
            changeRunnable?.let {
                h.removeCallbacks(it)
            }
            return
        }
        changeRunnable?.let {
            h.removeCallbacks(it)
        }
        changeRunnable = object : Runnable {
            override fun run() {
                val now = Date()
                val s = ((now.time / 1000) - lastChangeTime).coerceAtLeast(0)
                val timeSinceLastChangeString = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
                textView.text = getString(R.string.time_since_last_change, timeSinceLastChangeString)
                if (doorData.state != DoorState.CLOSED && s > DOOR_NOT_CLOSED_THRESHOLD_SECONDS) {
                    textView.setBackgroundColor(getColor(R.color.color_door_error))
                } else {
                    textView.setBackgroundColor(getColor(R.color.black))
                }
                h.postDelayed(this, 1000)
            }
        }
        changeRunnable?.let {
            it.run()
            h.postDelayed(it, 1000)
        }
    }

    companion object {
        val TAG: String = MainActivity::class.java.simpleName

        const val CHECK_IN_THRESHOLD_SECONDS = 60 * 15
        const val DOOR_NOT_CLOSED_THRESHOLD_SECONDS = 60 * 15
        const val FCM_DOOR_OPEN_TOPIC = "com.chriscartland.garage.FCM_DOOR_OPEN_TOPIC"

        const val RC_SIGN_IN = 1
    }
}

private fun String.toDoorOpenFcmTopic(): String {
    val re = Regex("[^a-zA-Z0-9-_.~%]")
    val filtered = re.replace(this, ".")
    return "door_open-$filtered"
}