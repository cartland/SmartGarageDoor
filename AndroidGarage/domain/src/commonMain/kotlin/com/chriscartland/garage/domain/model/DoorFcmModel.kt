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

package com.chriscartland.garage.domain.model

fun String.toFcmTopic(): DoorFcmTopic = DoorFcmTopic(toDoorOpenFcmTopic())

private fun String.toDoorOpenFcmTopic(): String {
    val re = Regex("[^a-zA-Z0-9-_.~%]")
    val filtered = re.replace(this, ".")
    return "door_open-$filtered"
}

/** Topic prefix for the additive resolved-on-close door notification ("v2"). */
const val DOOR_RESOLVED_FCM_TOPIC_PREFIX = "door_open_v2-"

/**
 * Additive "v2" door topic for the resolved-on-close notification
 * (docs/RESOLVED_NOTIFICATION_PLAN.md). Carries DATA-ONLY messages; the new
 * build subscribes to it ADDITIVELY — the legacy `door_open-` subscription
 * (state-sync + warning) is left completely untouched, so old app builds are
 * unaffected. Must stay byte-identical to the server's
 * `buildTimestampToFcmTopicV2` (FcmTopic.ts); pinned by FcmTopicTest on both
 * sides. Distinct prefix from `door_open-` (the next char is `_`, not `-`), so
 * the two never collide in prefix routing.
 */
fun String.toDoorResolvedFcmTopic(): String {
    val re = Regex("[^a-zA-Z0-9-_.~%]")
    val filtered = re.replace(this, ".")
    return "$DOOR_RESOLVED_FCM_TOPIC_PREFIX$filtered"
}
