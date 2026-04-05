#!/usr/bin/env bash
set -euo pipefail

# Run instrumented tests on a connected device or emulator.
# Not part of validate.sh — run manually when touching Room, DI, navigation, or Activity code.
#
# Requires: a connected device or running emulator (check with `adb devices`)

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$REPO_ROOT/AndroidGarage/gradlew -p $REPO_ROOT/AndroidGarage"

RED='\033[0;31m'
GREEN='\033[0;32m'
BOLD='\033[1m'
RESET='\033[0m'

# Check for connected device
if ! adb devices 2>/dev/null | grep -q "device$"; then
    echo -e "${RED}No device/emulator connected.${RESET}"
    echo "Start an emulator or connect a device, then retry."
    echo ""
    echo "  # Start an emulator:"
    echo "  emulator -avd <name> &"
    echo ""
    echo "  # Or use Gradle Managed Device (slower, no physical device needed):"
    echo "  $GRADLE :androidApp:pixel6Api34DebugAndroidTest"
    exit 1
fi

echo -e "${BOLD}Running instrumented tests on connected device...${RESET}"
echo ""

$GRADLE :androidApp:connectedDebugAndroidTest

echo ""
echo -e "${GREEN}${BOLD}All instrumented tests passed.${RESET}"
