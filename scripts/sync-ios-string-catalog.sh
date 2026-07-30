#!/usr/bin/env bash
set -euo pipefail

# Populate MobileGarage/iosApp/GarageControl/Localizable.xcstrings from the
# strings the Swift compiler extracted during a build.
#
# WHY THIS EXISTS
#
# Harvesting extracted keys into a String Catalog is an Xcode.app behavior. A
# command-line build emits one `.stringsdata` file per source file and then
# leaves the catalog untouched — so in a repo whose .xcodeproj is generated and
# gitignored, and whose whole workflow is validate-ios.sh -> xcodebuild, the
# catalog would never gain a single key. This script is the CLI equivalent of
# opening the project in Xcode.
#
# It is additive and non-destructive:
#   - new keys are appended with no translations (state: "new")
#   - existing entries keep every translation they already have
#   - keys no longer found in the build are LEFT ALONE, never deleted. A key can
#     vanish from .stringsdata because a file did not recompile this run, and
#     silently dropping a translated string is far worse than keeping a stale one.
#
# Usage:
#   ./scripts/sync-ios-string-catalog.sh            # build, then sync
#   ./scripts/sync-ios-string-catalog.sh --no-build # sync from the last build
#   ./scripts/sync-ios-string-catalog.sh --check    # fail if the catalog is out of date

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

PROJ="MobileGarage/iosApp"
XCODEPROJ="$PROJ/GarageControl.xcodeproj"
SCHEME="GarageControl"
CATALOG="$PROJ/GarageControl/Localizable.xcstrings"
DD="$PROJ/.derivedData-strings"
LOG="${TMPDIR:-/tmp}/ios-string-catalog.log"

DO_BUILD=1
CHECK_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --no-build) DO_BUILD=0 ;;
    --check) CHECK_ONLY=1 ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

if [ ! -f "$CATALOG" ]; then
  echo "No catalog at $CATALOG" >&2
  exit 1
fi

if [ "$DO_BUILD" -eq 1 ]; then
  echo "==> Generating Xcode project..."
  xcodegen generate --spec "$PROJ/project.yml" --project "$PROJ" > /dev/null

  echo "==> Building to extract strings (SWIFT_EMIT_LOC_STRINGS=YES)..."
  # A simulator build is enough; extraction is a compile-time side effect.
  xcodebuild -project "$XCODEPROJ" -scheme "$SCHEME" \
    -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$DD" \
    build CODE_SIGNING_ALLOWED=NO SWIFT_EMIT_LOC_STRINGS=YES > "$LOG" 2>&1 || {
      echo "Build failed; see $LOG" >&2
      tail -30 "$LOG" >&2
      exit 1
    }
fi

if [ ! -d "$DD" ]; then
  echo "No build output at $DD. Run without --no-build first." >&2
  exit 1
fi

echo "==> Merging extracted keys into $CATALOG..."
python3 - "$DD" "$CATALOG" "$CHECK_ONLY" "$PWD/MobileGarage/iosApp" <<'PY'
import json, pathlib, plistlib, subprocess, sys

derived_data, catalog_path, check_flag, app_root = sys.argv[1:5]
check_only = check_flag == "1"

def load(path):
    try:
        with open(path, "rb") as handle:
            return plistlib.load(handle)
    except Exception:
        raw = subprocess.run(
            ["plutil", "-convert", "json", "-o", "-", str(path)],
            capture_output=True,
        )
        if raw.returncode != 0:
            return None
        return json.loads(raw.stdout)

# Only OUR sources. The build also emits .stringsdata for every Swift package
# dependency (Firebase alone accounts for hundreds), and harvesting those would
# import their vocabulary into this app's catalog.
def is_ours(source):
    if not source.startswith(app_root):
        return False
    # SourcePackages checkouts live *inside* the derived-data dir under app_root.
    return "/.derivedData" not in source and "/SourcePackages/" not in source

found = set()
scanned = 0
for path in pathlib.Path(derived_data).rglob("*.stringsdata"):
    data = load(path)
    if not data or not is_ours(str(data.get("source", ""))):
        continue
    scanned += 1
    for table in data.get("tables", {}).values():
        # Each table is a list of {"key": ..., "comment": ..., "location": ...}.
        if isinstance(table, list):
            for entry in table:
                if isinstance(entry, dict) and entry.get("key"):
                    found.add(entry["key"])
        elif isinstance(table, dict):
            found.update(k for k in table.keys() if k)

catalog = json.loads(pathlib.Path(catalog_path).read_text())
strings = catalog.setdefault("strings", {})

added = sorted(k for k in found if k not in strings)
for key in added:
    # No "localizations" key: that is the catalog's own way of saying
    # "source language only, not yet translated".
    strings[key] = {"extractionState": "manual"}

if check_only:
    if added:
        print("Catalog is missing %d extracted key(s):" % len(added))
        for key in added[:20]:
            print("  %r" % (key,))
        if len(added) > 20:
            print("  ... and %d more" % (len(added) - 20))
        print("Run ./scripts/sync-ios-string-catalog.sh to update it.")
        sys.exit(1)
    print("Catalog up to date: %d key(s) from %d source file(s)." % (len(strings), scanned))
    sys.exit(0)

if added:
    catalog["strings"] = dict(sorted(strings.items()))
    pathlib.Path(catalog_path).write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")
    print("Added %d key(s) from %d source file(s); catalog now holds %d." % (len(added), scanned, len(catalog["strings"])))
else:
    print("No new keys. Catalog holds %d; %d source file(s) scanned." % (len(strings), scanned))
PY
