import Foundation
@preconcurrency import shared

/// Words for how a snooze went. Which outcome it is was decided in shared
/// code (`SnoozeAction.of`); this only picks the words, and the failure
/// lines are the Settings sheet's own (typed copy, verbatim from Android's
/// `snooze_failed_*` strings), so the sheet and the warning's Snooze action
/// cannot word the same failure differently.
enum SnoozeOutcomeWords {
    /// `SnoozeAction.Failed` is a shared sealed type, so this switch is
    /// exhaustive — a new failure case forces an update here rather than
    /// silently falling back to a generic message.
    static func failure(_ failed: SnoozeActionFailed) -> LocalizedStringResource {
        switch onEnum(of: failed) {
        case .eventChanged:
            return "Door state changed before snooze could apply."
        case .networkError:
            return "Snooze did not apply. Check your connection and try again."
        case .notAuthenticated:
            return "Sign in to snooze notifications."
        case .missingData:
            return "No recent door event available. Try again in a moment."
        }
    }

    /// The outcome card's title, or nil when there is nothing to say (nothing
    /// in flight ever reaches a card).
    static func title(_ action: SnoozeAction) -> LocalizedStringResource? {
        switch onEnum(of: action) {
        case .idle, .sending:
            return nil
        case .succeeded:
            return "Notifications snoozed"
        case .failed:
            return "Snooze did not apply"
        }
    }

    /// The card's body, or nil.
    static func body(_ action: SnoozeAction) -> LocalizedStringResource? {
        switch onEnum(of: action) {
        case .idle, .sending:
            return nil
        case .succeeded(let succeeded):
            switch onEnum(of: succeeded) {
            case .set(let set):
                // The Settings sheet's own convention for its snooze row.
                let until = Date(timeIntervalSince1970: TimeInterval(set.untilEpochSeconds))
                let time = until.formatted(date: .omitted, time: .shortened)
                return "Snoozing until \(time)"
            case .cleared:
                return "Snooze cleared"
            }
        case .failed(let failed):
            return failure(failed)
        }
    }

    /// Whether the outcome REPLACES the warning card or sits beside it.
    static func replacesWarning(_ action: SnoozeAction) -> Bool {
        if case .succeeded = onEnum(of: action) { return true }
        return false
    }
}
