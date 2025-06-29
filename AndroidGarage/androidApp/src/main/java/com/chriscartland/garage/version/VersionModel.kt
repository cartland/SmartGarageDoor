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

package com.chriscartland.garage.version

import android.content.Context
import android.os.Build

data class AppVersion(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
) {
    override fun toString(): String = "$packageName-$versionCode-$versionName"
}

fun Context.AppVersion(): AppVersion = AppVersion(
    packageName = packageName,
    versionCode =
    packageManager
        .getPackageInfo(
            packageName,
            0,
        ).let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                it.versionCode.toLong()
            }
        },
    versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "",
)
