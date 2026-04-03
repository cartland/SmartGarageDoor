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

step "Spotless (formatting)"
$GRADLE :androidApp:spotlessCheck && pass "spotlessCheck" || fail "spotlessCheck"

step "Detekt (static analysis)"
$GRADLE :androidApp:detekt && pass "detekt" || fail "detekt"

step "Android Lint"
$GRADLE :androidApp:lint && pass "lint" || fail "lint"

step "Unit Tests (debug)"
$GRADLE :androidApp:testDebugUnitTest && pass "testDebugUnitTest" || fail "testDebugUnitTest"

step "Unit Tests (release)"
$GRADLE :androidApp:testReleaseUnitTest && pass "testReleaseUnitTest" || fail "testReleaseUnitTest"

step "Unit Tests (benchmark)"
$GRADLE :androidApp:testBenchmarkUnitTest && pass "testBenchmarkUnitTest" || fail "testBenchmarkUnitTest"

step "Build Debug APK"
$GRADLE :androidApp:assembleDebug && pass "assembleDebug" || fail "assembleDebug"

# Record successful validation so git-guardrails hook knows we validated.
git rev-parse HEAD > "$REPO_ROOT/.claude/.validation-passed"

echo -e "\n${GREEN}${BOLD}All checks passed.${RESET}"
