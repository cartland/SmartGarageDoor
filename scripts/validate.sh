#!/usr/bin/env bash
set -euo pipefail

# Local validation script — mirrors CI checks.
# Run before pushing to catch issues early.
#
# Automatically discovers all modules with test sources so new
# modules are covered without editing this script.

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

step "Import boundary check (shared modules)"
$GRADLE :domain:checkImportBoundary :data:checkImportBoundary :data-local:checkImportBoundary :usecase:checkImportBoundary :presentation-model:checkImportBoundary && pass "import boundaries" || fail "import boundaries"

step "No fully qualified names in code"
$GRADLE checkNoFullyQualifiedNames && pass "no FQNs" || fail "no FQNs"

step "No Navigation 2 imports (use Nav3)"
$GRADLE checkNoNav2Imports && pass "no Nav2" || fail "no Nav2"

step "RememberSaveable guard (no unsaved custom types)"
$GRADLE checkRememberSaveable && pass "rememberSaveable guard" || fail "rememberSaveable guard"

step "Architecture (module dependency graph)"
$GRADLE checkArchitecture && pass "architecture" || fail "architecture"

step "Singleton guard (Database, Settings, HttpClient)"
$GRADLE checkSingletonGuard && pass "singleton guard" || fail "singleton guard"

step "Singleton caching (kotlin-inject generated _scoped.get matches @Singleton)"
$GRADLE checkSingletonCaching && pass "singleton caching" || fail "singleton caching"

step "DataStore + Room singleton annotations (provideAppSettings, provideAppDatabase)"
$GRADLE checkDataStoreSingleton && pass "datastore singleton" || fail "datastore singleton"

step "No raw Dispatchers (ADR-005 — VM/UseCase must inject DispatcherProvider)"
$GRADLE checkNoRawDispatchers && pass "no raw dispatchers" || fail "no raw dispatchers"

step "No bare top-level functions (ADR-009 — group in object {})"
$GRADLE checkNoBareTopLevelFunctions && pass "no bare top-level functions" || fail "no bare top-level functions"

step "No *Impl suffix on class names (ADR-008 — use descriptive prefix)"
$GRADLE checkNoImplSuffix && pass "no *Impl suffix" || fail "no *Impl suffix"

step "No idToken parameter on UseCases (ADR-027 — token internal to repos)"
$GRADLE checkNoTokenInUseCase && pass "no token in UseCase" || fail "no token in UseCase"

step "No idToken parameter on domain repository interfaces (ADR-027)"
$GRADLE checkRepositoryInterfaceNoToken && pass "no token in repo interface" || fail "no token in repo interface"

step "Every NavDisplay entry uses RouteContent (single source of horizontal layout)"
$GRADLE checkRouteContentUsage && pass "RouteContent usage" || fail "RouteContent usage"

step "ContentWidth cap applied only at RouteContent (no per-screen widthIn(max))"
$GRADLE checkContentWidthCap && pass "ContentWidth cap" || fail "ContentWidth cap"

step "No Mockito imports (ADR-003 — fakes over mocks)"
$GRADLE checkNoMockitoImports && pass "no Mockito imports" || fail "no Mockito imports"

step "Mutex withLock (no bare .lock()/.unlock())"
$GRADLE checkMutexWithLock && pass "mutex withLock" || fail "mutex withLock"

step "Layer imports (ViewModel→UseCase, UseCase→domain)"
$GRADLE checkLayerImports && pass "layer imports" || fail "layer imports"

step "Hardcoded colors (must use theme)"
$GRADLE checkHardcodedColors && pass "hardcoded colors" || fail "hardcoded colors"

step "ViewModel StateFlow (no stateIn(viewModelScope, ...))"
$GRADLE checkViewModelStateFlow && pass "viewmodel stateflow" || fail "viewmodel stateflow"

step "Screen ↔ ViewModel cardinality (1 VM per screen, exemptions tracked)"
$GRADLE checkScreenViewModelCardinality && pass "screen↔viewmodel cardinality" || fail "screen↔viewmodel cardinality"

step "Preview coverage (every public *Preview is imported by a screenshot test)"
$GRADLE checkPreviewCoverage && pass "preview coverage" || fail "preview coverage"

step "Fake public var (no unguarded public var on Fake* classes)"
$GRADLE checkFakePublicVar && pass "fake public var" || fail "fake public var"

step "Detekt (static analysis)"
$GRADLE :androidApp:detekt && pass "detekt" || fail "detekt"

step "Android Lint"
$GRADLE :androidApp:lint && pass "lint" || fail "lint"

# Discover all non-Android modules with test sources and run their tests.
# Checks both src/test/ (pure JVM) and src/commonTest/ (KMP).
# Android modules (androidApp) use variant-specific test tasks handled below.
step "Shared module tests (auto-discovered)"
FOUND_MODULES=0
for module_dir in "$REPO_ROOT"/AndroidGarage/*/; do
    module=$(basename "$module_dir")
    # Skip androidApp — it has variant-specific test tasks
    [ "$module" = "androidApp" ] && continue
    # Skip non-Gradle directories
    [ -f "$module_dir/build.gradle.kts" ] || [ -f "$module_dir/build.gradle" ] || continue
    # Check for test sources in either src/test/ or src/commonTest/
    has_tests=false
    [ -d "$module_dir/src/test" ] && has_tests=true
    [ -d "$module_dir/src/commonTest" ] && has_tests=true
    [ "$has_tests" = true ] || continue
    FOUND_MODULES=$((FOUND_MODULES + 1))
    # KMP Android library modules use testDebugUnitTest
    $GRADLE ":${module}:testDebugUnitTest" && pass ":${module}:testDebugUnitTest" || fail ":${module}:testDebugUnitTest"
done
if [ "$FOUND_MODULES" -eq 0 ]; then
    echo "  (no shared modules with tests found)"
fi

step "Unit Tests (debug)"
$GRADLE :androidApp:testDebugUnitTest && pass "testDebugUnitTest" || fail "testDebugUnitTest"

step "Unit Tests (release)"
$GRADLE :androidApp:testReleaseUnitTest && pass "testReleaseUnitTest" || fail "testReleaseUnitTest"

step "Unit Tests (benchmark)"
$GRADLE :androidApp:testBenchmarkUnitTest && pass "testBenchmarkUnitTest" || fail "testBenchmarkUnitTest"

step "Build Debug APK"
$GRADLE :androidApp:assembleDebug && pass "assembleDebug" || fail "assembleDebug"

step "Screenshot tests (compile)"
$GRADLE :android-screenshot-tests:compileDebugScreenshotTestKotlin \
    && pass "screenshot test compilation" || fail "screenshot test compilation"

# androidTest sources compile here (no device / runtime needed). Catches
# signature-breaking changes to public Composables that test sources call.
# Closes the gap that produced #604: PR #603 changed HomeContent's signature,
# AuthStateUIPropagationTest still called the old shape, and the failure only
# surfaced in post-merge CI's instrumented-tests job. Compile is fast (~2s).
step "Instrumented tests (compile)"
$GRADLE :androidApp:compileDebugAndroidTestKotlin \
    && pass "instrumented test compilation" || fail "instrumented test compilation"

step "Documentation front-matter (AGENTS.md contract)"
"$REPO_ROOT/scripts/check-doc-frontmatter.sh" \
    && pass "doc front-matter" || fail "doc front-matter"

step "Play Store whatsnew length (Google Play 500-char limit)"
"$REPO_ROOT/scripts/check-whatsnew-length.sh" \
    && pass "whatsnew length" || fail "whatsnew length"

step "Room schema drift check"
# After compilation, Room KSP generates schema JSON files.
# If they differ from what's committed, the schema changed without being tracked.
SCHEMA_DIR="$REPO_ROOT/AndroidGarage/data-local/schemas"
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

# ------------------------------------------------------------------
# Optional: R8 instrumented tests
# ------------------------------------------------------------------
# Runs connectedBenchmarkAndroidTest against an R8-minified APK so the
# release-only failure class (kotlinx.serialization Companion keeps,
# proto keepers, etc.) can be reproduced locally before tagging.
#
# Opt-in because it needs a connected device/emulator and takes several
# minutes:
#   $ VALIDATE_R8=1 ./scripts/validate.sh
#
# Without the flag this block is skipped with a short notice so the
# default run stays fast and device-independent. The release script
# (`scripts/release-android.sh`) is the place to make this a hard gate
# when cutting an Android release.
#
# Harness background: AndroidGarage/docs/R8_INSTRUMENTED_TESTS.md
# and ADR-020 (R8 release-build hardening).
if [ "${VALIDATE_R8:-0}" = "1" ]; then
    step "R8 instrumented tests (opt-in via VALIDATE_R8=1)"
    if ! command -v adb >/dev/null 2>&1; then
        fail "adb not found on PATH — R8 instrumented tests need it"
    fi
    if ! adb devices 2>/dev/null | grep -q "device$"; then
        fail "No device/emulator connected. Plug in a device or start an emulator, then retry."
    fi
    $GRADLE \
        -PtestR8=true \
        -PdebuggableBenchmark=true \
        -PVERSION_CODE=99999 \
        :androidApp:connectedBenchmarkAndroidTest \
        && pass "connectedBenchmarkAndroidTest (R8 minified)" \
        || fail "connectedBenchmarkAndroidTest (R8 minified)"
else
    step "R8 instrumented tests (skipped — opt-in via VALIDATE_R8=1)"
    echo "  To reproduce release-only R8 failures locally, run:"
    echo "    VALIDATE_R8=1 ./scripts/validate.sh"
    echo "  Requires a connected device/emulator. See docs/R8_INSTRUMENTED_TESTS.md"
fi

# Record successful validation so git-guardrails hook knows we validated.
git rev-parse HEAD > "$REPO_ROOT/.claude/.validation-passed"

echo -e "\n${GREEN}${BOLD}All checks passed.${RESET}"
