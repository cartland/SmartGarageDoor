import AppIntents

/// The phrases Siri and the Shortcuts app offer for the one intent this app
/// has. Every phrase asks a question. There is deliberately no "open the
/// garage" (strategy 3.4): an intent acts on a single spoken sentence, with
/// nowhere for the two-tap confirm or the server's `doorCommand` gate, so
/// acting by voice on iOS would bypass both.
struct GarageShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: DoorStatusIntent(),
            phrases: [
                "Is the garage door open in \(.applicationName)",
                "Is the garage door closed in \(.applicationName)",
                "What is the garage door doing in \(.applicationName)",
                "Check the garage door in \(.applicationName)",
            ]
        )
    }
}
