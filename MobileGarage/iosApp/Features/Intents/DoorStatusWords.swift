import Foundation
@preconcurrency import shared

/// The door, said out loud.
///
/// Shared code decided everything that matters — which headline, whether the
/// reading can be vouched for, what qualifies it (`GlanceStatus`, ADR-035);
/// this only puts those decisions into sentences a voice assistant can read
/// and a Shortcuts automation can compare. It composes the same three parts
/// the Android widget draws (headline, warning, subline), in the same order,
/// so the two platforms answer the same question from the same facts. The
/// door WORD is `DoorHeadline.label`, the Home screen's own, so Siri cannot
/// name a door differently from the screen it would open.
enum DoorStatusWords {
    /// One or more sentences, each localized on its own, joined with a space.
    static func sentence(for status: GlanceStatus, now: Date = Date()) -> String {
        var parts = [String(localized: headline(status.headline))]
        if let warning = status.warning {
            parts.append(String(localized: self.warning(warning)))
        }
        if let subline = subline(status.subline, now: now) {
            parts.append(String(localized: subline))
        }
        return parts.joined(separator: " ")
    }

    /// The app could not be asked at all (no delegate, no graph). Should be
    /// unreachable in a launched process; said rather than thrown so Siri has
    /// words either way.
    static let unavailable: LocalizedStringResource = "The garage app is not ready yet. Try again in a moment."

    static func headline(_ headline: StatusHeadline) -> LocalizedStringResource {
        switch onEnum(of: headline) {
        case .door(let door):
            return doorSentence(door.headline)
        case .connecting:
            // A glance never settles (GlanceStatusMapper pins it), so this is
            // unreachable from the intent; kept for exhaustiveness and honesty.
            return "Still connecting to the garage."
        case .noSignal:
            return "No signal from the garage."
        }
    }

    static func doorSentence(_ door: DoorHeadline) -> LocalizedStringResource {
        // Full sentences rather than "The garage door is \(label)": "Sensor
        // conflict" does not slot into that frame, and a voice line should not
        // depend on a word that was chosen to fit a headline.
        switch door {
        case .open: return "The garage door is open."
        case .closed: return "The garage door is closed."
        case .opening: return "The garage door is opening."
        case .closing: return "The garage door is closing."
        case .unknown: return "The garage door's position is unknown."
        case .sensorConflict: return "The garage door's sensors disagree."
        }
    }

    static func warning(_ warning: GlanceWarning) -> LocalizedStringResource {
        switch warning {
        case .stuck: return "It looks stuck."
        case .misaligned: return "The sensors look misaligned."
        }
    }

    static func subline(_ subline: GlanceSubline, now: Date) -> LocalizedStringResource? {
        switch onEnum(of: subline) {
        case .duration(let duration):
            // The same instant the widget shows ("since 3:42 PM"): a statement
            // about a past moment that no update schedule can make wrong.
            let since = Date(timeIntervalSince1970: TimeInterval(duration.sinceEpochSeconds))
            let clock = ClockWords.clockText(for: since, now: now)
            return "Since \(clock)."
        case .notConfirmed:
            return "That is not confirmed: the garage has not reported recently."
        case .nothing:
            return nil
        }
    }
}
