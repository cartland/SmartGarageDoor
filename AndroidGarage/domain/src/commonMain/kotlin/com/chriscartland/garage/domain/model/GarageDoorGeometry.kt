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

/**
 * Shared garage-door drawing geometry — the single source of truth for the
 * brand-locked door visualization rendered by **both** Android (Jetpack Compose
 * `GarageDoorCanvas.kt`) and iOS (SwiftUI `GarageDoorCanvas.swift`).
 *
 * All values are in a [VP]×[VP] design viewport; each platform's Canvas scales
 * uniformly to fit the available size. Per ADR-032 the door visualization is a
 * **Tier 1 (brand-locked)** surface — its shape *is* the brand — so these
 * constants must not drift between platforms.
 *
 * They previously lived as hand-duplicated literals in each platform's canvas
 * (with [CLIP_INSET] *derived* on Android but a hardcoded `22` on iOS); this
 * object makes the agreement structural instead of coincidental. The derivation
 * + layout invariants are pinned by `GarageDoorGeometryTest`. Follows the
 * `AppLinks` shared-constant precedent (locale-/platform-invariant config lives
 * in `domain`, consumed by both UIs via SKIE).
 */
object GarageDoorGeometry {
    /** Design viewport edge (square). All coordinates below are in this space. */
    const val VP: Float = 300f

    /** Aspect ratio of the door design (1:1 square). */
    const val ASPECT_RATIO: Float = 1f

    // Frame layout.
    const val FRAME_INSET: Float = 10f
    const val FRAME_STROKE_WIDTH: Float = 12f
    const val FRAME_CORNER_RADIUS: Float = 16f
    const val FRAME_BOTTOM: Float = 290f

    // Door panels — 4 evenly spaced panels (gap = pad = 6, radius = gap/2).
    const val PANEL_GAP: Float = 6f
    const val PANEL_X: Float = 20f
    const val PANEL_WIDTH: Float = 260f
    const val PANEL_HEIGHT: Float = 61f
    const val PANEL_RADIUS: Float = 3f

    /** Top Y of each of the 4 door panels (viewport space). */
    val PANEL_Y_STARTS: List<Float> = listOf(22f, 89f, 156f, 223f)

    // Handle on the bottom panel.
    const val HANDLE_X: Float = 139f
    const val HANDLE_Y: Float = 278f
    const val HANDLE_W: Float = 22f
    const val HANDLE_H: Float = 4f
    const val HANDLE_RADIUS: Float = 2f

    /**
     * Inset for clipping the panels inside the frame: frame inset + half the
     * frame stroke + one panel gap. **Derived** (not a literal) so it can never
     * disagree with the frame/gap constants above. Evaluates to `22f`.
     */
    const val CLIP_INSET: Float = FRAME_INSET + FRAME_STROKE_WIDTH / 2f + PANEL_GAP
}
