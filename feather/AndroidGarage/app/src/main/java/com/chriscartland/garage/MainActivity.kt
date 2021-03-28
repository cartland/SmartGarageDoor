package com.chriscartland.garage

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.chriscartland.garage.databinding.ActivityMainBinding
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userMessages: Map<String, Map<String, *>>

    private val db = Firebase.firestore
    private var doorListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        initUserMessages()

        val configRef = db.collection("configCurrent").document("current")
        configRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Config listener failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data as Map<*, *>
                Log.d(TAG, "Config data: $data")
                handleConfigData(data)
            } else {
                Log.d(TAG, "Config data: null")
            }
        }
    }

    private fun handleConfigData(data: Map<*, *>) {
        val body = data.get("body") as? Map<*, *>
        val buildTimestamp = body?.get("buildTimestamp") as? String
        handleBuildTimestampChnaged(buildTimestamp)
    }

    private fun handleBuildTimestampChnaged(buildTimestamp: String?) {
        Log.d(TAG, "buildTimestamp: $buildTimestamp")
        doorListener?.remove()
        if (buildTimestamp == null) {
            binding.statusTitle.text = ""
            return
        }
        Log.d(TAG, "Listening to events for buildTimestamp: $buildTimestamp")
        val eventRef = db.collection("eventsCurrent").document(buildTimestamp)
        doorListener = eventRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Event listener failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data as Map<*, *>
                handleEventDataChanged(data)
            }
        }
    }

    private fun initUserMessages() {
        userMessages = mapOf(
                "UNKNOWN" to mapOf(
                        "text" to "Unknown Status",
                        "backgroundColor" to getColor(R.color.color_door_error)
                ),
                "CLOSED" to mapOf(
                        "text" to "Door Closed",
                        "backgroundColor" to getColor(R.color.color_door_closed)
                ),
                "OPENING" to mapOf(
                        "text" to "Opening...",
                        "backgroundColor" to getColor(R.color.color_door_moving)
                ),
                "OPENING_TOO_LONG" to mapOf(
                        "text" to "Check door",
                        "backgroundColor" to getColor(R.color.color_door_error)
                ),
                "OPEN" to mapOf(
                        "text" to "Door Open",
                        "backgroundColor" to getColor(R.color.color_door_open)
                ),
                "CLOSING" to mapOf(
                        "text" to "Closing...",
                        "backgroundColor" to getColor(R.color.color_door_moving)
                ),
                "CLOSING_TOO_LONG" to mapOf(
                        "text" to "Check door",
                        "backgroundColor" to getColor(R.color.color_door_error)
                ),
                "ERROR_SENSOR_CONFLICT" to mapOf(
                        "text" to "Error",
                        "backgroundColor" to getColor(R.color.color_door_error)
                )
        )
    }

    private fun handleEventDataChanged(data: Map<*, *>) {
        val lastCheckInTime = data?.get("FIRESTORE_databaseTimestampSeconds") as? Long
        val currentEvent = data?.get("currentEvent") as? Map<*, *>
        val type = currentEvent?.get("type") as? String
        val message = currentEvent?.get("message") as? String
        val timestampSeconds = currentEvent?.get("timestampSeconds") as? Long
        Log.d(TAG, "type: $type")
        Log.d(TAG, "message: $message")
        Log.d(TAG, "timestampSeconds: $timestampSeconds")
        binding.statusTitle.text = userMessages[type]?.get("text") as? String ?: "Unknown Status"
        getColor(R.color.color_door_error)
        binding.statusTitle.setBackgroundColor(
                userMessages[type]?.get("backgroundColor") as? Int ?: getColor(R.color.color_door_error)
        )
        binding.statusMessage.text = message
        binding.lastCheckInTime.text = lastCheckInTime.toString()
        binding.timeSinceLastCheckIn.text = "TODO"
        binding.lastChangeTime.text = timestampSeconds.toString()
        binding.timeSinceLastChange.text = "TODO"
    }

    companion object {
        val TAG = MainActivity::class.java.simpleName
    }
}