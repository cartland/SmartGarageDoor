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

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class RemoteButtonPushResponse(
    @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp?,
    @Json(name = "buildTimestamp") val buildTimestamp: String?,
    @Json(name = "queryParams") val queryParams: QueryParams?,
    @Json(name = "session") val session: String?,
    @Json(name = "FIRESTORE_databaseTimestampSeconds") val firestoreDatabaseTimestampSeconds: Long?,
    @Json(name = "buttonAckToken") val buttonAckToken: String?,
    @Json(name = "body") val body: Any?, // Since the body is empty in this case, we use Any
    @Json(name = "email") val email: String?
) {
    @JsonClass(generateAdapter = true)
    data class FirestoreDatabaseTimestamp(
        @Json(name = "_seconds") val seconds: Long?,
        @Json(name = "_nanoseconds") val nanoseconds: Long?
    )

    @JsonClass(generateAdapter = true)
    data class QueryParams(
        @Json(name = "buildTimestamp") val buildTimestamp: String?,
        @Json(name = "buttonAckToken") val buttonAckToken: String?
    )
}
