import XCTest
import UserNotifications
@preconcurrency import shared
@testable import GarageControl

/// The warning's Snooze action on iOS: the category the server names, the
/// one action it carries, the words, and what the handler cannot do.
final class NotificationActionTests: XCTestCase {
    func testTheCategoryIsTheOneTheServerStamps() throws {
        // The same fixture the server's warning freeze-test reads.
        let url = repoRoot.appendingPathComponent("wire-contracts/fcmDoorWarning/apns_category.json")
        let fixture = try JSONDecoder().decode([String: String].self, from: Data(contentsOf: url))
        XCTAssertEqual(fixture["category"], DoorWarningCategory.identifier)
    }

    func testTheCategoryOffersExactlyOneActionAndItOnlySnoozes() {
        let category = DoorWarningCategory.make()
        XCTAssertEqual(category.identifier, DoorWarningCategory.identifier)
        XCTAssertEqual(category.actions.map(\.identifier), [DoorWarningCategory.snoozeOneHour])
        XCTAssertTrue(category.actions[0].options.contains(.authenticationRequired))
    }

    func testOutcomesAreWordedAndDiffer() throws {
        let set = SnoozeActionSucceededSet(untilEpochSeconds: 1_700_003_600)
        let failed = SnoozeActionFailedNetworkError.shared

        XCTAssertNil(SnoozeOutcomeWords.title(SnoozeActionSending.shared))
        XCTAssertNil(SnoozeOutcomeWords.body(SnoozeActionIdle.shared))
        let setTitle = try XCTUnwrap(SnoozeOutcomeWords.title(set))
        let failedTitle = try XCTUnwrap(SnoozeOutcomeWords.title(failed))
        XCTAssertNotEqual(String(localized: setTitle), String(localized: failedTitle))
        let setBody = try XCTUnwrap(SnoozeOutcomeWords.body(set))
        XCTAssertTrue(String(localized: setBody).hasPrefix("Snoozing until "), String(localized: setBody))
        XCTAssertTrue(SnoozeOutcomeWords.replacesWarning(set))
        XCTAssertFalse(SnoozeOutcomeWords.replacesWarning(failed))

        // Positive control: every failure words differently, so a `failure`
        // that returned one line for everything could not pass.
        let failures: [any SnoozeActionFailed] = [
            SnoozeActionFailedEventChanged.shared,
            SnoozeActionFailedNetworkError.shared,
            SnoozeActionFailedNotAuthenticated.shared,
            SnoozeActionFailedMissingData.shared,
        ]
        let words = failures.map { String(localized: SnoozeOutcomeWords.failure($0)) }
        XCTAssertEqual(Set(words).count, words.count, "\(words)")
    }

    func testTheHandlerOnlySnoozes() throws {
        for file in ["DoorWarningCategory.swift", "SnoozeFromNotification.swift", "SnoozeOutcomeWords.swift"] {
            let text = try source("Features/Notifications/\(file)")
            for forbidden in Self.forbidden {
                XCTAssertFalse(text.contains(forbidden), "\(file) mentions \(forbidden)")
            }
        }
    }

    func testTheForbiddenListCanActuallyFire() throws {
        // Positive control: the Home wrapper DOES reach the button.
        let home = try source("Features/Home/HomeViewModelWrapper.swift")
        XCTAssertTrue(Self.forbidden.contains { home.contains($0) })
    }

    // MARK: - Fixtures

    private static let forbidden = [
        "onButtonTap",
        "pushRemoteButton",
        "PushRemoteButton",
        "RemoteButton",
        "homeViewModel",
        "HomeViewModel",
        "ButtonStateMachine",
        "DoorCommand",
    ]

    private var iosApp: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // SnapshotTests/
            .deletingLastPathComponent() // iosApp/
    }

    private var repoRoot: URL {
        iosApp
            .deletingLastPathComponent() // MobileGarage/
            .deletingLastPathComponent() // repo root
    }

    private func source(_ relativePath: String) throws -> String {
        try String(contentsOf: iosApp.appendingPathComponent(relativePath), encoding: .utf8)
    }
}
