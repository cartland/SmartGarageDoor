#!/usr/bin/env bash
set -euo pipefail

# iOS launch smoke test — install the built GarageControl.app on a simulator,
# cold-launch it, assert the process survives, screenshot, then warm-relaunch
# and assert again. Prints "IOS LAUNCH SMOKE: PASS" on success (trust this
# marker, per the repo's affirmative-success-marker rule).
#
# WHY THIS EXISTS
#
# Before this script, no CI or release step ever LAUNCHED the app — ios-ci.yml
# and release-ios.yml only compile/archive, so an app that builds cleanly but
# crashes at launch (DI-graph init failure, dyld missing-symbol, a throwing
# AppDelegate path, Kotlin/Native release-mode-only crash) sailed straight to
# TestFlight. A TestFlight build in the ios/7 era shipped exactly that way.
# This script is the gate: release-ios.yml runs it against a Release-config
# simulator build BEFORE archiving, ios-ci.yml and validate-ios.sh run it
# against the Debug build on every iOS change.
#
# WHAT IT COVERS / DOESN'T
#
#   Covers: signed-out cold launch + warm relaunch, Debug and Release
#   (Kotlin/Native release binary + Swift -O on the release lane).
#   Residual gaps (documented in docs/IOS_RELEASE_SETUP.md § Launch smoke
#   gate): signed-in-only crashes (no Google credentials in CI), device-only
#   crashes (simulator artifact), and upgrade-from-old-build state.
#
# USAGE
#
#   scripts/ios-launch-smoke.sh [APP_PATH] [--configuration Debug|Release] \
#       [--screenshot-dir DIR]
#
#   APP_PATH defaults to the newest built
#   DerivedData/GarageControl-*/Build/Products/<CONFIG>-iphonesimulator/GarageControl.app
#   SMOKE_WAIT_SECONDS (env, default 10) is how long the process must survive.

RED='\033[0;31m'
GREEN='\033[0;32m'
BOLD='\033[1m'
RESET='\033[0m'

CONFIG="Debug"
APP_PATH=""
SHOT_DIR=""
WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-10}"

while [ $# -gt 0 ]; do
    case "$1" in
        --configuration)
            CONFIG="$2"
            shift 2
            ;;
        --screenshot-dir)
            SHOT_DIR="$2"
            shift 2
            ;;
        -h | --help)
            grep '^#' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            APP_PATH="$1"
            shift
            ;;
    esac
done

# Marker file for scoping the crash-report scan to THIS run.
START_MARKER=$(mktemp)
trap 'rm -f "$START_MARKER"' EXIT

fail() {
    echo -e "${RED}[FAIL] IOS LAUNCH SMOKE: $1${RESET}" >&2
    exit 1
}

# On failure, surface WHY: dump any crash report the launch produced. Simulator
# app crashes land on the host at ~/Library/Logs/DiagnosticReports/<Name>-*.ips.
dump_crash_reports() {
    local reports_dir="$HOME/Library/Logs/DiagnosticReports"
    local found=0
    local report
    while IFS= read -r report; do
        found=1
        echo -e "${BOLD}--- crash report: $report (first 120 lines) ---${RESET}"
        head -120 "$report"
        echo -e "${BOLD}--- end crash report ---${RESET}"
    done < <(find "$reports_dir" -maxdepth 1 -name 'GarageControl-*.ips' -newer "$START_MARKER" 2>/dev/null)
    if [ "$found" -eq 0 ]; then
        echo "No GarageControl crash report found under $reports_dir (process may have been killed externally, or the crash produced no report)."
    fi
}

# --- Locate the app bundle ---
if [ -z "$APP_PATH" ]; then
    APP_PATH=$(find "$HOME/Library/Developer/Xcode/DerivedData" -maxdepth 5 \
        -path "*/Build/Products/$CONFIG-iphonesimulator/GarageControl.app" -print0 2>/dev/null |
        xargs -0 stat -f '%m %N' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)
fi
[ -n "$APP_PATH" ] && [ -d "$APP_PATH" ] ||
    fail "no GarageControl.app found for configuration '$CONFIG'. Build it first (xcodebuild -configuration $CONFIG -sdk iphonesimulator) or pass the .app path explicitly."

BUNDLE_ID=$(plutil -extract CFBundleIdentifier raw "$APP_PATH/Info.plist") ||
    fail "could not read CFBundleIdentifier from $APP_PATH/Info.plist"

echo -e "${BOLD}=== iOS launch smoke ($CONFIG) ===${RESET}"
echo "App:    $APP_PATH"
echo "Bundle: $BUNDLE_ID"

# --- Pick a simulator: an already-booted iPhone, else the newest-runtime
# available iPhone (booted here). ---
UDID=$(xcrun simctl list devices booted | grep -m1 "iPhone" | grep -oE '[0-9A-F]{8}-[0-9A-F-]{27}' || true)
if [ -z "$UDID" ]; then
    UDID=$(xcrun simctl list devices available -j | python3 -c '
import json, re, sys

data = json.load(sys.stdin)
best = None
for runtime, devices in data["devices"].items():
    match = re.search(r"iOS-(\d+)-(\d+)$", runtime)
    if not match:
        continue
    version = (int(match.group(1)), int(match.group(2)))
    for device in devices:
        if not device.get("isAvailable") or "iPhone" not in device["name"]:
            continue
        key = (version, device["name"])
        if best is None or key > best[0]:
            best = (key, device["udid"])
print(best[1] if best else "")
')
    [ -n "$UDID" ] || fail "no available iPhone simulator found (xcrun simctl list devices available)."
    echo "Booting simulator $UDID..."
    xcrun simctl boot "$UDID" 2>/dev/null || true
fi
xcrun simctl bootstatus "$UDID" -b >/dev/null || fail "simulator $UDID did not finish booting."
echo "Sim:    $UDID"

# --- Fresh install ---
xcrun simctl uninstall "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$UDID" "$APP_PATH" || fail "simctl install failed."

screenshot() {
    local label="$1"
    [ -n "$SHOT_DIR" ] || return 0
    mkdir -p "$SHOT_DIR"
    xcrun simctl io "$UDID" screenshot "$SHOT_DIR/launch-$label.png" >/dev/null 2>&1 ||
        echo "WARN: screenshot for '$label' failed (non-fatal)."
}

# Launch the app and assert its process is still alive after WAIT_SECONDS.
# The PID printed by `simctl launch` is a host process, so `kill -0` sees it.
launch_and_assert() {
    local label="$1"
    local out pid
    out=$(xcrun simctl launch "$UDID" "$BUNDLE_ID") || fail "$label launch: simctl launch failed."
    pid="${out##*: }"
    case "$pid" in
        '' | *[!0-9]*) fail "$label launch: could not parse PID from simctl output: $out" ;;
    esac
    echo "[$label] launched as PID $pid; waiting ${WAIT_SECONDS}s..."
    sleep "$WAIT_SECONDS"
    if ! kill -0 "$pid" 2>/dev/null; then
        screenshot "$label-FAILED"
        dump_crash_reports
        fail "$label launch: app process $pid died within ${WAIT_SECONDS}s of launch."
    fi
    screenshot "$label"
    echo -e "${GREEN}[$label] PASS — process $pid alive after ${WAIT_SECONDS}s.${RESET}"
}

# Cold launch (fresh install, no app data), then warm relaunch (data present) —
# the two can differ (empirical: the #1055 fresh-install-only render bug).
launch_and_assert "cold"
xcrun simctl terminate "$UDID" "$BUNDLE_ID" || fail "could not terminate app between cold and warm launch."
launch_and_assert "warm"

echo -e "${GREEN}${BOLD}IOS LAUNCH SMOKE: PASS${RESET} (cold + warm launch, $CONFIG)"
