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

package com.chriscartland.garage.fcm

sealed class DoorFcmState {
    data object Unknown : DoorFcmState()

    data object NotRegistered : DoorFcmState()

    data class Registered(
        val topic: DoorFcmTopic,
    ) : DoorFcmState()
}

@JvmInline
value class DoorFcmTopic(
    val string: String,
)

fun String.toFcmTopic(): DoorFcmTopic = DoorFcmTopic(toDoorOpenFcmTopic())

private fun String.toDoorOpenFcmTopic(): String {
    val re = Regex("[^a-zA-Z0-9-_.~%]")
    val filtered = re.replace(this, ".")
    return "door_open-$filtered"
}
