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
// identity (ADR-029 § 3). The drawing **geometry** (`GarageDoorGeometry`), the
// door-fill **palette** (`GarageDoorPalette`), and the **animation spec**
// (`DoorAnimation`: offset constants, the `DoorPosition → offset / overlay /
// color-state` mappings, the live-slide duration, and the
// `DoorAnimationMemory` replay policy) are all shared `:domain` single sources
// of truth, consumed by both platforms via SKIE. No hand-mirrored mappings
// remain.
//
// Animation (ADR-032 spec-vs-execution split): the *spec* is shared/provable —
// what the door does (from/target offsets, the 12 s linear OPENING/CLOSING
// slide, the spring settle on the terminal event, the once-per-event replay).
// The *execution* is best-effort native — Android drives a Compose `Animatable`
// + `LaunchedEffect`; iOS drives a SwiftUI `@State` offset + `withAnimation`
// (below). Both read the identical shared parameters; only the frame-by-frame
// interpolation engine differs. See `MobileGarage/docs/DOOR_ANIMATION.md`.

/// Renders the garage door for a [DoorPosition], including the directional /
/// warning overlay, sized to a 1:1 square.
///
/// Two modes:
/// - **Static** (the default — History rows, previews, snapshot gallery): renders
///   the per-state snapshot offset (`DoorAnimation.staticPositionFor`) with a
///   gentle ease between states. Deterministic, no live trajectory.
/// - **Animated** (`animated: true` — the live Home door, the one live surface):
///   drives the full shared trajectory — a 12 s linear OPENING/CLOSING slide and
///   a spring settle to the terminal state, replaying the slide from the start
///   once per motion event. See `AnimatedDoorCanvas`.
struct GarageDoorView: View {
    let position: DoorPosition
    /// Picks the muted "stale" color variant — mirrors Android's
    /// `HomeStatusDisplay.isStale` (which is the device check-in staleness).
    var isStale: Bool = false
    /// Drive the full live trajectory instead of a static snapshot. Only the
    /// live Home door sets this; History rows + previews stay static.
    var animated: Bool = false
    /// Server timestamp of the door's last position change — identifies one
    /// motion event so the slide replays once per event (cold-open / first
    /// view), not on every re-appearance. Only consulted when `animated`.
    var lastChangeTimeSeconds: Int64?

    @Environment(\.colorScheme) private var scheme
    /// Shared replay memory (`:domain` `DoorAnimationMemory`), injected at the
    /// app root (`MainScreen`). Mirrors Android's `LocalDoorAnimationMemory`.
    @Environment(\.doorAnimationMemory) private var memory

    var body: some View {
        let rgb = DoorPalette.doorRGB(for: position, stale: isStale, scheme: scheme)
        let color = Color(rgb: rgb)
        let darkColor = Color(rgb: DoorPalette.blendWithBlackHalf(rgb))
        ZStack {
            if animated {
                AnimatedDoorCanvas(
                    position: position,
                    lastChangeTimeSeconds: lastChangeTimeSeconds,
                    memory: memory,
                    color: color,
                    darkColor: darkColor
                )
            } else {
                staticCanvas(color: color, darkColor: darkColor)
            }
            overlay
        }
        .aspectRatio(GarageDoorCanvas.aspectRatio, contentMode: .fit)
        .accessibilityHidden(true) // the status label carries the spoken state
    }

    private func staticCanvas(color: Color, darkColor: Color) -> some View {
        let offset = CGFloat(DoorAnimation.shared.staticPositionFor(doorPosition: position))
        return GarageDoorCanvas(doorOffset: offset, color: color, darkColor: darkColor)
            .animation(.easeInOut(duration: 0.6), value: offset)
    }

    @ViewBuilder private var overlay: some View {
        switch DoorAnimation.shared.overlayFor(doorPosition: position) {
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

/// Drives the live door trajectory in SwiftUI, consuming the shared `:domain`
/// `DoorAnimation` spec + `DoorAnimationMemory` replay policy (Tier 1, ADR-032).
/// This is the **execution** layer — best-effort native animation; every
/// parameter it animates toward is shared and provable.
///
/// - First appearance of a not-yet-seen motion event (cold-open / first view):
///   seed at the `from` end and slide linearly to the target over the shared
///   `ANIMATION_DURATION_SECONDS`. Re-appearance of the same event snaps.
/// - A live state change animates from the *current* offset: motion states slide
///   linearly; terminal/error states settle via a slow no-overshoot spring —
///   that settle is the "spring shut / spring open" when the real terminal event
///   arrives over the network mid-slide.
private struct AnimatedDoorCanvas: View {
    let position: DoorPosition
    let lastChangeTimeSeconds: Int64?
    let memory: DoorAnimationMemory
    let color: Color
    let darkColor: Color

    @State private var offset: CGFloat

    init(
        position: DoorPosition,
        lastChangeTimeSeconds: Int64?,
        memory: DoorAnimationMemory,
        color: Color,
        darkColor: Color
    ) {
        self.position = position
        self.lastChangeTimeSeconds = lastChangeTimeSeconds
        self.memory = memory
        self.color = color
        self.darkColor = darkColor
        // Snap-safe default seed = the target. `onAppear` dips to the `from` end
        // and slides only when this is a newly observed motion event.
        _offset = State(initialValue: CGFloat(DoorAnimation.shared.targetPositionFor(doorPosition: position)))
    }

    var body: some View {
        GarageDoorCanvas(doorOffset: offset, color: color, darkColor: darkColor)
            .onAppear { seedAndAnimate() }
            // iOS 16 single-parameter onChange (the two-param form is iOS 17+).
            .onChange(of: position) { _ in animate(to: position) }
    }

    private func seedAndAnimate() {
        let isMotion = !DoorAnimation.shared.useSpringFor(doorPosition: position)
        let key = DoorMotionKey(
            doorPosition: position,
            lastChangeTimeSeconds: lastChangeTimeSeconds.map { KotlinLong(value: $0) }
        )
        let replay = isMotion && memory.consumeAnimateFromStart(key: key)
        guard replay else {
            offset = CGFloat(DoorAnimation.shared.targetPositionFor(doorPosition: position))
            return
        }
        // Seed at the `from` end (no animation), then slide to target on the next
        // runloop tick so SwiftUI animates the full slide instead of coalescing
        // both assignments into the final value.
        offset = CGFloat(DoorAnimation.shared.fromPositionFor(doorPosition: position))
        DispatchQueue.main.async {
            withAnimation(.linear(duration: Self.slideDuration)) {
                offset = CGFloat(DoorAnimation.shared.targetPositionFor(doorPosition: position))
            }
        }
    }

    private func animate(to position: DoorPosition) {
        let target = CGFloat(DoorAnimation.shared.targetPositionFor(doorPosition: position))
        if DoorAnimation.shared.useSpringFor(doorPosition: position) {
            // Slow, no-overshoot settle — the native analog of Android's
            // spring(DampingRatioNoBouncy, StiffnessVeryLow). Exact feel is Tier-3
            // native taste; the fact that terminal states *settle* is shared.
            withAnimation(.spring(response: 0.6, dampingFraction: 1.0)) { offset = target }
        } else {
            withAnimation(.linear(duration: Self.slideDuration)) { offset = target }
        }
    }

    /// Shared product-decision slide duration (12 s) read from `:domain`.
    private static var slideDuration: Double { Double(DoorAnimation.shared.ANIMATION_DURATION_SECONDS) }
}

/// Pure drawing of the door at a vertical [doorOffset] (proportion of the drawn
/// size; 0 = closed, negative = sliding up / opening). `Animatable` so SwiftUI
/// interpolates the offset frame-by-frame during transitions.
struct GarageDoorCanvas: View, Animatable {
    var doorOffset: CGFloat
    var color: Color
    var darkColor: Color

    /// 1:1 square — mirrors Android `GarageDoorGeometry.ASPECT_RATIO`.
    static var aspectRatio: CGFloat { CGFloat(GarageDoorGeometry.shared.ASPECT_RATIO) }

    // Design viewport — all constants below are in this unit space. The canvas
    // scales uniformly to fit. The single source of truth is `:domain`
    // `GarageDoorGeometry`, consumed identically by Android's `GarageDoorCanvas.kt`
    // (Tier-1 brand surface, ADR-032) — read here into `CGFloat` so the drawing
    // body below is unchanged.
    private static var vp: CGFloat { CGFloat(GarageDoorGeometry.shared.VP) }
    private static var frameInset: CGFloat { CGFloat(GarageDoorGeometry.shared.FRAME_INSET) }
    private static var frameStroke: CGFloat { CGFloat(GarageDoorGeometry.shared.FRAME_STROKE_WIDTH) }
    private static var frameCorner: CGFloat { CGFloat(GarageDoorGeometry.shared.FRAME_CORNER_RADIUS) }
    private static var frameBottom: CGFloat { CGFloat(GarageDoorGeometry.shared.FRAME_BOTTOM) }
    private static var panelX: CGFloat { CGFloat(GarageDoorGeometry.shared.PANEL_X) }
    private static var panelWidth: CGFloat { CGFloat(GarageDoorGeometry.shared.PANEL_WIDTH) }
    private static var panelHeight: CGFloat { CGFloat(GarageDoorGeometry.shared.PANEL_HEIGHT) }
    private static var panelRadius: CGFloat { CGFloat(GarageDoorGeometry.shared.PANEL_RADIUS) }
    private static var panelYStarts: [CGFloat] {
        GarageDoorGeometry.shared.PANEL_Y_STARTS.map { CGFloat(truncating: $0) }
    }
    private static var handleX: CGFloat { CGFloat(GarageDoorGeometry.shared.HANDLE_X) }
    private static var handleY: CGFloat { CGFloat(GarageDoorGeometry.shared.HANDLE_Y) }
    private static var handleW: CGFloat { CGFloat(GarageDoorGeometry.shared.HANDLE_W) }
    private static var handleH: CGFloat { CGFloat(GarageDoorGeometry.shared.HANDLE_H) }
    private static var handleRadius: CGFloat { CGFloat(GarageDoorGeometry.shared.HANDLE_RADIUS) }
    private static var clipInset: CGFloat { CGFloat(GarageDoorGeometry.shared.CLIP_INSET) }

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

// MARK: - Shared door-animation replay memory (environment)

/// Carries the shared `:domain` `DoorAnimationMemory` (the once-per-event slide
/// dedup) down the view tree. The **iOS holder lifecycle** — analogous to
/// Android's `LocalDoorAnimationMemory` `CompositionLocal`: a single instance is
/// created once at the app root (`MainScreen`, a `@State` so it survives tab
/// switches and resets on process death) and injected here. The default is a
/// throwaway instance, harmless because static-mode rendering never consults it.
private struct DoorAnimationMemoryKey: EnvironmentKey {
    static let defaultValue = DoorAnimationMemory()
}

extension EnvironmentValues {
    var doorAnimationMemory: DoorAnimationMemory {
        get { self[DoorAnimationMemoryKey.self] }
        set { self[DoorAnimationMemoryKey.self] = newValue }
    }
}

// MARK: - Door status palette (mirror DoorStatusColorScheme.kt + Color.kt)

private enum DoorPalette {
    // (light, dark) RGB pairs — the shared brand-locked `:domain`
    // GarageDoorPalette (single source of truth, mirrored by Android `Color.kt`).
    // The shared ARGB Longs are masked to the low 24 bits for `Color(rgb:)`.
    private static func rgb(_ argb: Int64) -> Int { Int(argb & 0xFFFFFF) }
    private static var palette: GarageDoorPalette { GarageDoorPalette.shared }

    private static var closedFresh: (light: Int, dark: Int) {
        (rgb(palette.CLOSED_FRESH_LIGHT), rgb(palette.CLOSED_FRESH_DARK))
    }
    private static var closedStale: (light: Int, dark: Int) {
        (rgb(palette.CLOSED_STALE_LIGHT), rgb(palette.CLOSED_STALE_DARK))
    }
    private static var openFresh: (light: Int, dark: Int) {
        (rgb(palette.OPEN_FRESH_LIGHT), rgb(palette.OPEN_FRESH_DARK))
    }
    private static var openStale: (light: Int, dark: Int) {
        (rgb(palette.OPEN_STALE_LIGHT), rgb(palette.OPEN_STALE_DARK))
    }
    private static var unknownFresh: (light: Int, dark: Int) {
        (rgb(palette.UNKNOWN_FRESH_LIGHT), rgb(palette.UNKNOWN_FRESH_DARK))
    }
    private static var unknownStale: (light: Int, dark: Int) {
        (rgb(palette.UNKNOWN_STALE_LIGHT), rgb(palette.UNKNOWN_STALE_DARK))
    }

    static func doorRGB(for p: DoorPosition, stale: Bool, scheme: ColorScheme) -> Int {
        let pair: (light: Int, dark: Int)
        switch DoorAnimation.shared.colorStateFor(doorPosition: p) {
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
