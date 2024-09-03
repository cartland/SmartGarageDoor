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

package com.chriscartland.garage

import android.content.Context
import android.util.Log

/**
 * Log a warning if the sign-in configuration is not correct.
 */
fun checkSignInConfiguration(TAG: String, context: Context) {
    val googleClientIdForWeb = context.getString(R.string.web_client_id)
    if (googleClientIdForWeb == Constants.INCORRECT_WEB_CLIENT_ID) {
        Log.w(TAG, "The web client ID matches the INCORRECT_WEB_CLIENT_ID. " +
                "One Tap Sign-In with Google will not work. " +
                "Update the web client ID to be used with setServerClientId(). " +
                "https://developers.google.com/identity/one-tap/android/get-saved-credentials " +
                "Create a web client ID in this Google Cloud Console " +
                "https://console.cloud.google.com/apis/credentials")
    }
}
