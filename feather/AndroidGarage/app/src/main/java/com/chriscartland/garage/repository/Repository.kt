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

package com.chriscartland.garage.repository

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.chriscartland.garage.model.AppVersion

class Repository {

    val appVersion: MutableLiveData<AppVersion> = MutableLiveData()

    fun updatePackageVersion(packageManager: PackageManager, packageName: String) {
        Log.d(TAG, "updatePackageVersionUI")
        packageManager.getPackageInfo(packageName, 0).let {
            val newAppVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                AppVersion(
                    versionCode = it.longVersionCode,
                    versionName = it.versionName
                )
            } else {
                AppVersion(
                    versionCode = it.versionCode.toLong(),
                    versionName = it.versionName
                )
            }
            appVersion.value = newAppVersion
        }
    }

    companion object {
        val TAG: String = Repository::class.java.simpleName
    }
}