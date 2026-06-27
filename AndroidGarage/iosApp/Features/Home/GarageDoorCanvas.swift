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

import SwiftUI
@preconcurrency import shared

// SwiftUI port of Android's `GarageDoorCanvas.kt` / `GarageIcon.kt` — the
// animated garage-door visualization that is part of the shared "Garage"
// identity (ADR-029 § 3). The drawing geometry, the door-position → offset /
// color / overlay mappings, and the door-status palette all mirror the Android
// source so both platforms render the same door.
//
// Parity scope (v1): the *visual* — door shape, per-state offset, per-state
// color (fresh / stale), and the directional / warning overlay. The animation
// is a simple ease between offsets on state change. The full Android trajectory
// (a 12 s linear tween for OPENING / CLOSING and a once-per-event "replay from
// the start" gated by `DoorAnimationMemory`) is a deferred polish pass.

/// Renders the garage door for a [DoorPosition], including the directional /
/// warning overlay, sized to a 1:1 square. Animates the door panels between
/// states.
struct GarageDoorView: View {
    let position: DoorPosition
    /// Picks the muted "stale" color variant — mirrors Android's
    /// `HomeStatusDisplay.isStale` (which is the device check-in staleness).
    var isStale: Bool = false

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let offset = DoorVisual.offset(for: position)
        let rgb = DoorPalette.doorRGB(for: position, stale: isStale, scheme: scheme)
        ZStack {
            GarageDoorCanvas(
                doorOffset: offset,
                color: Color(rgb: rgb),
                darkColor: Color(rgb: DoorPalette.blendWithBlackHalf(rgb))
            )
            .animation(.easeInOut(duration: 0.6), value: offset)

            overlay
        }
        .aspectRatio(GarageDoorCanvas.aspectRatio, contentMode: .fit)
        .accessibilityHidden(true) // the status label carries the spoken state
    }

    @ViewBuilder private var overlay: some View {
        switch DoorVisual.overlay(for: position) {
        case .none:
            EmptyView()
        case .arrowUp:
            DoorOverlayBadge(systemName: "arrow.up", iconScale: 0.9)
        case .arrowDown:
            DoorOverlayBadge(systemName: "arrow.down", iconScale: 0.9)
        case .warning:
            DoorOverlayBadge(systemName: "exclamationmark.triangle.fill", iconScale: 0.6)
        }
    }
}

/// Pure drawing of the door at a vertical [doorOffset] (proportion of the drawn
/// size; 0 = closed, negative = sliding up / opening). `Animatable` so SwiftUI
/// interpolates the offset frame-by-frame during transitions.
struct GarageDoorCanvas: View, Animatable {
    var doorOffset: CGFloat
    var color: Color
    var darkColor: Color

    /// 1:1 square — mirrors Android `GARAGE_DOOR_ASPECT_RATIO`.
    static let aspectRatio: CGFloat = 1

    // Design viewport — all constants below are in this unit space. The canvas
    // scales uniformly to fit. Values mirror `GarageDoorCanvas.kt`.
    private static let vp: CGFloat = 300
    private static let frameInset: CGFloat = 10
    private static let frameStroke: CGFloat = 12
    private static let frameCorner: CGFloat = 16
    private static let frameBottom: CGFloat = 290
    private static let panelX: CGFloat = 20
    private static let panelWidth: CGFloat = 260
    private static let panelHeight: CGFloat = 61
    private static let panelRadius: CGFloat = 3
    private static let panelYStarts: [CGFloat] = [22, 89, 156, 223]
    private static let handleX: CGFloat = 139
    private static let handleY: CGFloat = 278
    private static let handleW: CGFloat = 22
    private static let handleH: CGFloat = 4
    private static let handleRadius: CGFloat = 2
    // FRAME_INSET + FRAME_STROKE_WIDTH / 2 + PANEL_GAP (10 + 6 + 6).
    private static let clipInset: CGFloat = 22

    var animatableData: CGFloat {
        get { doorOffset }
        set { doorOffset = newValue }
    }

    var body: some View {
        Canvas { context, size in
            let vp = Self.vp
            let scale = min(size.width / vp, size.height / vp)
            let drawSize = vp * scale
            let originX = (size.width - drawSize) / 2
            let originY = (size.height - drawSize) / 2

            func x(_ v: CGFloat) -> CGFloat { v * scale + originX }
            func y(_ v: CGFloat) -> CGFloat { v * scale + originY }
            func s(_ v: CGFloat) -> CGFloat { v * scale }

            // Vertical gradient, color (top) → darkColor (bottom). First stop at
            // 0.3 to match Compose's `verticalGradient(0.3 to color, 1 to dark)`.
            let shading = GraphicsContext.Shading.linearGradient(
                Gradient(stops: [
                    .init(color: color, location: 0.3),
                    .init(color: darkColor, location: 1.0),
                ]),
                startPoint: CGPoint(x: 0, y: originY),
                endPoint: CGPoint(x: 0, y: originY + drawSize)
            )

            // Panels + handle, clipped inside the frame. Clip a value-type copy
            // so the frame below draws unclipped (mirrors Compose `clipRect {}`).
            let clipInset = Self.clipInset
            var clipped = context
            clipped.clip(to: Path(CGRect(
                x: x(clipInset),
                y: y(clipInset),
                width: x(vp - clipInset) - x(clipInset),
                height: y(vp) - y(clipInset)
            )))

            let panelOffset = doorOffset * drawSize
            for panelY in Self.panelYStarts {
                let rect = CGRect(
                    x: x(Self.panelX),
                    y: y(panelY) + panelOffset,
                    width: s(Self.panelWidth),
                    height: s(Self.panelHeight)
                )
                clipped.fill(Path(roundedRect: rect, cornerRadius: s(Self.panelRadius)), with: shading)
            }

            let handle = CGRect(
                x: x(Self.handleX),
                y: y(Self.handleY) + panelOffset,
                width: s(Self.handleW),
                height: s(Self.handleH)
            )
            clipped.fill(
                Path(roundedRect: handle, cornerRadius: s(Self.handleRadius)),
                with: .color(Color(rgb: 0x111111))
            )

            // Frame — U-shape with rounded top corners, stroked on top.
            let cr = s(Self.frameCorner)
            let left = x(Self.frameInset)
            let right = x(vp - Self.frameInset)
            let top = y(Self.frameInset)
            let bottom = y(Self.frameBottom)
            var frame = Path()
            frame.move(to: CGPoint(x: left, y: bottom))
            frame.addArc(tangent1End: CGPoint(x: left, y: top), tangent2End: CGPoint(x: right, y: top), radius: cr)
            frame.addArc(tangent1End: CGPoint(x: right, y: top), tangent2End: CGPoint(x: right, y: bottom), radius: cr)
            frame.addLine(to: CGPoint(x: right, y: bottom))
            context.stroke(frame, with: shading, lineWidth: s(Self.frameStroke))
        }
    }
}

/// Circular badge overlaid on the door for motion / warning states. Mirrors
/// Android's `DirectionOverlay` / `WarningOverlay` (30% of the door, centered).
private struct DoorOverlayBadge: View {
    let systemName: String
    let iconScale: CGFloat

    var body: some View {
        GeometryReader { geo in
            let diameter = min(geo.size.width, geo.size.height) * 0.3
            ZStack {
                Circle().fill(Color(uiColor: .systemBackground))
                Image(systemName: systemName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: diameter * iconScale, height: diameter * iconScale)
                    .foregroundStyle(Color(uiColor: .label))
            }
            .frame(width: diameter, height: diameter)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

// MARK: - Door state → visual mappings (mirror DoorAnimation.kt)

private enum DoorColorState { case closed, open, unknown }
private enum DoorOverlayKind { case none, arrowUp, arrowDown, warning }

private enum DoorVisual {
    // Door offset positions as a proportion of viewport height — mirror the
    // constants in `AnimatableGarageDoor.kt`.
    private static let closed: CGFloat = 0.0
    private static let closingStatic: CGFloat = -0.20
    private static let midway: CGFloat = -0.35
    private static let openingStatic: CGFloat = -0.65
    private static let open: CGFloat = -0.75

    /// Visual offset for a state. Motion states use the mid-motion "static"
    /// offsets (Android `staticPositionFor`) so OPENING / CLOSING read as a
    /// door in motion under the simple ease, rather than snapping to the
    /// open / closed endpoints (`targetPositionFor`).
    static func offset(for p: DoorPosition) -> CGFloat {
        switch p {
        case .unknown: return midway
        case .closed: return closed
        case .opening: return openingStatic
        case .openingTooLong: return midway
        case .open: return open
        case .openMisaligned: return open
        case .closing: return closingStatic
        case .closingTooLong: return midway
        case .errorSensorConflict: return midway
        }
    }

    static func colorState(for p: DoorPosition) -> DoorColorState {
        switch p {
        case .unknown, .errorSensorConflict: return .unknown
        case .closed: return .closed
        case .opening, .openingTooLong, .open, .openMisaligned, .closing, .closingTooLong: return .open
        }
    }

    static func overlay(for p: DoorPosition) -> DoorOverlayKind {
        switch p {
        case .opening: return .arrowUp
        case .closing: return .arrowDown
        case .unknown, .openingTooLong, .closingTooLong, .errorSensorConflict: return .warning
        case .closed, .open, .openMisaligned: return .none
        }
    }
}

// MARK: - Door status palette (mirror DoorStatusColorScheme.kt + Color.kt)

private enum DoorPalette {
    // (light, dark) hex pairs from Android `Color.kt`.
    private static let closedFresh = (light: 0x226B43, dark: 0x25673C)
    private static let closedStale = (light: 0x456C54, dark: 0x40694F)
    private static let openFresh = (light: 0x932F1E, dark: 0x7A2B1E)
    private static let openStale = (light: 0x9A655C, dark: 0x7A524B)
    private static let unknownFresh = (light: 0x444444, dark: 0x555555)
    private static let unknownStale = (light: 0x444444, dark: 0x555555)

    static func doorRGB(for p: DoorPosition, stale: Bool, scheme: ColorScheme) -> Int {
        let pair: (light: Int, dark: Int)
        switch DoorVisual.colorState(for: p) {
        case .closed: pair = stale ? closedStale : closedFresh
        case .open: pair = stale ? openStale : openFresh
        case .unknown: pair = stale ? unknownStale : unknownFresh
        }
        return scheme == .dark ? pair.dark : pair.light
    }

    /// Halve each channel — equivalent to Android's
    /// `blendColors(color, Black, 0.5)` (the gradient's dark end).
    static func blendWithBlackHalf(_ rgb: Int) -> Int {
        let r = Int((Double((rgb >> 16) & 0xFF) * 0.5).rounded())
        let g = Int((Double((rgb >> 8) & 0xFF) * 0.5).rounded())
        let b = Int((Double(rgb & 0xFF) * 0.5).rounded())
        return (r << 16) | (g << 8) | b
    }
}

private extension Color {
    /// 0xRRGGBB in sRGB — matches Android's gamma-space `Color(0xFF......)`.
    init(rgb: Int) {
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255.0,
            green: Double((rgb >> 8) & 0xFF) / 255.0,
            blue: Double(rgb & 0xFF) / 255.0
        )
    }
}

// MARK: - Door state label (mirror HomeContent.doorStateLabel)

extension DoorPosition {
    /// User-visible label for the door state. Mirrors Android's
    /// `doorStateLabel(DoorPosition)` + `home_door_state_*` strings.
    var statusLabel: String {
        switch self {
        case .open, .openMisaligned: return "Open"
        case .closed: return "Closed"
        case .unknown: return "Unknown"
        case .opening, .openingTooLong: return "Opening"
        case .closing, .closingTooLong: return "Closing"
        case .errorSensorConflict: return "Sensor conflict"
        }
    }
}

#Preview("Door closed") {
    GarageDoorView(position: .closed)
        .frame(width: 180, height: 180)
        .padding()
}

#Preview("Door opening") {
    GarageDoorView(position: .opening)
        .frame(width: 180, height: 180)
        .padding()
}

#Preview("Door states") {
    let states: [DoorPosition] = [
        .closed, .opening, .open, .closing, .openingTooLong, .errorSensorConflict, .unknown,
    ]
    return ScrollView {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 120))], spacing: 16) {
            ForEach(states, id: \.self) { state in
                VStack {
                    GarageDoorView(position: state)
                        .frame(height: 120)
                    Text(state.statusLabel).font(.caption)
                }
            }
        }
        .padding()
    }
}
