import Foundation
import shared

/// The snooze row's state, with its one time value already rendered.
///
/// The mirror of Android's `SnoozeRowState`: a rendered projection of the shared
/// `SnoozeRowStatus`. The shared type carries an instant because 12- versus
/// 24-hour is a locale property; this type carries the formatted string because
/// the stateless view — and therefore the snapshot suite — must not depend on
/// the host's time zone.
///
/// Being one exhaustive enum is the point. The view previously received
/// `snoozeLabel: String`, `snoozeSnoozing: Bool` and `notificationsGranted: Bool`
/// and worked out the precedence itself, which is how "you snoozed this" and
/// "the OS is blocking notifications" ended up sharing a glyph.
enum SnoozeRowDisplay: Equatable {
    case loading
    case permissionDenied
    case off
    /// Snoozing until the given already-localized time of day.
    case snoozingUntil(String)

    /// SF Symbol for each state.
    ///
    /// `bell.badge.slash` (snoozed by you) and `bell.slash` (blocked by the OS)
    /// are deliberately different: one is a choice you can undo here, the other
    /// sends you to Settings. Android's equivalent pairing is
    /// `NotificationsPaused` / `NotificationsOff`.
    var icon: String {
        switch self {
        case .loading, .off: return "bell"
        case .snoozingUntil: return "bell.badge.slash"
        case .permissionDenied: return "bell.slash"
        }
    }

    /// Whether tapping the row opens the duration sheet (versus asking the OS
    /// for permission). Derived from the same value the row rendered from, so
    /// the tap target cannot disagree with the label.
    var opensDurationSheet: Bool { self != .permissionDenied }

    /// Row subtitle.
    ///
    /// `LocalizedStringResource`, not `String`: a `String` handed to a `Text`
    /// is invisible to the compiler's string extractor, so the subtitle could
    /// never be translated (see `docs/IOS_LOCALIZATION.md`). Callers that need
    /// actual characters — the snooze sheet's footer lowercases this — resolve
    /// it with `String(localized:)`.
    var subtitle: LocalizedStringResource {
        switch self {
        case .loading:
            return "Loading…"
        case .permissionDenied:
            return "Notifications disabled. Tap to enable."
        case .off:
            return "Notifications enabled"
        case .snoozingUntil(let time):
            return "Snoozing until \(time)"
        }
    }
}

/// iOS wording for the shared `SnoozeDurationUIOption` set.
///
/// The options and their order are shared; the words are not. The switch is
/// exhaustive so a duration added to shared fails the build here until it has a
/// label.
enum SnoozeDurationLabels {
    static func text(for option: SnoozeDurationUIOption) -> LocalizedStringResource {
        switch option {
        case .none: return "Don't snooze"
        case .oneHour: return "1 hour"
        case .fourHours: return "4 hours"
        case .eightHours: return "8 hours"
        case .twelveHours: return "12 hours"
        }
    }
}
