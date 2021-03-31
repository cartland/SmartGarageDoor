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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.chriscartland.garage.databinding.ActivityMainBinding
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val db = Firebase.firestore
    private var configListener: ListenerRegistration? = null
    private var doorListener: ListenerRegistration? = null

    private var loadingState = LoadingState.DEFAULT
        set(value) {
            field = value
            onLoadingStateChanged(field)
        }

    enum class LoadingState {
        DEFAULT,
        LOADING_CONFIG,
        NO_CONFIG,
        LOADING_DATA,
        LOADED_DATA
    }

    private val h: Handler = Handler(Looper.getMainLooper())
    private var checkInRunnable: Runnable? = null
    private var changeRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

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
                val buildTimestamp = data?.fromConfigDataToBuildTimestamp()
                handleConfigData(buildTimestamp)
            } else {
                Log.d(TAG, "Config data: null")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        packageManager.getPackageInfo(packageName, 0).let {
            val textView = binding.versionCodeTextView
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                textView.text = getString(
                    R.string.version_code_string,
                    it.versionName,
                    it.longVersionCode
                )
            } else {
                textView.text = getString(
                    R.string.version_code_string,
                    it.versionName,
                    it.versionCode
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        configListener?.remove()
        doorListener?.remove()
        checkInRunnable?.let {
            h.removeCallbacks(it)
        }
        changeRunnable?.let {
            h.removeCallbacks(it)
        }
    }

    private fun onLoadingStateChanged(state: LoadingState) {
        Log.d(TAG, "onLoadingStateChanged: ${state.name}");
        when (state) {
            LoadingState.DEFAULT -> {}
            LoadingState.NO_CONFIG -> {
                handleDoorChanged(Door(message = getString(R.string.missing_config)))
            }
            LoadingState.LOADING_CONFIG -> {}
            LoadingState.LOADING_DATA -> {}
            LoadingState.LOADED_DATA -> {}
        }
    }

    private fun handleConfigData(buildTimestamp: String?) {
        Log.d(TAG, "buildTimestamp: $buildTimestamp")
        doorListener?.remove()
        if (buildTimestamp.isNullOrEmpty()) {
            loadingState = LoadingState.NO_CONFIG
            return
        } else {
            loadingState = LoadingState.LOADING_DATA
        }
        Log.d(TAG, "Listening to events for buildTimestamp: $buildTimestamp")
        val eventRef = db.collection("eventsCurrent").document(buildTimestamp)
        doorListener = eventRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Event listener failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data as Map<*, *>?
                val doorStatus = data?.toDoorStatus() ?: Door(message = getString(R.string.empty_data))
                handleDoorChanged(doorStatus)
                loadingState = LoadingState.LOADED_DATA
            }
        }
    }

    private fun handleDoorChanged(doorStatus: Door) {
        updateStatusTitle(doorStatus)
        updateStatusMessage(doorStatus)
        updateLastCheckInTime(doorStatus)
        updateLastChangeTime(doorStatus)
        updateTimeSinceLastCheckIn(doorStatus)
        updateTimeSinceLastChange(doorStatus)
    }

    private fun updateStatusTitle(door: Door) {
        val data = getStatusTitleAndColor(door, this)
        val textView = binding.statusTitle
        textView.text = data.first
        textView.setBackgroundColor(data.second)
    }

    private fun updateStatusMessage(door: Door) {
        val textView = binding.statusMessage
        textView.text = door.message
    }

    private fun updateLastCheckInTime(door: Door) {
        val lastCheckInTime = door.lastCheckInTimeSeconds
        val textView = binding.lastCheckInTime
        if (lastCheckInTime != null) {
            val lastCheckInTimeString =
                DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastCheckInTime * 1000))
            textView.text = getString(R.string.last_check_in_time, lastCheckInTimeString)
        } else {
            textView.text = ""
        }
    }

    private fun updateTimeSinceLastCheckIn(door: Door) {
        val lastCheckInTime = door.lastCheckInTimeSeconds
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

    private fun updateLastChangeTime(door: Door) {
        val lastChangeTime = door.lastChangeTimeSeconds
        val textView = binding.lastChangeTime
        if (lastChangeTime != null) {
            val lastChangeTimeString =
                DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(lastChangeTime * 1000))
            textView.text = getString(R.string.last_change_time, lastChangeTimeString)
        } else {
            textView.text = null
        }
    }

    private fun updateTimeSinceLastChange(door: Door) {
        val lastChangeTime = door.lastChangeTimeSeconds
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
                if (door.state != DoorState.CLOSED && s > DOOR_NOT_CLOSED_THRESHOLD_SECONDS) {
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

    private fun getStatusTitleAndColor(door: Door, context: Context): Pair<String, Int> {
        return when (door.state) {
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
    }
}

private fun Map<*, *>.fromConfigDataToBuildTimestamp(): String? {
    val body = this.get("body") as? Map<*, *>
    return body?.get("buildTimestamp") as? String?
}

private fun Map<*, *>.toDoorStatus(): Door {
    val currentEvent = this["currentEvent"] as? Map<*, *>
    val type = currentEvent?.get("type") as? String ?: ""
    val state = try {
        DoorState.valueOf(type)
    } catch (e: IllegalArgumentException) {
        DoorState.UNKNOWN
    }
    val message = currentEvent?.get("message") as? String ?: ""
    val timestampSeconds = currentEvent?.get("timestampSeconds") as? Long?
    val lastCheckInTime = this["FIRESTORE_databaseTimestampSeconds"] as? Long?
    return Door(
        state = state,
        message = message,
        lastChangeTimeSeconds = timestampSeconds,
        lastCheckInTimeSeconds = lastCheckInTime
    )
}
