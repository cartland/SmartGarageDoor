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
import android.os.Build
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
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import java.util.Date


class MainActivity : AppCompatActivity() {

    private lateinit var doorViewModel: DoorViewModel
    private lateinit var binding: ActivityMainBinding

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val db = Firebase.firestore
    private var configListener: ListenerRegistration? = null
//    private var doorListener: ListenerRegistration? = null

    private var appVersion: AppVersion? = null
    private var serverConfig: ServerConfig? = null

    private var loadingState = LoadingState.DEFAULT
        set(value) {
            field = value
            onLoadingStateChanged(field)
        }

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
        doorViewModel.doorData.observe(this, Observer {
            handleDoorChanged(it ?: DoorData())
        })
        doorViewModel.doorLoadingState.observe(this, Observer {
            loadingState = when (it) {
                DoorLoadingState.DEFAULT -> LoadingState.NO_CONFIG
                DoorLoadingState.LOADING_DATA -> LoadingState.LOADING_DATA
                DoorLoadingState.LOADED_DATA -> LoadingState.LOADED_DATA
            }
        })

        updatePackageVersionUI()
        resetButton() // TODO: Manage button UI in XML.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        auth = Firebase.auth
        loadConfig()
        val currentUser = auth.currentUser
        onUserUpdated(currentUser)
    }

    fun onSignInClicked(view: View) {
        Log.d(TAG, "onSignInClicked")
        signIn()
    }

    fun onSignOutClicked(view: View) {
        Log.d(TAG, "onSignOutClicked")
        signOut()
    }

    private fun signIn() {
        Log.d(TAG, "signIn")
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun signOut() {
        auth.signOut()
        onUserUpdated(auth.currentUser)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult")
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d(TAG, "firebaseAuthWithGoogle")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    onUserUpdated(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    onUserUpdated(null)
                }
            }
    }

    fun onPushButton(view: View) {
        Log.d(TAG, "onPushButton")
        val config = serverConfig
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
        auth.currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
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

    private fun onUserUpdated(currentUser: FirebaseUser?) {
        Log.d(TAG, "onUserUpdated: ${currentUser?.email}")
        updateUserUI()
    }

    private fun updateUserUI() {
        val currentUser = auth.currentUser
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
        configListener?.remove()
//        doorListener?.remove()
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

    private fun updatePackageVersionUI() {
        Log.d(TAG, "updatePackageVersionUI")
        packageManager.getPackageInfo(packageName, 0).let {
            val textView = binding.versionCodeTextView
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
            textView.text = getString(
                R.string.version_code_string,
                newAppVersion.versionName ?: "",
                newAppVersion.versionCode ?: 0L
            )
            appVersion = newAppVersion
        }
    }

    private fun loadConfig() {
        Log.d(TAG, "loadConfig")
        configListener?.remove()
        loadingState = LoadingState.LOADING_CONFIG
        val configRef = db.collection("configCurrent").document("current")
        configListener = configRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Config listener failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data as Map<*, *>?
                Log.d(TAG, "Config data: $data")
                val config = data?.toServerConfig()
                handleConfigData(config)
            } else {
                Log.d(TAG, "Config data: null")
            }
        }
    }

    private fun onLoadingStateChanged(state: LoadingState) {
        Log.d(TAG, "onLoadingStateChanged: ${state.name}");
        when (state) {
            LoadingState.DEFAULT -> {}
            LoadingState.NO_CONFIG -> {
                handleDoorChanged(DoorData(message = getString(R.string.missing_config)))
            }
            LoadingState.LOADING_CONFIG -> {}
            LoadingState.LOADING_DATA -> {}
            LoadingState.LOADED_DATA -> {}
        }
    }

    private fun handleConfigData(config: ServerConfig?) {
        Log.d(TAG, "handleConfigData")
        serverConfig = config
        Log.d(TAG, "Config ${config?.toString()}")
        val buildTimestamp = config?.buildTimestamp
        Log.d(TAG, "buildTimestamp: $buildTimestamp")
//        doorListener?.remove()
        if (buildTimestamp.isNullOrEmpty()) {
//            loadingState = LoadingState.NO_CONFIG
            return
//        } else {
//            loadingState = LoadingState.LOADING_DATA
        }
//        Log.d(TAG, "Listening to events for buildTimestamp: $buildTimestamp")
        val eventRef = db.collection("eventsCurrent").document(buildTimestamp)
//        doorListener = eventRef.addSnapshotListener { snapshot, e ->
//            if (e != null) {
//                Log.w(TAG, "Event listener failed.", e)
//                return@addSnapshotListener
//            }
//            if (snapshot != null && snapshot.exists()) {
//                val doorStatus = snapshot?.toDoorData() ?: DoorData(message = getString(R.string.empty_data))
//                handleDoorChanged(doorStatus)
//                loadingState = LoadingState.LOADED_DATA
//            }
//        }
        doorViewModel.setDoorStatusDocumentReference(eventRef)
        registerDoorOpenNotifications(buildTimestamp)
        updateButtonUI()
    }

    private fun updateButtonUI() {
        val config = serverConfig
        val currentUser = auth.currentUser
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

    private fun getStatusTitleAndColor(doorData: DoorData, context: Context): Pair<String, Int> {
        Log.d(TAG, "getStatusTitleAndColor")
        return when (doorData.state) {
            null -> Pair(
                "Unknown Status",
                context.getColor(R.color.color_door_error)
            )
            DoorState.UNKNOWN -> Pair(
                "Unknown Status",
                context.getColor(R.color.color_door_error)
            )
            DoorState.CLOSED -> Pair(
                "Door Closed",
                context.getColor(R.color.color_door_closed)
            )
            DoorState.OPENING -> Pair(
                "Opening...",
                context.getColor(R.color.color_door_moving)
            )
            DoorState.OPENING_TOO_LONG -> Pair(
                "Check door",
                context.getColor(R.color.color_door_error)
            )
            DoorState.OPEN -> Pair(
                "Door Open",
                context.getColor(R.color.color_door_open)
            )
            DoorState.CLOSING -> Pair(
                "Closing...",
                context.getColor(R.color.color_door_moving)
            )
            DoorState.CLOSING_TOO_LONG -> Pair(
                "Check door",
                context.getColor(R.color.color_door_error)
            )
            DoorState.ERROR_SENSOR_CONFLICT -> Pair(
                "Error",
                context.getColor(R.color.color_door_error)
            )
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

private fun Map<*, *>.toServerConfig(): ServerConfig? {
    val body = this["body"] as? Map<*, *> ?: return null
    val buildTimestamp = body["buildTimestamp"] as? String?
    val remoteButtonPushKey = body["remoteButtonPushKey"] as? String?
    val remoteButtonBuildTimestamp = body["remoteButtonBuildTimestamp"] as? String?
    val host = body["host"] as? String?
    val path = body["path"] as? String?
    val remoteButtonEnabled = body["remoteButtonEnabled"] as? Boolean ?: false
    val remoteButtonAuthorizedEmails = (body["remoteButtonAuthorizedEmails"] as? ArrayList<String>)?.toTypedArray()
    return ServerConfig(
        buildTimestamp = buildTimestamp,
        remoteButtonPushKey = remoteButtonPushKey,
        remoteButtonBuildTimestamp =remoteButtonBuildTimestamp,
        host = host,
        path = path,
        remoteButtonEnabled = remoteButtonEnabled,
        remoteButtonAuthorizedEmails = remoteButtonAuthorizedEmails
    )
}