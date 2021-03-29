package com.chriscartland.garage

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
        binding.statusTitle.setBackgroundColor(
            userMessages[type]?.get("backgroundColor") as? Int ?: getColor(R.color.color_door_error)
        )
        binding.statusMessage.text = message

        if (lastCheckInTime != null) {
            val lastCheckInTimeString = DateFormat.format("yyyy-MM-dd hh:mm:ss a",  Date(lastCheckInTime * 1000))
            binding.lastCheckInTime.text = "Last check-in: $lastCheckInTimeString"
            updateLastCheckInTime(lastCheckInTime)
        } else {
            binding.lastCheckInTime.text = ""
            updateLastCheckInTime(null)
        }
        if (timestampSeconds != null) {
            val lastChangeTimeString = DateFormat.format("yyyy-MM-dd hh:mm:ss a", Date(timestampSeconds * 1000))
            binding.lastChangeTime.text = "Last change: $lastChangeTimeString"
            updateLastChangeTime(timestampSeconds)
        } else {
            binding.lastChangeTime.text = null
            updateLastChangeTime(null)
        }
    }

    val h: Handler = Handler(Looper.getMainLooper())
    var checkInRunnable: Runnable? = null
    var changeRunnable: Runnable? = null

    private fun updateLastCheckInTime(lastCheckInTime: Long?) {
        if (lastCheckInTime == null) {
            binding.timeSinceLastCheckIn.text = ""
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
                val s = if ((now.time / 1000) - lastCheckInTime > 0) {
                    (now.time / 1000) - lastCheckInTime
                } else {
                    0
                }
                val timeSinceLastCheckInString =
                    String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
                binding.timeSinceLastCheckIn.text =
                    "Time since last check-in: $timeSinceLastCheckInString"
                h.postDelayed(this, 1000)
            }
        }
        checkInRunnable?.let {
            it.run()
            h.postDelayed(it, 1000)
        }
    }

    private fun updateLastChangeTime(lastChangeTime: Long?) {
        if (lastChangeTime == null) {
            binding.timeSinceLastChange.text = ""
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
                val s = if ((now.time / 1000) - lastChangeTime > 0) {
                    (now.time / 1000) - lastChangeTime
                } else {
                    0
                }
                val timeSinceLastChangeString = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
                binding.timeSinceLastChange.text = "Time since last change: $timeSinceLastChangeString"
                h.postDelayed(this, 1000)
            }
        }
        changeRunnable?.let {
            it.run()
            h.postDelayed(it, 1000)
        }

    }

    companion object {
        val TAG = MainActivity::class.java.simpleName
    }
}