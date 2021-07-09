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
import com.android.volley.Response
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

    val doorData = MutableLiveData<DoorData>()

    fun refreshDoorData(context: Context, buildTimestamp: String) {
        Log.d(TAG, "refreshDoorData")
        executor.execute {
            val url = buildUri(buildTimestamp).toString()
            fetchJSONFromURL(context, url)
        }
    }

    // https://us-central1-PROJECT-ID.cloudfunctions.net/currentEventData\?session\=\&buildTimestamp\=Sat%20Mar%2013%2014%3A45%3A00%202021
    private fun buildUri(buildTimestamp: String): Uri {
        Log.d(TAG, "buildUri $buildTimestamp")
        val builder = Uri.Builder()
            .scheme(Constants.SCHEME)
            .authority(Constants.AUTHORITY)
            .appendPath(Constants.CURRENT_EVENT_DATA_PATH)
            .appendQueryParameter(Constants.SESSION_PARAM_KEY, "")
            .appendQueryParameter(Constants.BUILD_TIMESTAMP_PARAM_KEY, buildTimestamp)
        return builder.build()
    }

    private fun fetchJSONFromURL(context: Context, url: String) {
        Log.d(TAG, "fetchJSONFromURL $url")
        val refreshTrace = Firebase.performance.newTrace(TRACE_FETCH_JSON_REQUEST)
        refreshTrace.start()

        val queue = Volley.newRequestQueue(context)
        val stringRequest = StringRequest(
            Request.Method.GET,
            url,
            Response.Listener<String> { jsonString ->
                refreshTrace.stop()
                Log.d(TAG, "Network request success")
                val map = jsonString.toMap()
                if (map == null) {
                    Log.e(TAG, "refreshDoorData: Could not parse JSON from network")
                    return@Listener
                }
                Log.d(TAG, "refreshDoorData: JSON map from network $map")
                val data = map?.currentEventDataToDoorData()
                if (data == null) {
                    Log.e(TAG, "refreshDoorData: Data is not valid")
                    return@Listener
                }
                Log.d(TAG, "refreshDoorData: Posting doorData: $data")
                doorData.postValue(data)
            },
            Response.ErrorListener {
                refreshTrace.stop()
                Log.e(TAG, "Network error: ${it.message ?: it.localizedMessage}")
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
