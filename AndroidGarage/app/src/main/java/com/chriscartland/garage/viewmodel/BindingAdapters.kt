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

package com.chriscartland.garage.viewmodel

import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.chriscartland.garage.R
import com.chriscartland.garage.model.DoorDataAge
import com.chriscartland.garage.model.DoorState

const val CHECK_IN_THRESHOLD_SECONDS = 60 * 15
const val DOOR_NOT_CLOSED_THRESHOLD_SECONDS = 60 * 15

@BindingAdapter("app:checkInAge")
fun checkInAge(view: TextView, age: DoorDataAge?) {
    val ageSeconds = age?.ageSeconds ?: 0
    val durationString = String.format("%02d:%02d", (ageSeconds % 3600) / 60, (ageSeconds % 60));
    view.text = view.context.getString(R.string.time_since_last_check_in, durationString)

    val warning = ageSeconds > CHECK_IN_THRESHOLD_SECONDS
    if (warning) {
        view.setBackgroundColor(view.context.getColor(R.color.color_door_error))
    } else {
        view.setBackgroundColor(view.context.getColor(R.color.transparent))
    }
}

@BindingAdapter("app:changeAge")
fun changeAge(view: TextView, age: DoorDataAge?) {
    val ageSeconds = age?.ageSeconds ?: 0
    val d = (ageSeconds) / 86400
    val h = (ageSeconds % 86400) / 3600
    val m = (ageSeconds % 3600) / 60
    val s = (ageSeconds % 60)
    view.text = when {
        ageSeconds < 2 -> {
            view.context.getString(R.string.time_since_last_change_1_second)
        }
        ageSeconds < 60 -> {
            view.context.getString(R.string.time_since_last_change_seconds, s)
        }
        ageSeconds < 60 * 2 -> {
            view.context.getString(R.string.time_since_last_change_1_minute)
        }
        ageSeconds < 60 * 60 -> {
            view.context.getString(R.string.time_since_last_change_minutes, m)
        }
        ageSeconds < 60 * 60 * 2 -> {
            view.context.getString(R.string.time_since_last_change_1_hour)
        }
        ageSeconds < 60 * 60 * 24 -> {
            view.context.getString(R.string.time_since_last_change_hours, h)
        }
        ageSeconds < 60 * 60 * 24 * 2 -> {
            view.context.getString(R.string.time_since_last_change_1_day)
        }
        else -> {
            view.context.getString(R.string.time_since_last_change_days, d)
        }
    }
}
