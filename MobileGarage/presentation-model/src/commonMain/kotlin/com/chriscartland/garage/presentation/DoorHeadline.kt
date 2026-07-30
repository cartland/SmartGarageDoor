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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorPosition

/**
 * What the Status card's headline says, as one of six answers.
 *
 * Nine [DoorPosition]s collapse to six headlines: the anomalous variants read
 * the same as their normal counterparts, and surface what makes them anomalous
 * in the [DoorWarning] chip below instead. A door that has been opening too
 * long is still, to the person reading the card, *opening*.
 *
 * Which positions collapse together is a product decision, and it was being
 * made twice — a nine-arm `when` on Android, a six-case `switch` on iOS. They
 * agreed, but nothing held them to it: adding a tenth position would have
 * compiled on both sides only after two independent edits, and a disagreement
 * between them would show up as the two apps naming the same door differently.
 */
enum class DoorHeadline {
    OPEN,
    CLOSED,
    OPENING,
    CLOSING,
    UNKNOWN,
    SENSOR_CONFLICT,
}

/** Collapses a [DoorPosition] to the headline the Status card shows for it. */
object DoorHeadlineMapper {
    fun forPosition(position: DoorPosition): DoorHeadline =
        when (position) {
            // The misaligned/too-long variants deliberately share a headline
            // with their normal counterpart; the difference is the warning chip.
            DoorPosition.OPEN, DoorPosition.OPEN_MISALIGNED -> DoorHeadline.OPEN
            DoorPosition.CLOSED -> DoorHeadline.CLOSED
            DoorPosition.OPENING, DoorPosition.OPENING_TOO_LONG -> DoorHeadline.OPENING
            DoorPosition.CLOSING, DoorPosition.CLOSING_TOO_LONG -> DoorHeadline.CLOSING
            DoorPosition.UNKNOWN -> DoorHeadline.UNKNOWN
            DoorPosition.ERROR_SENSOR_CONFLICT -> DoorHeadline.SENSOR_CONFLICT
        }
}
