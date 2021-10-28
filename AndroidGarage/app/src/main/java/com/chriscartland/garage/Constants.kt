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

package com.chriscartland.garage

class Constants {
    companion object {
        // https://us-central1-PROJECT-ID.cloudfunctions.net/eventHistory?session=ABC&buildTimestamp=Sat%20Mar%2013%2014%3A45%3A00%202021
        const val SCHEME = "https"
        const val AUTHORITY = "us-central1-PROJECT-ID.cloudfunctions.net"
        const val CURRENT_EVENT_DATA_PATH = "currentEventData"
        const val EVENT_HISTORY_PATH = "eventHistory"

        const val SESSION_PARAM_KEY = "session"
        const val BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp"

        const val CHECK_IN_THRESHOLD_SECONDS = 60 * 15 // 15 minutes in seconds.

        // Before compiling this project, change the value of web_client_id in auth.xml.
        // This copy of the incorrect value allows us to log a warning if auth.xml has not changed.
        const val INCORRECT_WEB_CLIENT_ID = "123456789012-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz.apps.googleusercontent.com"
    }
}
