// swiftlint:disable all
// swiftformat:disable all

import XCTest
import SwiftUI
import Prefire
import shared
@testable import iosApp
import SnapshotTesting
#if canImport(AccessibilitySnapshot)
    import AccessibilitySnapshot
#endif

@MainActor class PreviewTests: XCTestCase, Sendable {
    private var simulatorDevice: String?
    private var requiredOSVersion: Int?
    private let snapshotDevices: [String] = []
#if os(iOS)
    private let deviceConfig: DeviceConfig = ViewImageConfig.iPhoneX.deviceConfig
#elseif os(tvOS)
    private let deviceConfig: DeviceConfig = ViewImageConfig.tv.deviceConfig
#endif



    @MainActor override func setUp() async throws {
        try await super.setUp()

        checkEnvironments()
        UIView.setAnimationsEnabled(false)
    }

    // MARK: - PreviewProvider

    // MARK: - Macros

    func test_Doorstates_Preview() {        
        let prefireSnapshot = PrefireSnapshot(
            {
                let states: [DoorPosition] = [
                    .closed, .opening, .open, .closing, .openingTooLong, .errorSensorConflict, .unknown,
                ]
            return ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120))], spacing: 16) {
                        ForEach(states, id: \.self) { state in
                            VStack {
                                GarageDoorView(position: state)
                                    .frame(height: 120)
                                Text(state.statusLabel).font(.caption)
                            }
                        }
                    }
                    .padding()
                }
            },
            name: "Door states",
            isScreen: true,
            device: deviceConfig
        )

        if let failure = assertSnapshots(for: prefireSnapshot) {
            XCTFail(failure)
        }
    }

    func test_Dooropening_Preview() {        
        let prefireSnapshot = PrefireSnapshot(
            {
                GarageDoorView(position: .opening)
                    .frame(width: 180, height: 180)
                    .padding()
            },
            name: "Door opening",
            isScreen: true,
            device: deviceConfig
        )

        if let failure = assertSnapshots(for: prefireSnapshot) {
            XCTFail(failure)
        }
    }

    func test_Doorclosed_Preview() {        
        let prefireSnapshot = PrefireSnapshot(
            {
                GarageDoorView(position: .closed)
                    .frame(width: 180, height: 180)
                    .padding()
            },
            name: "Door closed",
            isScreen: true,
            device: deviceConfig
        )

        if let failure = assertSnapshots(for: prefireSnapshot) {
            XCTFail(failure)
        }
    }
    // MARK: Private

    private func assertSnapshots<Content: SwiftUI.View>(for prefireSnapshot: PrefireSnapshot<Content>) -> String? {
        guard !snapshotDevices.isEmpty else {
            return assertSnapshot(for: prefireSnapshot)
        }

        for deviceName in snapshotDevices {
            var snapshot = prefireSnapshot
            guard let device: DeviceConfig = PreviewDevice(rawValue: deviceName).snapshotDevice() else {
                fatalError("Unknown device name from configuration file: \(deviceName)")
            }

            snapshot.name = "\(prefireSnapshot.name)-\(deviceName)"
            snapshot.device = device

            // Ignore specific device safe area
            snapshot.device.safeArea = .zero

            // Ignore specific device display scale
            snapshot.traits = UITraitCollection(displayScale: 2.0)

            if let failure = assertSnapshot(for: snapshot) {
                XCTFail(failure)
            }
        }

        return nil
    }

    private func assertSnapshot<Content: SwiftUI.View>(for prefireSnapshot: PrefireSnapshot<Content>) -> String? {
        let (previewView, preferences) = prefireSnapshot.loadViewWithPreferences()

        let failure = verifySnapshot(
            of: previewView,
            as: .wait(
                for: preferences.delay,
                on: .image(
                    precision: preferences.precision,
                    perceptualPrecision: preferences.perceptualPrecision,
                    layout: prefireSnapshot.isScreen ? .device(config: prefireSnapshot.device.imageConfig) : .sizeThatFits,
                    traits: prefireSnapshot.traits
                )
            ),
            record: preferences.record,
            testName: prefireSnapshot.name
        )

        #if canImport(AccessibilitySnapshot)
            let vc = UIHostingController(rootView: previewView)
            vc.view.frame = UIScreen.main.bounds

            SnapshotTesting.assertSnapshot(
                matching: vc,
                as: .wait(for: preferences.delay, on: .accessibilityImage(showActivationPoints: .always)),
                testName: prefireSnapshot.name + ".accessibility"
            )
        #endif
        return failure
    }

    /// Check environments to avoid problems with snapshots on different devices or OS.
    private func checkEnvironments() {
        if let simulatorDevice, let deviceModel = ProcessInfo().environment["SIMULATOR_MODEL_IDENTIFIER"] {
            guard deviceModel.contains(simulatorDevice) else {
                fatalError("Switch to using \(simulatorDevice) for these tests. (You are using \(deviceModel))")
            }
        }

        if let requiredOSVersion {
            let osVersion = ProcessInfo().operatingSystemVersion
            guard osVersion.majorVersion == requiredOSVersion else {
                fatalError("Switch to iOS \(requiredOSVersion) for these tests. (You are using \(osVersion))")
            }
        }
    }
}

// MARK: - SnapshotTesting + Extensions

private extension DeviceConfig {
    var imageConfig: ViewImageConfig { ViewImageConfig(safeArea: safeArea, size: size, traits: traits) }
}

private extension ViewImageConfig {
    var deviceConfig: DeviceConfig { DeviceConfig(safeArea: safeArea, size: size, traits: traits) }
}

private extension PreviewDevice {
    func snapshotDevice() -> ViewImageConfig? {
        switch rawValue {
        #if os(iOS)
        case "iPhone 16 Pro Max", "iPhone 15 Pro Max", "iPhone 14 Pro Max", "iPhone 13 Pro Max", "iPhone 12 Pro Max":
            return .iPhone13ProMax
        case "iPhone 16 Pro", "iPhone 15 Pro", "iPhone 14 Pro", "iPhone 13 Pro", "iPhone 12 Pro":
            return .iPhone13Pro
        case "iPhone 16", "iPhone 15", "iPhone 14", "iPhone 13", "iPhone 12", "iPhone 11", "iPhone 10", "iPhone X":
            return .iPhoneX
        case "iPhone 6", "iPhone 6s", "iPhone 7", "iPhone 8", "iPhone SE (2nd generation)", "iPhone SE (3rd generation)":
            return .iPhone8
        case "iPhone 6 Plus", "iPhone 6s Plus", "iPhone 8 Plus":
            return .iPhone8Plus
        case "iPhone SE (1st generation)":
            return .iPhoneSe
        case "iPad":
            return .iPad10_2
        case "iPad Mini":
            return .iPadMini
        case "iPad Pro 11":
            return .iPadPro11
        case "iPad Pro 12.9":
            return .iPadPro12_9
        #elseif os(tvOS)
        case "Apple TV":
            return .tv
        #endif
        default: return nil
        }
    }

    func snapshotDevice() -> DeviceConfig? {
        (self.snapshotDevice())?.deviceConfig
    }
}