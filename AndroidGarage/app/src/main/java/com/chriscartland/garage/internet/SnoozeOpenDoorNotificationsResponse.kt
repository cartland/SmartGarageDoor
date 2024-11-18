/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// {"currentEventTimestampSeconds":1731877851,"snoozeRequestSeconds":1731892276,"snoozeDuration":"0h","snoozeEndTimeSeconds":1731892276,"FIRESTORE_databaseTimestamp":{"_seconds":1731892276,"_nanoseconds":370000000},"FIRESTORE_databaseTimestampSeconds":1731892276}
@JsonClass(generateAdapter = true)
data class SnoozeOpenDoorNotificationsResponse(
    @Json(name = "currentEventTimestampSeconds") val currentEventTimestampSeconds: Long?,
    @Json(name = "snoozeRequestSeconds") val snoozeRequestSeconds: Long?,
    @Json(name = "snoozeDuration") val snoozeDuration: String?,
    @Json(name = "snoozeEndTimeSeconds") val snoozeEndTimeSeconds: Long?,
    @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp?,
    @Json(name = "FIRESTORE_databaseTimestampSeconds") val firestoreDatabaseTimestampSeconds: Long?,
    @Json(name = "error") val error: String?,
) {
    @JsonClass(generateAdapter = true)
    data class FirestoreDatabaseTimestamp(
        @Json(name = "_seconds") val seconds: Long?,
        @Json(name = "_nanoseconds") val nanoseconds: Long?,
    )
}
