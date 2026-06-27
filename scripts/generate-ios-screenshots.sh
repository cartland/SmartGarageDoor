#!/bin/bash
#
# Regenerate the iOS reference screenshots — a browsable visual gallery of every
# SwiftUI #Preview in the iOS app — via Prefire + swift-snapshot-testing.
#
# Posture: "regenerate, don't assert" (mirrors scripts/generate-android-
# screenshots.sh). The references under SnapshotTests/__Snapshots__/ are deleted
# and re-recorded; swift-snapshot-testing's record-on-missing path reports each
# new snapshot as a test "failure", which is EXPECTED and ignored. These PNGs are
# a visual reference, NOT a pixel-perfect gating test. See the ADR on the iOS
# snapshot gallery (AndroidGarage/docs/DECISIONS.md).
#
# Pipeline:
#   1. Resolve SPM packages into a known dir and locate the prebuilt `prefire` CLI.
#   2. `prefire tests` scans Features/ + Core/ for #Preview and (re)generates
#      SnapshotTests/PreviewTests.generated.swift.
#   3. Regenerate the Xcode project (picks up any new test functions / sources).
#   4. Delete the old references and run the snapshot test target on a simulator
#      (record-on-missing); the non-zero "record" exit is ignored.
#   5. Build SCREENSHOT_GALLERY.md from the recorded PNGs.
#
# Run from the repository root: ./scripts/generate-ios-screenshots.sh
#
# Env:
#   IOS_SNAPSHOT_SIMULATOR   simulator name (default "iPhone 16"). The snapshot
#                            layout is fixed by the generated test's DeviceConfig,
#                            so the actual simulator only needs to exist.
#   OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES  skip the Gradle shared.framework
#                            rebuild (fast path when the framework is already built).

set -uo pipefail

if [ ! -d "AndroidGarage/iosApp" ]; then
  echo "ERROR: run from the repository root (AndroidGarage/iosApp not found)." >&2
  exit 1
fi

PROJ="AndroidGarage/iosApp"
XCODEPROJ="$PROJ/iosApp.xcodeproj"
SCHEME="iosApp"
SNAP_DIR="$PROJ/SnapshotTests"
# Persistent, known DerivedData so the SPM checkouts (and the prebuilt `prefire`
# CLI binary inside the Prefire package) live in one reusable location.
DD="$PROJ/.derivedData-snapshots"
LOG="${TMPDIR:-/tmp}/ios-snapshots.log"

# Resolve a concrete simulator destination. The snapshot layout is fixed by the
# generated test's DeviceConfig, so any iPhone simulator produces identical
# output — pick whatever is available. Order: explicit override, a booted
# iPhone, else a fallback name (xcodebuild boots it).
#   IOS_SNAPSHOT_DESTINATION  full -destination string (overrides everything)
#   IOS_SNAPSHOT_SIMULATOR    fallback device name (default "iPhone 16 Pro")
DEST="${IOS_SNAPSHOT_DESTINATION:-}"
if [ -z "$DEST" ]; then
  BOOTED_ID=$(xcrun simctl list devices booted 2>/dev/null | grep -m1 "iPhone" | grep -oE "[0-9A-F]{8}-[0-9A-F-]{27}")
  if [ -n "$BOOTED_ID" ]; then
    DEST="id=$BOOTED_ID"
  else
    DEST="platform=iOS Simulator,name=${IOS_SNAPSHOT_SIMULATOR:-iPhone 16 Pro}"
  fi
fi

# Passed through to the build. Default NO so the Gradle prebuild runs and embeds
# shared.framework into the host app (required, or the test bundle crashes at
# launch). Set YES only when the framework is already embedded in this
# derivedData (fast iteration).
OVR="${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-NO}"

echo "==> Resolving Swift packages (for the Prefire CLI)..."
xcodebuild -project "$XCODEPROJ" -scheme "$SCHEME" \
  -derivedDataPath "$DD" \
  -resolvePackageDependencies > "$LOG" 2>&1 || { tail -20 "$LOG" >&2; exit 1; }

PREFIRE="$(find "$DD/SourcePackages/checkouts/Prefire/Binaries" -type f -name prefire -path '*bin*' 2>/dev/null | head -1)"
if [ -z "$PREFIRE" ]; then
  echo "ERROR: prefire binary not found under $DD/SourcePackages/checkouts/Prefire/Binaries" >&2
  exit 1
fi
echo "    prefire: $PREFIRE"

echo "==> Generating PreviewTests from #Preview macros..."
"$PREFIRE" tests "$PROJ/Features" "$PROJ/Core" \
  --target iosApp --test-target iosAppSnapshotTests \
  --config "$PROJ/.prefire.yml" --output "$SNAP_DIR"

echo "==> Regenerating Xcode project..."
xcodegen generate --spec "$PROJ/project.yml" --project "$PROJ" > /dev/null
# Drop any stale per-user scheme so the shared (test-enabled) scheme is used.
rm -f "$XCODEPROJ"/xcuserdata/*/xcschemes/"$SCHEME".xcscheme 2>/dev/null || true

echo "==> Recording reference screenshots ($DEST; record-on-missing, 'failures' expected)..."
rm -rf "$SNAP_DIR/__Snapshots__"
xcodebuild -project "$XCODEPROJ" -scheme "$SCHEME" \
  -sdk iphonesimulator -destination "$DEST" \
  -derivedDataPath "$DD" \
  test CODE_SIGNING_ALLOWED=NO OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED="$OVR" > "$LOG" 2>&1
echo "    (test exit ignored — record mode reports new snapshots as failures)"

PNG_COUNT=$(find "$SNAP_DIR/__Snapshots__" -name '*.png' 2>/dev/null | wc -l | tr -d ' ')
if [ "$PNG_COUNT" -eq 0 ]; then
  echo "ERROR: no PNGs recorded. Last 30 log lines:" >&2
  tail -30 "$LOG" >&2
  exit 1
fi
echo "==> Recorded $PNG_COUNT reference PNG(s)."

echo "==> Generating gallery..."
python3 scripts/generate-ios-screenshot-gallery.py

echo ""
echo "Done. Browse: $SNAP_DIR/SCREENSHOT_GALLERY.md"
