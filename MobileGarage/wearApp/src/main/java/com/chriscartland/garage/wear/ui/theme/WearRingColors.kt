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
 * One ring's palette: the faint track, the sweep that counts toward a commit,
 * and the fill that reports one.
 *
 * Brightness carries the progression *within* a scheme — [sweep] is dimmed so
 * [committed] has somewhere brighter to go — while hue carries which world the
 * ring belongs to. See [WearRingColors].
 */
data class WearRingColorScheme(
    /** Faint full-circle track under the sweep, drawn at a low alpha. */
    val track: Color,
    /** Progress toward the press: dimmed, so the commit has somewhere to go. */
    val sweep: Color,
    /** The press was submitted — commit bloom and the in-flight ring. */
    val committed: Color,
)

/**
 * Palettes for the hold-to-confirm ring, one per world it can be counting in.
 *
 * ## Why the real ring is grey and not themed
 *
 * These used to be `MaterialTheme.colorScheme.primary` / `.tertiary`. Wear
 * Material3's baseline palette resolves those to `Primary90` (#E9DDFF, pale
 * lavender) and `Tertiary90` (#FFDCC2, pale peach) — both visibly tinted, and
 * the peach in particular read as an orange warning ring at exactly the moment
 * the ring was reporting success.
 *
 * The ring is a progress instrument on a black OLED watch face, not a branded
 * surface, so the real one wants neutral greys. The door already owns the
 * screen's colour (green closed, red open); a tinted ring competes with the one
 * element whose hue actually carries meaning.
 *
 * ## Why the simulated ring IS tinted
 *
 * That reasoning inverts once a second, consequence-free surface exists. The
 * simulation's job is to be unmistakable, and hue is the signal that survives a
 * glance at a moving animation — a word can be missed while you are watching a
 * countdown, a colour cannot. [simulated] is therefore deliberately the one
 * ring in the app that is not white.
 *
 * Azure specifically, for the same reason the peach was rejected: it is the one
 * hue with no existing job here. Green and red belong to the door, amber would
 * read as a warning about the press itself, and white is the real ring. Blue
 * says only "this is the other one".
 */
object WearRingColors {
    /** The real press: neutral, authoritative. */
    val neutral = WearRingColorScheme(
        track = Color(0xFFFFFFFF),
        sweep = Color(0xFFDDDDDD),
        committed = Color(0xFFFFFFFF),
    )

    /** The rehearsal: same animation, unmistakably not the same ring. */
    val simulated = WearRingColorScheme(
        // The track stays white-at-low-alpha in both. It is the unlit part of
        // the dial rather than part of the message, and tinting it made the
        // whole circle read as a coloured object rather than as a ring filling.
        track = Color(0xFFFFFFFF),
        sweep = Color(0xFF6FB4D8),
        committed = Color(0xFF9CD8F5),
    )
}
