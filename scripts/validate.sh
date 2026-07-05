#!/usr/bin/env bash
set -euo pipefail

# Local validation script — mirrors CI checks.
# Run before pushing to catch issues early.
#
# Automatically discovers all modules with test sources so new
# modules are covered without editing this script.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$REPO_ROOT/MobileGarage/gradlew -p $REPO_ROOT/MobileGarage"

RED='\033[0;31m'
GREEN='\033[0;32m'
BOLD='\033[1m'
RESET='\033[0m'

pass() { echo -e "${GREEN}PASS${RESET} $1"; }
fail() { echo -e "${RED}FAIL${RESET} $1"; exit 1; }
step() { echo -e "\n${BOLD}--- $1 ---${RESET}"; }

step "Spotless (formatting — all modules)"
if $GRADLE spotlessCheck; then
    pass "spotlessCheck"
else
    fail "spotlessCheck"
fi

step "Import boundary check (shared modules)"
if $GRADLE :domain:checkImportBoundary :data:checkImportBoundary :data-local:checkImportBoundary :usecase:checkImportBoundary :presentation-model:checkImportBoundary; then
    pass "import boundaries"
else
    fail "import boundaries"
fi

step "No fully qualified names in code"
if $GRADLE checkNoFullyQualifiedNames; then
    pass "no FQNs"
else
    fail "no FQNs"
fi

step "No Navigation 2 imports (use Nav3)"
if $GRADLE checkNoNav2Imports; then
    pass "no Nav2"
else
    fail "no Nav2"
fi

step "UI layer routes through ViewModel (no direct component.UseCase/Repository)"
if $GRADLE checkUiLayerNoGraphAccess; then
    pass "ui layer no graph access"
else
    fail "ui layer no graph access"
fi

step "RememberSaveable guard (no unsaved custom types)"
if $GRADLE checkRememberSaveable; then
    pass "rememberSaveable guard"
else
    fail "rememberSaveable guard"
fi

step "Architecture (module dependency graph)"
if $GRADLE checkArchitecture; then
    pass "architecture"
else
    fail "architecture"
fi

step "Singleton guard (Database, Settings, HttpClient)"
if $GRADLE checkSingletonGuard; then
    pass "singleton guard"
else
    fail "singleton guard"
fi

step "Singleton caching (kotlin-inject generated _scoped.get matches @Singleton)"
if $GRADLE checkSingletonCaching; then
    pass "singleton caching"
else
    fail "singleton caching"
fi

step "DataStore + Room singleton annotations (provideAppSettings, provideAppDatabase)"
if $GRADLE checkDataStoreSingleton; then
    pass "datastore singleton"
else
    fail "datastore singleton"
fi

step "No raw Dispatchers (ADR-005 — VM/UseCase must inject DispatcherProvider)"
if $GRADLE checkNoRawDispatchers; then
    pass "no raw dispatchers"
else
    fail "no raw dispatchers"
fi

step "No bare top-level functions (ADR-009 — group in object {})"
if $GRADLE checkNoBareTopLevelFunctions; then
    pass "no bare top-level functions"
else
    fail "no bare top-level functions"
fi

step "No literal Text() in Composable scope (Phase 3 — string-resource migration)"
if $GRADLE checkNoLiteralStringsInCompose; then
    pass "no literal Text() in Composable"
else
    fail "no literal Text() in Composable"
fi

step "No *Impl suffix on class names (ADR-008 — use descriptive prefix)"
if $GRADLE checkNoImplSuffix; then
    pass "no *Impl suffix"
else
    fail "no *Impl suffix"
fi

step "No 'Ui' / 'View' in class names (KMP collision; ported from battery-butler)"
if $GRADLE checkNamingConvention; then
    pass "naming convention"
else
    fail "naming convention"
fi

step "Previews don't transitively use Clock.System.now() / Instant.now() (deterministic screenshots)"
if $GRADLE checkPreviewTime; then
    pass "preview time determinism"
else
    fail "preview time determinism"
fi

step "isSystemInDarkTheme() only inside theme module (theme-detection isolation)"
if $GRADLE checkThemeDetection; then
    pass "theme detection isolation"
else
    fail "theme detection isolation"
fi

step "No idToken parameter on UseCases (ADR-027 — token internal to repos)"
if $GRADLE checkNoTokenInUseCase; then
    pass "no token in UseCase"
else
    fail "no token in UseCase"
fi

step "No idToken parameter on domain repository interfaces (ADR-027)"
if $GRADLE checkRepositoryInterfaceNoToken; then
    pass "no token in repo interface"
else
    fail "no token in repo interface"
fi

step "Every NavDisplay entry uses RouteContent (single source of horizontal layout)"
if $GRADLE checkRouteContentUsage; then
    pass "RouteContent usage"
else
    fail "RouteContent usage"
fi

step "ContentWidth cap applied only at RouteContent (no per-screen widthIn(max))"
if $GRADLE checkContentWidthCap; then
    pass "ContentWidth cap"
else
    fail "ContentWidth cap"
fi

step "No LocalConfiguration dimension reads (use LocalAppWindowSizeClass)"
if $GRADLE checkNoLocalConfigurationDimensionReads; then
    pass "no LocalConfiguration dim reads"
else
    fail "no LocalConfiguration dim reads"
fi

step "AppLayoutMode boundary (no direct LocalAppWindowSizeClass.current reads)"
if $GRADLE checkAppLayoutModeBoundary; then
    pass "AppLayoutMode boundary"
else
    fail "AppLayoutMode boundary"
fi

step "No raw WindowInsets.<x>.asPaddingValues() (use LocalContentEdgeInsets / safeListContentPadding)"
if $GRADLE checkNoRawSafeDrawingPaddingValues; then
    pass "no raw asPaddingValues"
else
    fail "no raw asPaddingValues"
fi

step "No Mockito imports (ADR-003 — fakes over mocks)"
if $GRADLE checkNoMockitoImports; then
    pass "no Mockito imports"
else
    fail "no Mockito imports"
fi

step "Mutex withLock (no bare .lock()/.unlock())"
if $GRADLE checkMutexWithLock; then
    pass "mutex withLock"
else
    fail "mutex withLock"
fi

step "Auth state projection (no raw authState in side-effecting combine watchers)"
if $GRADLE checkAuthStateProjection; then
    pass "authState projection"
else
    fail "authState projection"
fi

step "Layer imports (ViewModel→UseCase, UseCase→domain)"
if $GRADLE checkLayerImports; then
    pass "layer imports"
else
    fail "layer imports"
fi

step "Hardcoded colors (must use theme)"
if $GRADLE checkHardcodedColors; then
    pass "hardcoded colors"
else
    fail "hardcoded colors"
fi

step "ViewModel StateFlow (no stateIn(viewModelScope, ...))"
if $GRADLE checkViewModelStateFlow; then
    pass "viewmodel stateflow"
else
    fail "viewmodel stateflow"
fi

step "Screen ↔ ViewModel cardinality (1 VM per screen, exemptions tracked)"
if $GRADLE checkScreenViewModelCardinality; then
    pass "screen↔viewmodel cardinality"
else
    fail "screen↔viewmodel cardinality"
fi

step "Preview coverage (every public *Preview is imported by a screenshot test)"
if $GRADLE checkPreviewCoverage; then
    pass "preview coverage"
else
    fail "preview coverage"
fi

step "Fake public var (no unguarded public var on Fake* classes)"
if $GRADLE checkFakePublicVar; then
    pass "fake public var"
else
    fail "fake public var"
fi

step "Detekt (static analysis)"
if $GRADLE :androidApp:detekt; then
    pass "detekt"
else
    fail "detekt"
fi

step "Android Lint"
if $GRADLE :androidApp:lint; then
    pass "lint"
else
    fail "lint"
fi

# Discover all non-Android modules with test sources and run their tests.
# Checks both src/test/ (pure JVM) and src/commonTest/ (KMP).
# Android modules (androidApp) use variant-specific test tasks handled below.
step "Shared module tests (auto-discovered)"
FOUND_MODULES=0
for module_dir in "$REPO_ROOT"/MobileGarage/*/; do
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
    if $GRADLE ":${module}:testDebugUnitTest"; then
        pass ":${module}:testDebugUnitTest"
    else
        fail ":${module}:testDebugUnitTest"
    fi
done
if [ "$FOUND_MODULES" -eq 0 ]; then
    echo "  (no shared modules with tests found)"
fi

step "Unit Tests (debug)"
if $GRADLE :androidApp:testDebugUnitTest; then
    pass "testDebugUnitTest"
else
    fail "testDebugUnitTest"
fi

step "Unit Tests (release)"
if $GRADLE :androidApp:testReleaseUnitTest; then
    pass "testReleaseUnitTest"
else
    fail "testReleaseUnitTest"
fi

step "Unit Tests (benchmark)"
if $GRADLE :androidApp:testBenchmarkUnitTest; then
    pass "testBenchmarkUnitTest"
else
    fail "testBenchmarkUnitTest"
fi

step "Build Debug APK"
if $GRADLE :androidApp:assembleDebug; then
    pass "assembleDebug"
else
    fail "assembleDebug"
fi

step "Screenshot tests (compile)"
if $GRADLE :android-screenshot-tests:compileDebugScreenshotTestKotlin; then
    pass "screenshot test compilation"
else
    fail "screenshot test compilation"
fi

# androidTest sources compile here (no device / runtime needed). Catches
# signature-breaking changes to public Composables that test sources call.
# Closes the gap that produced #604: PR #603 changed HomeContent's signature,
# AuthStateUIPropagationTest still called the old shape, and the failure only
# surfaced in post-merge CI's instrumented-tests job. Compile is fast (~2s).
step "Instrumented tests (compile)"
if $GRADLE :androidApp:compileDebugAndroidTestKotlin; then
    pass "instrumented test compilation"
else
    fail "instrumented test compilation"
fi

step "Documentation front-matter (AGENTS.md contract)"
if "$REPO_ROOT/scripts/check-doc-frontmatter.sh"; then
    pass "doc front-matter"
else
    fail "doc front-matter"
fi

step "Play Store whatsnew length (Google Play 500-char limit)"
if "$REPO_ROOT/scripts/check-whatsnew-length.sh"; then
    pass "whatsnew length"
else
    fail "whatsnew length"
fi

step "Room schema drift check"
# After compilation, Room KSP generates schema JSON files.
# If they differ from what's committed, the schema changed without being tracked.
SCHEMA_DIR="$REPO_ROOT/MobileGarage/data-local/schemas"
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
# Harness background: MobileGarage/docs/R8_INSTRUMENTED_TESTS.md
# and ADR-020 (R8 release-build hardening).
if [ "${VALIDATE_R8:-0}" = "1" ]; then
    step "R8 instrumented tests (opt-in via VALIDATE_R8=1)"
    if ! command -v adb >/dev/null 2>&1; then
        fail "adb not found on PATH — R8 instrumented tests need it"
    fi
    if ! adb devices 2>/dev/null | grep -q "device$"; then
        fail "No device/emulator connected. Plug in a device or start an emulator, then retry."
    fi
    if $GRADLE \
        -PtestR8=true \
        -PdebuggableBenchmark=true \
        -PVERSION_CODE=99999 \
        :androidApp:connectedBenchmarkAndroidTest; then
        pass "connectedBenchmarkAndroidTest (R8 minified)"
    else
        fail "connectedBenchmarkAndroidTest (R8 minified)"
    fi
else
    step "R8 instrumented tests (skipped — opt-in via VALIDATE_R8=1)"
    echo "  To reproduce release-only R8 failures locally, run:"
    echo "    VALIDATE_R8=1 ./scripts/validate.sh"
    echo "  Requires a connected device/emulator. See docs/R8_INSTRUMENTED_TESTS.md"
fi

# Record successful validation so git-guardrails hook knows we validated.
git rev-parse HEAD > "$REPO_ROOT/.claude/.validation-passed"

echo -e "\n${GREEN}${BOLD}All checks passed.${RESET}"
