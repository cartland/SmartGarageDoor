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

package com.chriscartland.garage.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Renders the garage door for a given [DoorPosition].
 *
 * The modifier from the caller flows directly to the door composable —
 * no intermediate wrapper Boxes. The door applies its own aspect ratio
 * internally, so callers only need to provide size constraints.
 */
@Composable
fun GarageIcon(
    doorPosition: DoorPosition,
    modifier: Modifier = Modifier,
    static: Boolean = false,
    color: Color = Color.Blue,
) {
    when (doorPosition) {
        DoorPosition.UNKNOWN -> Midway(modifier = modifier, color = color)
        DoorPosition.CLOSED -> Closed(modifier = modifier, color = color)
        DoorPosition.OPENING -> Opening(modifier = modifier, static = static, color = color)
        DoorPosition.OPENING_TOO_LONG -> Midway(modifier = modifier, color = color)
        DoorPosition.OPEN -> Open(modifier = modifier, color = color)
        DoorPosition.OPEN_MISALIGNED -> Open(modifier = modifier, color = color)
        DoorPosition.CLOSING -> Closing(modifier = modifier, static = static, color = color)
        DoorPosition.CLOSING_TOO_LONG -> Midway(modifier = modifier, color = color)
        DoorPosition.ERROR_SENSOR_CONFLICT -> Midway(modifier = modifier, color = color)
    }
}

@Preview(showBackground = true)
@Composable
fun GarageIconPreview() {
    GarageIcon(
        doorPosition = DoorPosition.OPENING,
    )
}
