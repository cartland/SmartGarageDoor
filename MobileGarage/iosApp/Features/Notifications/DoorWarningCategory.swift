import Foundation
import UserNotifications

/// The open-door warning's notification category and its one action.
///
/// The server stamps `aps.category` on the warning (both ends are pinned to
/// `wire-contracts/fcmDoorWarning/apns_category.json`), and iOS shows the
/// actions of whichever registered category matches. There is exactly one:
/// snooze for an hour. Snooze is not a press — it changes what the server
/// will say for an hour, never what the door does — which is why it may live
/// on a notification while the button may not (strategy 3.5).
enum DoorWarningCategory {
    /// Matches the server's `DOOR_WARNING_APNS_CATEGORY`.
    static let identifier = "DOOR_WARNING"
    static let snoozeOneHour = "SNOOZE_ONE_HOUR"

    static func make() -> UNNotificationCategory {
        UNNotificationCategory(
            identifier: identifier,
            actions: [
                UNNotificationAction(
                    identifier: snoozeOneHour,
                    title: String(localized: "Snooze 1 hour"),
                    // An unlocked device, as on Android: an hour of silence is
                    // not something a pocket should decide.
                    options: [.authenticationRequired]
                ),
            ],
            intentIdentifiers: [],
            options: []
        )
    }

    /// Registered at launch, before any warning can arrive.
    static func register(with center: UNUserNotificationCenter = .current()) {
        center.setNotificationCategories([make()])
    }
}
