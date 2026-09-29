import Foundation
@preconcurrency import shared

// iOS wording for the shared headline types. Which positions collapse onto
// the same headline is decided by `DoorHeadlineMapper`, and which of the
// three `StatusHeadline`s applies by `StatusHeadlineMapper` (the settle
// window), so the two apps cannot end up naming the same door differently;
// this picks only the words. The Home screen and the door-status intent both
// read from here, which is what keeps Siri and the screen it would open in
// agreement.

extension DoorHeadline {
    var label: LocalizedStringResource {
        switch self {
        case .open: return "Open"
        case .closed: return "Closed"
        case .opening: return "Opening"
        case .closing: return "Closing"
        case .unknown: return "Unknown"
        case .sensorConflict: return "Sensor conflict"
        }
    }
}

extension StatusHeadline {
    /// The door word, or the two things a screen says when it has no door:
    /// "Connecting…" while it is still arriving, "No signal" once that has
    /// gone on too long.
    var label: LocalizedStringResource {
        switch onEnum(of: self) {
        case .door(let door): return door.headline.label
        case .connecting: return "Connecting…"
        case .noSignal: return "No signal"
        }
    }
}
