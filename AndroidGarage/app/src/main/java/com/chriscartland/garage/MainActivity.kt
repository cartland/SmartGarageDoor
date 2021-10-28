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
import android.content.IntentSender
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.chriscartland.garage.adapter.EventAdapter
import com.chriscartland.garage.databinding.ActivityMainBinding
import com.chriscartland.garage.model.LoadingState
import com.chriscartland.garage.model.ServerConfig
import com.chriscartland.garage.repository.updateOpenDoorFcmSubscription
import com.chriscartland.garage.viewmodel.DoorViewModel
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var doorViewModel: DoorViewModel
    private lateinit var eventHistoryAdapter: EventAdapter

    private val h: Handler = Handler(Looper.getMainLooper())
    private var runEachSecond: Runnable? = null
    private var runEachMinute: Runnable? = null
    private var buttonRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        setContentView(binding.root)

        val recyclerView = findViewById<RecyclerView>(R.id.event_history_list)
        recyclerView.setHasFixedSize(true)
        eventHistoryAdapter = EventAdapter(this, listOf())
        recyclerView.adapter = eventHistoryAdapter

        doorViewModel = ViewModelProvider(this).get(DoorViewModel::class.java)
        binding.doorViewModel = doorViewModel
        doorViewModel.doorData.observe(this, Observer {
            doorViewModel.hideProgressBar()
        })
        doorViewModel.eventHistory.observe(this, { eventHistory ->
            doorViewModel.hideProgressBar()
            eventHistoryAdapter.items = eventHistory
            eventHistoryAdapter.notifyDataSetChanged()
        })
        doorViewModel.loadingConfig.observe(this, Observer { loadingConfig ->
            val configData = loadingConfig.data
            val state = loadingConfig.loading
            Log.d(TAG, "configData: ${configData}")
            when (state) {
                LoadingState.NO_DATA -> {}
                LoadingState.LOADING_DATA -> {}
                LoadingState.LOADED_DATA -> {
                    val buildTimestamp = configData?.buildTimestamp ?: return@Observer
                    updateOpenDoorFcmSubscription(
                        this,
                        buildTimestamp
                    )
                }
            }
        })
        doorViewModel.firebaseUser.observe(this, Observer {
            Log.d(TAG, "firebaseUser: ${it?.email}")
            val signedIn = (it != null)
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            with (sharedPref.edit()) {
                putBoolean(SIGNED_IN_KEY, signedIn)
                apply()
            }
        })
        doorViewModel.enableRemoteButton()

        // One Tap Sign-In configuration.
        checkSignInConfiguration(TAG, this)
        val googleClientIdForWeb = getString(R.string.web_client_id)
        doorViewModel.oneTapSignInClient = Identity.getSignInClient(this)
        doorViewModel.oneTapSignInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(googleClientIdForWeb)
                    .setFilterByAuthorizedAccounts(false)
                    .build())
            .setAutoSelectEnabled(true)
            .build()
        signIn(clicked = false)
        updateEverySecond()
        updateEveryMinute()
    }

    private fun updateEverySecond() {
        // Remove any previously running callbacks.
        runEachSecond?.let {
            h.removeCallbacks(it)
        }
        // This callback will be run every second.
        runEachSecond = object : Runnable {
            override fun run() {
                // Pass the current time to the ViewModel.
                val now = Date()
                doorViewModel.onUpdateTimeSecond(now)
                h.postDelayed(this, 1000)
            }
        }
        // Run it now to start the cycle.
        runEachSecond?.let {
            it.run()
        }
    }

    private fun updateEveryMinute() {
        // Remove any previously running callbacks.
        runEachMinute?.let {
            h.removeCallbacks(it)
        }
        // This callback will be run every second.
        runEachMinute = object : Runnable {
            override fun run() {
                // Pass the current time to the ViewModel.
                val now = Date()
                eventHistoryAdapter.notifyDataSetChanged()
                h.postDelayed(this, 1000)
            }
        }
        // Run it now to start the cycle.
        runEachMinute?.let {
            it.run()
        }
    }

    fun onSignInClicked(view: View) {
        Log.d(TAG, "onSignInClicked")
        signIn(clicked = true)
    }

    fun signIn(clicked: Boolean) {
        checkSignInConfiguration(TAG, this)
        if (!clicked) {
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            val signedIn = sharedPref.getBoolean(SIGNED_IN_KEY, false)
            if (signedIn) {
                val user = Firebase.auth.currentUser
                doorViewModel.firebaseUser.value = user
                return
            }
        }
        if (!(clicked || doorViewModel.showOneTapUI.value == true)) {
            Log.d(TAG, "signIn: Skipping sign in, not clicked, and showOneTapUI is not true")
            val user = Firebase.auth.currentUser
            doorViewModel.firebaseUser.value = user
            return
        }
        val signInClient = doorViewModel.oneTapSignInClient ?: return
        val signInRequest = doorViewModel.oneTapSignInRequest ?: return
        Log.d(TAG, "beginSignIn")
        signInClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    startIntentSenderForResult(
                        result.pendingIntent.intentSender, RC_ONE_TAP_SIGN_IN,
                        null, 0, 0, 0, null)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                // No saved credentials found. Launch the One Tap sign-up flow, or
                // do nothing and continue presenting the signed-out UI.
                Log.d(TAG, e.localizedMessage)
            }
    }

    fun onSignOutClicked(view: View) {
        Log.d(TAG, "onSignOutClicked")
        doorViewModel.signOut()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult")
        when (requestCode) {
            RC_ONE_TAP_SIGN_IN -> {
                doorViewModel.handleOneTapSignIn(this, data)
            }
        }
    }

    fun onPushButton(view: View) {
        Log.d(TAG, "onPushButton")
        val config: ServerConfig? = doorViewModel.loadingConfig.value?.data
        if (config == null) {
            Log.e(TAG, "Cannot push button without server configuration")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.button_confirmation_title)
            .setMessage(R.string.button_confirmation_message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.confirm_push_button) { dialog, whichButton ->
                pushRemoteButton(this, config)
            }
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
        doorViewModel.showProgressBar()
        doorViewModel.disableRemoteButton()
        buttonRunnable?.let {
            h.removeCallbacks(it)
        }
        buttonRunnable = object : Runnable {
            override fun run() {
                doorViewModel.enableRemoteButton()
                doorViewModel.hideProgressBar()
            }
        }
        buttonRunnable?.let {
            val buttonDelay = 30 * 1000L // 30 seconds.
            h.postDelayed(it, buttonDelay)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        doorViewModel.refreshData()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        runEachSecond?.let {
            h.removeCallbacks(it)
        }
        buttonRunnable?.let {
            h.removeCallbacks(it)
        }
        doorViewModel.enableRemoteButton()
        doorViewModel.hideProgressBar()
    }

    companion object {
        val TAG: String = MainActivity::class.java.simpleName

        const val RC_ONE_TAP_SIGN_IN = 1
        const val SIGNED_IN_KEY = "com.chriscartland.garage.SIGNED_IN_KEY"
    }
}
