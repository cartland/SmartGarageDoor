/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

/**
 * The server's answer to "is this direction actionable right now?"
 *
 * The server judges the same table the local gate does, plus check-in
 * staleness, which the watch has no way to judge for itself. Asking is
 * additive: a command must pass the local gate AND this one, so consulting
 * the server can only ever refuse something, never permit something the
 * client would have refused on its own.
 *
 * Only VOICE asks. A spoken sentence names a direction; a button tap does
 * not, and the tap path never consults this. See
 * `FirebaseServer/src/functions/http/DoorCommand.ts`.
 */
data class DoorCommandVerdict(
    val accepted: Boolean,
    val rejection: DoorCommandRejection?,
    /** The projected state the server judged against, for diagnosis. */
    val doorState: String,
    /** True when the server considered the device reading too old to trust. */
    val checkInStale: Boolean,
)

/**
 * Why the server refused. Deliberately one-to-one with the server's own
 * `DoorCommandRejection` (`FirebaseServer/src/controller/DoorCommandGate.ts`)
 * and, in turn, with the client's `VoiceCommandIgnoreReason` — so a server
 * refusal is worded with the strings both surfaces already have, and no new
 * copy had to be written for it.
 *
 * [UNRECOGNIZED] is the deny-by-default landing spot for a reason a newer
 * server knows about and this build does not. It must keep meaning "refused",
 * never "allowed": a client that cannot understand the answer has not been
 * given permission.
 */
enum class DoorCommandRejection {
    ALREADY_OPEN,
    ALREADY_CLOSED,
    DOOR_MOVING,
    DOOR_STUCK,
    DOOR_STATE_UNKNOWN,
    UNRECOGNIZED,
}
