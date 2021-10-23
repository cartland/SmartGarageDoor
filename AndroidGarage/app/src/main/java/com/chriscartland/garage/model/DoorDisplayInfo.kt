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

package com.chriscartland.garage.model

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.chriscartland.garage.R

data class DoorDisplayInfo(
    val status: String,
    val color: Int,
    val icon: Drawable?
) {
    companion object {
        fun fromLoadingDoorData(context: Context, loadingDoor: Loading<DoorData>) : DoorDisplayInfo {
            return when (loadingDoor.loading) {
                LoadingState.NO_DATA -> DoorDisplayInfo(
                    status = context.getString(R.string.title_no_data),
                    color = context.getColor(R.color.color_door_error),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_unknown)
                )
                LoadingState.LOADING_DATA -> DoorDisplayInfo(
                    status = context.getString(R.string.title_loading),
                    color = context.getColor(R.color.color_door_error),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_unknown)
                )
                LoadingState.LOADED_DATA -> fromDoorState(context, loadingDoor.data?.state)
            }
        }

        fun fromDoorState(context: Context, doorState: DoorState?) : DoorDisplayInfo {
            return when (doorState) {
                DoorState.UNKNOWN -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_error),
                    color = context.getColor(R.color.color_door_error),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_unknown)
                )
                DoorState.CLOSED -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_closed),
                    color = context.getColor(R.color.color_door_closed),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_closed)
                )
                DoorState.OPENING -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_opening),
                    color = context.getColor(R.color.color_door_opening),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_moving)
                )
                DoorState.OPENING_TOO_LONG -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_opening_too_long),
                    color = context.getColor(R.color.color_door_error),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_unknown)
                )
                DoorState.OPEN -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_open),
                    color = context.getColor(R.color.color_door_open),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_open)
                )
                DoorState.OPEN_MISALIGNED -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_open_misaligned),
                    color = context.getColor(R.color.color_door_open),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_open)
                )
                DoorState.CLOSING -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_closing),
                    color = context.getColor(R.color.color_door_closing),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_moving)
                )
                DoorState.CLOSING_TOO_LONG -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_closing_too_long),
                    color = context.getColor(R.color.color_door_error),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_unknown)
                )
                DoorState.ERROR_SENSOR_CONFLICT -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_sensor_conflict),
                    color = context.getColor(R.color.color_door_error),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_unknown)
                )
                null -> DoorDisplayInfo(
                    status = context.getString(R.string.title_door_error),
                    color = context.getColor(R.color.color_door_error),
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_garage_simple_unknown)
                )
            }
        }
    }
}
