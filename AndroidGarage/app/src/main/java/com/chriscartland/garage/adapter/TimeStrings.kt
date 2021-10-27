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
import com.chriscartland.garage.R

fun timeSinceLastChangeString(context: Context, ageSeconds: Long): String {
    val d = (ageSeconds) / 86400
    val h = (ageSeconds % 86400) / 3600
    val m = (ageSeconds % 3600) / 60
    val s = (ageSeconds % 60)
    return when {
        ageSeconds < 2 -> {
            context.getString(R.string.time_since_last_change_1_second)
        }
        ageSeconds < 60 -> {
            context.getString(R.string.time_since_last_change_seconds, s)
        }
        ageSeconds < 60 * 2 -> {
            context.getString(R.string.time_since_last_change_1_minute)
        }
        ageSeconds < 60 * 15 -> {
            context.getString(R.string.time_since_last_change_minutes_generic)
        }
        ageSeconds < 60 * 30 -> {
            context.getString(R.string.time_since_last_change_15_minutes_generic)
        }
        ageSeconds < 60 * 45 -> {
            context.getString(R.string.time_since_last_change_30_minutes_generic)
        }
        ageSeconds < 60 * 60 -> {
            context.getString(R.string.time_since_last_change_45_minutes_generic)
        }
        ageSeconds < 60 * 60 * 2 -> {
            context.getString(R.string.time_since_last_change_1_hour)
        }
        ageSeconds < 60 * 60 * 24 -> {
            context.getString(R.string.time_since_last_change_hours, h)
        }
        ageSeconds < 60 * 60 * 24 * 2 -> {
            context.getString(R.string.time_since_last_change_1_day)
        }
        else -> {
            context.getString(R.string.time_since_last_change_days, d)
        }
    }
}