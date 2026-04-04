#!/usr/bin/env bash
set -euo pipefail

# Local validation script — mirrors CI checks.
# Run before pushing to catch issues early.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$REPO_ROOT/AndroidGarage/gradlew -p $REPO_ROOT/AndroidGarage"

RED='\033[0;31m'
GREEN='\033[0;32m'
BOLD='\033[1m'
RESET='\033[0m'

pass() { echo -e "${GREEN}PASS${RESET} $1"; }
fail() { echo -e "${RED}FAIL${RESET} $1"; exit 1; }
step() { echo -e "\n${BOLD}--- $1 ---${RESET}"; }

step "Spotless (formatting — all modules)"
$GRADLE spotlessCheck && pass "spotlessCheck" || fail "spotlessCheck"

step "Detekt (static analysis)"
$GRADLE :androidApp:detekt && pass "detekt" || fail "detekt"

step "Android Lint"
$GRADLE :androidApp:lint && pass "lint" || fail "lint"

step "Domain module tests"
$GRADLE :domain:test && pass "domain:test" || fail "domain:test"

step "Unit Tests (debug)"
$GRADLE :androidApp:testDebugUnitTest && pass "testDebugUnitTest" || fail "testDebugUnitTest"

step "Unit Tests (release)"
$GRADLE :androidApp:testReleaseUnitTest && pass "testReleaseUnitTest" || fail "testReleaseUnitTest"

step "Unit Tests (benchmark)"
$GRADLE :androidApp:testBenchmarkUnitTest && pass "testBenchmarkUnitTest" || fail "testBenchmarkUnitTest"

step "Build Debug APK"
$GRADLE :androidApp:assembleDebug && pass "assembleDebug" || fail "assembleDebug"

step "Room schema drift check"
# After compilation, Room KSP generates schema JSON files.
# If they differ from what's committed, the schema changed without being tracked.
SCHEMA_DIR="$REPO_ROOT/AndroidGarage/androidApp/schemas"
if git diff --quiet -- "$SCHEMA_DIR" 2>/dev/null && \
   [ -z "$(git ls-files --others --exclude-standard -- "$SCHEMA_DIR" 2>/dev/null)" ]; then
    pass "Room schema unchanged"
else
    echo -e "${RED}FAIL${RESET} Room schema files changed after compilation!"
    echo ""
    echo "This means a Room entity or database definition was modified."
    echo "You MUST increment the database version in AppDatabase.kt."
    echo ""
    echo "Changed schema files:"
    git diff --name-only -- "$SCHEMA_DIR" 2>/dev/null
    git ls-files --others --exclude-standard -- "$SCHEMA_DIR" 2>/dev/null
    echo ""
    echo "Steps to fix:"
    echo "  1. Increment 'version' in @Database annotation (AppDatabase.kt)"
    echo "  2. Run ./gradlew :androidApp:assembleDebug to regenerate schema"
    echo "  3. Commit the new schema JSON file"
    echo ""
    fail "Room schema drift detected"
fi

# Record successful validation so git-guardrails hook knows we validated.
git rev-parse HEAD > "$REPO_ROOT/.claude/.validation-passed"

echo -e "\n${GREEN}${BOLD}All checks passed.${RESET}"
