#!/usr/bin/env bash
set -euo pipefail

# Validate the iOS app locally — a CI-exact mirror of .github/workflows/ios-ci.yml
# ("Build iOS app + framework test"). Run this before pushing iOS changes.
#
# WHY THIS EXISTS
#
# iOS CI is NOT a required status check, so a PR with auto-merge on can merge
# before the iOS run finishes — a red only surfaces post-merge on main (see
# CLAUDE.md § "iOS local verification ... the non-required-iOS-CI trap"). Running
# this script first is the mitigation: it reproduces exactly what CI does, so an
# iOS break is caught before the push instead of after the merge.
#
# WHAT IT RUNS (identical to ios-ci.yml, in the same order)
#   1. :iosFramework:iosSimulatorArm64Test  — the NativeComponent DI-graph
#      identity test (40 cases); also warms the shared.framework the app embeds.
#   2. xcodegen generate                     — regenerate the (gitignored) .xcodeproj.
#   3. xcodebuild ... build                  — compile the SwiftUI app against the
#      framework for a generic iOS Simulator destination, signing disabled.
#
# This does NOT run the snapshot-gallery tests (Prefire) — those are
# regenerate-don't-assert and are not a gating CI check. Regenerate them with
# ./scripts/generate-ios-screenshots.sh when a view changes.
#
# CI-EXACT BUILD NOTE: do NOT pass OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES here.
# CI doesn't set it, and the generic 'iOS Simulator' destination needs the
# framework rebuilt for the destination's arch(s) — the override skips that
# Gradle rebuild and the build then fails with "cannot find type ... in scope"
# (CLAUDE.md § "CI-exact iOS build trap").

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

REPO_ROOT="$(git rev-parse --show-toplevel)"
IOS_APP_DIR="$REPO_ROOT/MobileGarage/iosApp"
XCODEPROJ="$IOS_APP_DIR/GarageControl.xcodeproj"
PROJECT_SPEC="$IOS_APP_DIR/project.yml"

fail() {
    echo -e "${RED}[FAIL] $1${RESET}" >&2
    exit 1
}

# --- Preconditions: macOS + the iOS toolchain (this is a no-op on Linux CI). ---
if [ "$(uname -s)" != "Darwin" ]; then
    fail "iOS validation requires macOS (Xcode + xcodegen). Current OS: $(uname -s)."
fi
command -v xcodegen >/dev/null 2>&1 || fail "xcodegen not found. Install with: brew install xcodegen"
command -v xcodebuild >/dev/null 2>&1 || fail "xcodebuild not found. Install Xcode and run: xcode-select --install"

echo -e "${BOLD}=== iOS Validation (mirrors ios-ci.yml) ===${RESET}"
echo ""

# --- Step 1: iosFramework simulator test (DI-graph identity) ---
# Flake note (CLAUDE.md): a red here whose log shows "The daemon has terminated
# unexpectedly on startup" with NO "Test Case ... failed" is a Gradle-daemon
# infra flake, not a regression — rerun with --rerun-tasks to confirm.
echo -e "${BOLD}[1/3] iosFramework simulator tests (:iosFramework:iosSimulatorArm64Test)${RESET}"
"$REPO_ROOT/MobileGarage/gradlew" -p "$REPO_ROOT/MobileGarage" :iosFramework:iosSimulatorArm64Test \
    || fail "iosFramework simulator tests failed."
echo -e "${GREEN}[PASS] iosFramework simulator tests${RESET}"
echo ""

# --- Step 2: regenerate the Xcode project from project.yml ---
echo -e "${BOLD}[2/3] Generate Xcode project (xcodegen)${RESET}"
xcodegen generate --spec "$PROJECT_SPEC" --project "$IOS_APP_DIR" \
    || fail "xcodegen generate failed."
echo -e "${GREEN}[PASS] xcodegen generate${RESET}"
echo ""

# --- Step 3: build the SwiftUI app for the simulator (signing disabled) ---
echo -e "${BOLD}[3/3] Build iOS app (xcodebuild, generic iOS Simulator)${RESET}"
xcodebuild \
    -project "$XCODEPROJ" \
    -scheme GarageControl \
    -configuration Debug \
    -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' \
    build \
    CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO \
    || fail "iOS app build failed."
echo -e "${GREEN}[PASS] iOS app build${RESET}"
echo ""

# --- Write the validation marker (read by scripts/release-ios.sh) ---
# Records the HEAD SHA that passed, so the release script can confirm the tag
# is being cut on a validated commit. Kept separate from the Android marker
# (.claude/.validation-passed) so validating one platform doesn't clear the other.
mkdir -p "$REPO_ROOT/.claude"
git -C "$REPO_ROOT" rev-parse HEAD > "$REPO_ROOT/.claude/.ios-validation-passed"

echo -e "${GREEN}${BOLD}All iOS checks passed.${RESET}"
echo "Wrote validation marker for $(git -C "$REPO_ROOT" rev-parse --short HEAD) (.claude/.ios-validation-passed)."
