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

package com.chriscartland.garage.internet

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.android.volley.Request
<<<<<<< HEAD
import com.android.volley.Response
=======
>>>>>>> temp_remote/main
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.chriscartland.garage.AppExecutors
import com.chriscartland.garage.Constants
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorState
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executor

class RemoteDataSource private constructor(
    private val executor: Executor
) {

    val doorHistory = MutableLiveData<List<DoorData>>()

    fun refreshDoorHistory(context: Context, buildTimestamp: String) {
        Log.d(TAG, "refreshDoorHistory")
        executor.execute {
            val url = buildUri(
                buildTimestamp,
                Constants.EVENT_HISTORY_PATH,
                eventHistoryMaxCount = 100,
            ).toString()
            fetchJSONFromURL(context, url) { jsonString ->
                Log.d(TAG, "Network request success")
                val map = jsonString.toMap()
                if (map == null) {
                    Log.e(TAG, "refreshDoorHistory: Could not parse JSON from network")
                    return@fetchJSONFromURL
                }
                Log.d(TAG, "refreshDoorHistory: JSON map from network $map")
                val data = map.eventHistoryToDoorData()
                if (data == null) {
                    Log.e(TAG, "refreshDoorHistory: Data is not valid")
                    return@fetchJSONFromURL
                } else {
                    Log.d(TAG, "refreshDoorHistory: Posting doorData: $data")
                    doorHistory.postValue(data)
                    return@fetchJSONFromURL
                }
            }
        }
    }

    // https://us-central1-PROJECT-ID.cloudfunctions.net/PATH\?session\=\&buildTimestamp\=Sat%20Mar%2013%2014%3A45%3A00%202021
    private fun buildUri(
        buildTimestamp: String,
        path: String,
        eventHistoryMaxCount: Int? = null,
    ): Uri {
        Log.d(TAG, "buildUri $buildTimestamp")
        val builder = Uri.Builder()
            .scheme(Constants.SCHEME)
            .authority(Constants.AUTHORITY)
            .appendPath(path)
            .appendQueryParameter(Constants.SESSION_PARAM_KEY, "")
            .appendQueryParameter(Constants.BUILD_TIMESTAMP_PARAM_KEY, buildTimestamp)
        if (eventHistoryMaxCount != null) {
            builder.appendQueryParameter(
                Constants.EVENT_HISTORY_MAX_COUNT_PARAM_KEY,
                eventHistoryMaxCount.toString()
            )
        }
        return builder.build()
    }

    private fun fetchJSONFromURL(context: Context, url: String, callback: (String) -> Unit) {
        Log.d(TAG, "fetchJSONFromURL $url")
        val refreshTrace = Firebase.performance.newTrace(TRACE_FETCH_JSON_REQUEST)
        refreshTrace.start()

        val queue = Volley.newRequestQueue(context)
        val stringRequest = StringRequest(
            Request.Method.GET,
            url,
            { jsonString ->
                refreshTrace.stop()
                callback(jsonString)
            },
            { error ->
                refreshTrace.stop()
                Log.e(TAG, "Network error: ${error.message ?: error.localizedMessage}")
            })
        queue.add(stringRequest)
    }

    companion object {
        val TAG: String = RemoteDataSource::class.java.simpleName

        const val TRACE_FETCH_JSON_REQUEST: String = "TRACE_FETCH_JSON_REQUEST"

        @Volatile
        private var INSTANCE: RemoteDataSource? = null

        fun getInstance(executors: AppExecutors): RemoteDataSource =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteDataSource(executors.networkIO).also { INSTANCE = it }
            }
    }
}

fun String.toMap(): Map<*, *>? {
    return try {
        val jsonObj = JSONObject(this)
        jsonObj.toMap()
    } catch (e: JSONException) {
        Log.e(RemoteDataSource.TAG, "JSON String to Map error: ${e.message}")
        null
    }
}

fun JSONObject.toMap(): Map<String, *> = keys().asSequence().associateWith {
    when (val value = this[it]) {
        is JSONArray -> {
            val map = (0 until value.length()).associate { Pair(it.toString(), value[it]) }
            JSONObject(map).toMap().values.toList()
        }
        is JSONObject -> value.toMap()
        JSONObject.NULL -> null
        else            -> value
    }
}

fun Map<*, *>.currentEventDataToDoorData(): DoorData? {
    val data = this["currentEventData"] as? Map<*, *> ?: return null
    val currentEvent = data["currentEvent"] as? Map<*, *>
    val type = currentEvent?.get("type") as? String ?: ""
    val state = try {
        DoorState.valueOf(type)
    } catch (e: IllegalArgumentException) {
        DoorState.UNKNOWN
    }
    val message = currentEvent?.get("message") as? String ?: ""
    val timestampSeconds = (currentEvent?.get("timestampSeconds") as Int?)?.toLong()
    val lastCheckInTime = (currentEvent?.get("checkInTimestampSeconds") as Int?)?.toLong()
    return DoorData(
        state = state,
        message = message,
        lastChangeTimeSeconds = timestampSeconds,
        lastCheckInTimeSeconds = lastCheckInTime
    )
}

fun Map<*, *>.eventHistoryToDoorData(): List<DoorData>? {
    val eventHistory = this["eventHistory"] as? List<Map<*, *>> ?: return null
    val result = mutableListOf<DoorData>()
    for (event in eventHistory) {
        val currentEvent = event["currentEvent"] as? Map<*, *>
        val type = currentEvent?.get("type") as? String ?: ""
        val state = try {
            DoorState.valueOf(type)
        } catch (e: IllegalArgumentException) {
            DoorState.UNKNOWN
        }
        val message = currentEvent?.get("message") as? String ?: ""
        val timestampSeconds = (currentEvent?.get("timestampSeconds") as Int?)?.toLong()
        val lastCheckInTime = (currentEvent?.get("checkInTimestampSeconds") as Int?)?.toLong()

        val doorData = DoorData(
            state = state,
            message = message,
            lastChangeTimeSeconds = timestampSeconds,
            lastCheckInTimeSeconds = lastCheckInTime
        )
        result.add(doorData)
    }
    return result
}
