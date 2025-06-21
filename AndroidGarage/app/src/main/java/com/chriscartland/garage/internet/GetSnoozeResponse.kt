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

@JsonClass(generateAdapter = true)
data class GetSnoozeResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "snooze") val snooze: Snooze? = null,
    @Json(name = "error") val error: String? = null,
) {
    @JsonClass(generateAdapter = true)
    data class Snooze(
        @Json(name = "currentEventTimestampSeconds") val currentEventTimestampSeconds: Long? = null,
        @Json(name = "snoozeRequestSeconds") val snoozeRequestSeconds: Long? = null,
        @Json(name = "snoozeDuration") val snoozeDuration: String? = null,
        @Json(name = "snoozeEndTimeSeconds") val snoozeEndTimeSeconds: Long? = null,
        @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp? = null,
        @Json(name = "FIRESTORE_databaseTimestampSeconds") val firestoreDatabaseTimestampSeconds: Long? = null,
    ) {
        @JsonClass(generateAdapter = true)
        data class FirestoreDatabaseTimestamp(
            @Json(name = "_seconds") val seconds: Long? = null,
            @Json(name = "_nanoseconds") val nanoseconds: Long? = null,
        )
    }
}
