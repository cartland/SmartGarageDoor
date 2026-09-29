import AppIntents
import Foundation
import UIKit
@preconcurrency import shared

/// "Is the garage door open?" — answered, never acted on.
///
/// READ-ONLY, and the reason is the reason the app's own button takes two
/// taps and the watch's a press-and-hold: a spoken sentence is a single,
/// unconfirmable gesture, and acting on it would also skip the server's
/// `doorCommand` third gate that the phone and watch voice paths consult
/// (strategy 3.4). So this app has exactly one intent and it only looks.
/// `DoorStatusIntentTests` reads this file and refuses any reference to the
/// button, with the Home wrapper as the positive control.
///
/// Runs in the app's own process (`openAppWhenRun = false`): the system
/// launches the app in the background if it has to, `AppDelegate` builds the
/// DI graph exactly as on any launch, and `IntentGlanceStatus` answers from
/// the local store plus one bounded refresh — the same shape as the Android
/// widget, which is the other surface that gets asked cold.
struct DoorStatusIntent: AppIntent {
    static var title: LocalizedStringResource = "Garage door status"
    static var description: IntentDescription? = IntentDescription(
        "Says whether the garage door is open or closed, and since when. It never operates the door."
    )
    static var openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        guard let delegate = UIApplication.shared.delegate as? AppDelegate,
              let component = delegate.component else {
            let text = String(localized: DoorStatusWords.unavailable)
            return .result(value: text, dialog: IntentDialog(Self.spoken(text)))
        }
        let reader = component.intentGlanceStatus
        try await reader.refresh()
        let status = try await reader.current()
        let text = DoorStatusWords.sentence(for: status)
        return .result(value: text, dialog: IntentDialog(Self.spoken(text)))
    }

    /// Wraps an already-localized sentence for the dialog without minting a
    /// catalog key for the whole thing: every piece was localized on its own
    /// in `DoorStatusWords`, which is where translators see them.
    private static func spoken(_ text: String) -> LocalizedStringResource {
        LocalizedStringResource(String.LocalizationValue(text))
    }
}
