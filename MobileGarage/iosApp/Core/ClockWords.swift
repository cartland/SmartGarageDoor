import Foundation
@preconcurrency import shared

/// The platform's half of a clock time.
///
/// WHICH form to use — time only, or date and time — is the shared decision
/// (`SinceStatusMapper.clockFor`, strategy 1.8), so Android cannot draw the
/// day line differently. The FORMAT stays this platform's: a localized
/// template, not a fixed pattern, so the device decides 12- vs 24-hour and
/// field order. Pinning "h:mm a" + en_US would show AM/PM to a user whose
/// phone is set to 24-hour time; Android uses
/// `DateTimeFormatter.ofLocalizedTime` for the same reason.
///
/// One home for the formatters, because three callers format an instant —
/// the Home screen's since-line, the History list's event times, and the
/// door-status intent's spoken "since" — and three private copies had
/// already started to drift in their comments.
enum ClockWords {
    /// Time only on the same day, otherwise month, day and time.
    static func clockText(for date: Date, now: Date = Date()) -> String {
        let clock = SinceStatusMapper.shared.clockFor(
            sinceEpochSeconds: Int64(date.timeIntervalSince1970),
            nowEpochSeconds: Int64(now.timeIntervalSince1970),
            timeZoneId: TimeZone.current.identifier
        )
        switch clock {
        case .timeOnly: return timeOnlyFormatter.string(from: date)
        case .dateAndTime: return dateTimeFormatter.string(from: date)
        }
    }

    /// Time only, whatever the day: the History list's convention, whose day
    /// is its section header (mirrors `HistoryFormatter.formatTime`).
    static func timeOnly(_ date: Date) -> String {
        timeOnlyFormatter.string(from: date)
    }

    private static let timeOnlyFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("jmm")
        return formatter
    }()

    private static let dateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMMdjmm")
        return formatter
    }()
}
