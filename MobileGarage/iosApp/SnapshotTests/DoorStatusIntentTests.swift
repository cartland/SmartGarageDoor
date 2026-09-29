import XCTest
@preconcurrency import shared
@testable import GarageControl

/// The door-status intent is the one thing on iOS that Siri can reach. These
/// pin what it says and, more importantly, what it cannot do.
final class DoorStatusIntentTests: XCTestCase {
    private let now: Int64 = 1_700_000_000

    func testAConfirmedDoorIsNamedWithSinceWhen() {
        let status = glance(door: .closed, checkedInAgo: 20, changedAgo: 3 * 3600, fetchFailed: false)
        let text = DoorStatusWords.sentence(for: status, now: nowDate)
        XCTAssertTrue(text.contains("closed"), text)
        XCTAssertTrue(text.contains("Since "), text)
        XCTAssertFalse(text.contains("not confirmed"), text)
    }

    func testAReadingNotVouchedForSaysSoAndGivesNoSince() {
        // The garage reported, but our refresh failed: a remembered door,
        // which a duration would present as continuous when it may not be.
        let status = glance(door: .open, checkedInAgo: 20, changedAgo: 3 * 3600, fetchFailed: true)
        let text = DoorStatusWords.sentence(for: status, now: nowDate)
        XCTAssertTrue(text.contains("open"), text)
        XCTAssertTrue(text.contains("not confirmed"), text)
        XCTAssertFalse(text.contains("Since "), text)
    }

    func testNothingKnownIsNoSignal() {
        let status = glance(door: nil, checkedInAgo: nil, changedAgo: nil, fetchFailed: true)
        let text = DoorStatusWords.sentence(for: status, now: nowDate)
        XCTAssertTrue(text.contains("No signal"), text)
    }

    func testTwoDoorsDoNotReadTheSame() {
        // Positive control for the contains() checks above: a sentence
        // builder that ignored the door would still contain "open" for one
        // of them. Open and closed must differ.
        let open = DoorStatusWords.sentence(
            for: glance(door: .open, checkedInAgo: 20, changedAgo: 60, fetchFailed: false), now: nowDate
        )
        let closed = DoorStatusWords.sentence(
            for: glance(door: .closed, checkedInAgo: 20, changedAgo: 60, fetchFailed: false), now: nowDate
        )
        XCTAssertNotEqual(open, closed)
    }

    func testTheIntentOnlyLooks() throws {
        // The intent runs in the app process with the whole DI graph in
        // reach, so nothing structural stops it from pressing. This reads
        // its source and refuses every name that leads to the button.
        XCTAssertFalse(DoorStatusIntent.openAppWhenRun)
        let intent = try source("Features/Intents/DoorStatusIntent.swift")
        let words = try source("Features/Intents/DoorStatusWords.swift")
        for forbidden in Self.forbidden {
            XCTAssertFalse(intent.contains(forbidden), "DoorStatusIntent mentions \(forbidden)")
            XCTAssertFalse(words.contains(forbidden), "DoorStatusWords mentions \(forbidden)")
        }
    }

    func testTheForbiddenListCanActuallyFire() throws {
        // Positive control: the Home wrapper DOES reach the button, so the
        // same check against it must find something — otherwise a renamed
        // symbol has made the list blind.
        let home = try source("Features/Home/HomeViewModelWrapper.swift")
        XCTAssertTrue(Self.forbidden.contains { home.contains($0) })
    }

    // MARK: - Fixtures

    // Spelled as the symbols are, not as prose: the intent's own doc comment
    // is allowed to name the `doorCommand` gate while explaining why it does
    // not consult it, but `CheckDoorCommandUseCase` / `checkDoorCommand` in
    // code would be a route to the acting path.
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

    private var nowDate: Date { Date(timeIntervalSince1970: TimeInterval(now)) }

    private func glance(door: DoorPosition?, checkedInAgo: Int64?, changedAgo: Int64?, fetchFailed: Bool) -> GlanceStatus {
        GlanceStatusMapper.shared.forGlance(
            doorPosition: door,
            lastCheckInEpochSeconds: checkedInAgo.map { KotlinLong(value: now - $0) },
            lastChangeEpochSeconds: changedAgo.map { KotlinLong(value: now - $0) },
            nowEpochSeconds: now,
            isFetchError: fetchFailed
        )
    }

    /// A production source file, located from this test's own path so the
    /// check needs no bundle resources.
    private func source(_ relativePath: String) throws -> String {
        let iosApp = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // SnapshotTests/
            .deletingLastPathComponent() // iosApp/
        return try String(contentsOf: iosApp.appendingPathComponent(relativePath), encoding: .utf8)
    }
}
