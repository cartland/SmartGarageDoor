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
import com.chriscartland.garage.MainActivity
import com.chriscartland.garage.R
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorDataAge
import com.chriscartland.garage.model.DoorDisplayInfo
import com.chriscartland.garage.model.DoorState
import com.chriscartland.garage.model.Loading

@BindingAdapter("app:doorStatusTitle")
fun doorStatusTitle(view: TextView, loadingDoor: Loading<DoorData>) {
    val doorDisplayInfo = DoorDisplayInfo.fromLoadingDoorData(view.context, loadingDoor)
    view.setBackgroundColor(doorDisplayInfo.color)
    view.text = doorDisplayInfo.status
}

@BindingAdapter("app:checkInAge")
fun checkInAge(view: TextView, age: DoorDataAge?) {
    val ageSeconds = age?.ageSeconds ?: 0
    val durationString = String.format("%d:%02d:%02d", ageSeconds / 3600, (ageSeconds % 3600) / 60, (ageSeconds % 60));
    view.text = view.context.getString(R.string.time_since_last_check_in, durationString)

    val warning = ageSeconds > MainActivity.CHECK_IN_THRESHOLD_SECONDS
    if (warning) {
        view.setBackgroundColor(view.context.getColor(R.color.color_door_error))
    } else {
        view.setBackgroundColor(view.context.getColor(R.color.black))
    }
}

@BindingAdapter("app:changeAge")
fun changeAge(view: TextView, age: DoorDataAge?) {
    val ageSeconds = age?.ageSeconds ?: 0
    val durationString = String.format("%d:%02d:%02d", ageSeconds / 3600, (ageSeconds % 3600) / 60, (ageSeconds % 60));
    view.text = view.context.getString(R.string.time_since_last_change, durationString)

    val doorData = age?.doorData
    val notClosed = doorData?.state != DoorState.CLOSED
    val oldData = ageSeconds > MainActivity.DOOR_NOT_CLOSED_THRESHOLD_SECONDS
    val warning = notClosed && oldData
    if (warning) {
        view.setBackgroundColor(view.context.getColor(R.color.color_door_error))
    } else {
        view.setBackgroundColor(view.context.getColor(R.color.black))
    }
}
