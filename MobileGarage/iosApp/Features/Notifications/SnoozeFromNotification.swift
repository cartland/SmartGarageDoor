import Foundation
import UserNotifications
@preconcurrency import shared

/// The warning's Snooze action, decided and then said (strategy 3.5).
///
/// The same four lines as Android's `SnoozeFromNotification`: the door from
/// the local store's FIRST emission (a tap can land in a process that exists
/// only to answer it, where the repository's `StateFlow` is still its null
/// seed — the read the widget and the door-status intent use), the shared
/// `SnoozeNotificationsUseCase` for one hour, and `SnoozeAction.of`, the one
/// mapping from its result to a worded outcome, so this and the Settings
/// sheet cannot disagree. It holds nothing that can press;
/// `NotificationActionTests` reads this file and refuses any route to the
/// button.
enum SnoozeFromNotification {
    /// The one duration a notification action offers.
    static let duration = SnoozeDurationServerOption.hours1

    static func perform(component: NativeComponent) async -> SnoozeAction {
        var event: DoorEvent?
        for await first in component.localDoorDataSource.currentDoorEvent {
            event = first
            break
        }
        do {
            let result = try await component.snoozeNotificationsUseCase.invoke(
                snoozeDurationHours: duration.duration,
                lastChangeTimeSeconds: event?.lastChangeTimeSeconds
            )
            return SnoozeActionCompanion.shared.of(result: result)
        } catch {
            // The use case never throws (it answers a typed result); this is
            // the bridge's cancellation path, worded as the network failure.
            return SnoozeActionFailedNetworkError.shared
        }
    }

    /// Say how it went where the warning was. A success REPLACES the warning
    /// (the door is still open, but the user just said they know); a failure
    /// sits BESIDE it, because the warning still stands.
    static func present(
        _ action: SnoozeAction,
        replacing warningIdentifier: String,
        center: UNUserNotificationCenter = .current()
    ) async {
        guard let title = SnoozeOutcomeWords.title(action),
              let body = SnoozeOutcomeWords.body(action) else { return }
        if SnoozeOutcomeWords.replacesWarning(action) {
            center.removeDeliveredNotifications(withIdentifiers: [warningIdentifier])
        }
        let content = UNMutableNotificationContent()
        content.title = String(localized: title)
        content.body = String(localized: body)
        let request = UNNotificationRequest(identifier: outcomeIdentifier, content: content, trigger: nil)
        try? await center.add(request)
    }

    static let outcomeIdentifier = "garage_door_snooze_outcome"
}
