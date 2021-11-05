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
 */

package com.chriscartland.garage.adapter

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chriscartland.garage.Constants.Companion.CHECK_IN_THRESHOLD_SECONDS
import com.chriscartland.garage.R
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorDisplayInfo
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import com.chriscartland.garage.model.DoorState
import kotlin.math.roundToInt


class EventAdapter(
    private val context: Context,
    var items: List<DoorData>
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val timeSinceLastChange: TextView = view.findViewById(R.id.time_since_last_change)
        val card: MaterialCardView = view.findViewById(R.id.card_view)
        val eventIcon: ImageView = view.findViewById(R.id.event_icon)
        val titleText: TextView = view.findViewById(R.id.event_title_text)
        val messageText: TextView = view.findViewById(R.id.event_message_text)
        val dateText: TextView = view.findViewById(R.id.event_date_text)
        val timeText: TextView = view.findViewById(R.id.event_time_text)
        val timeTextSeconds: TextView = view.findViewById(R.id.event_time_seconds_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.door_event, parent, false)
        return EventViewHolder(adapterLayout)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = items[position]
        val display = DoorDisplayInfo.fromDoorState(context, event.state)
        val lastChangeTime = event.lastChangeTimeSeconds ?: 0
        val now = Date()
        val ageSeconds = ((now.time / 1000) - lastChangeTime).coerceAtLeast(0)
        val timeSinceLastChange = timeSinceLastChangeString(context, ageSeconds)
        holder.timeSinceLastChange.text = timeSinceLastChange
        holder.eventIcon.setImageDrawable(display.icon)
        holder.titleText.text = display.status
        holder.messageText.text = event.message
        holder.dateText.text = simpleDate(event.lastChangeTimeSeconds)
        holder.timeText.text = simpleTime(event.lastChangeTimeSeconds)
        holder.timeTextSeconds.text = simpleTimeSeconds(event.lastChangeTimeSeconds)

        val showAge = shouldShowAge(
            proposedString = timeSinceLastChange,
            currentTimeMillis = now.time,
            currentItemPosition = position,
        )
        holder.timeSinceLastChange.visibility = if (showAge) View.VISIBLE else View.GONE

        val color = if (position == 0) {
            val checkInTime = event.lastCheckInTimeSeconds ?: 0
            val checkInAgeSeconds = ((now.time / 1000) - checkInTime).coerceAtLeast(0)
            val warning = checkInAgeSeconds > CHECK_IN_THRESHOLD_SECONDS
            if (warning) {
                context.getColor(R.color.color_door_error)
            } else {
                display.color
            }
        } else {
            context.getColor(R.color.color_stale_background)
        }
        holder.card.setCardBackgroundColor(color)

        val showFullSizeCard = shouldShowFullSizeCard(
            currentItemPosition = position
        )

        val smallTextSize = if (showFullSizeCard) {
            context.resources.getDimension(R.dimen.card_text_size_small_full)
        } else {
            context.resources.getDimension(R.dimen.card_text_size_small_mini)
        }
        holder.titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
        holder.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
        holder.dateText.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
        holder.timeTextSeconds.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)

        val largeTextSize = if (showFullSizeCard) {
            context.resources.getDimension(R.dimen.card_text_size_large_full)
        } else {
            context.resources.getDimension(R.dimen.card_text_size_large_mini)
        }
        holder.timeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, largeTextSize)

        val iconSize = if (showFullSizeCard) {
            context.resources.getDimension(R.dimen.status_icon_size_full).roundToInt()
        } else {
            context.resources.getDimension(R.dimen.status_icon_size_mini).roundToInt()
        }
        holder.eventIcon.updateLayoutParams {
            width = iconSize
            height = iconSize
        }
    }

    private fun shouldShowFullSizeCard(currentItemPosition: Int): Boolean {
        val item = items[currentItemPosition]
        if (currentItemPosition == 0) {
            return true
        }
        return when (item.state) {
            DoorState.UNKNOWN -> false
            DoorState.CLOSED -> true
            DoorState.OPENING -> false
            DoorState.OPENING_TOO_LONG -> true
            DoorState.OPEN -> true
            DoorState.OPEN_MISALIGNED -> true
            DoorState.CLOSING -> false
            DoorState.CLOSING_TOO_LONG -> true
            DoorState.ERROR_SENSOR_CONFLICT -> true
            null -> false
        }
    }

    private fun shouldShowAge(
        proposedString: String,
        currentTimeMillis: Long,
        currentItemPosition: Int,
    ): Boolean {
        return if (currentItemPosition == 0) {
            true
        } else {
            val previousItem = items[currentItemPosition - 1]
            val previousTime = previousItem.lastChangeTimeSeconds ?: 0
            val previousAgeSeconds = ((currentTimeMillis / 1000) - previousTime).coerceAtLeast(0)
            val previousAgeDisplayed = timeSinceLastChangeString(context, previousAgeSeconds)
            // Show age if it is different than previous age.
            proposedString != previousAgeDisplayed
        }
    }

    fun simpleDate(timestamp: Long?): String {
        if (timestamp == null) {
            return "--"
        }
        val date = Date(timestamp * 1000L)
        val format = SimpleDateFormat("MMM d", Locale.US)
        return format.format(date)
    }

    fun simpleTime(timestamp: Long?): String {
        if (timestamp == null) {
            return "--:--"
        }
        val date = Date(timestamp * 1000L)
        val format = SimpleDateFormat("H:mm", Locale.US)
        return format.format(date)
    }

    fun simpleTimeSeconds(timestamp: Long?): String {
        if (timestamp == null) {
            return ":--"
        }
        val date = Date(timestamp * 1000L)
        val format = SimpleDateFormat(":ss", Locale.US)
        return format.format(date)
    }

    override fun getItemCount() = items.size
}

fun Int.toPx(context: Context) = this * context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
