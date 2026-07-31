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

package com.chriscartland.garage.wear.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors for the hold-to-confirm ring.
 *
 * Deliberately NOT `MaterialTheme.colorScheme.primary` / `.tertiary`, which is
 * what these used to be. Wear Material3's baseline palette resolves those to
 * `Primary90` (#E9DDFF, pale lavender) and `Tertiary90` (#FFDCC2, pale peach) —
 * both visibly tinted, and the peach in particular read as an orange warning
 * ring at exactly the moment the ring is reporting success.
 *
 * The ring is a progress instrument on a black OLED watch face, not a branded
 * surface, so it wants neutral greys. The door already owns the screen's color
 * (green closed, red open); a tinted ring competes with the one element whose
 * hue actually carries meaning.
 *
 * Brightness, not hue, carries the ring's own progression: [sweep] is a dimmed
 * white that reads as "counting", [committed] is full white for the press that
 * really was sent.
 */
object WearRingColors {
    /** Faint full-circle track under the sweep, drawn at a low alpha. */
    val track = Color(0xFFFFFFFF)

    /** Progress toward the press: dimmed, so the commit has somewhere to go. */
    val sweep = Color(0xFFDDDDDD)

    /** The press was submitted — commit bloom and the in-flight ring. */
    val committed = Color(0xFFFFFFFF)
}
