#!/usr/bin/env bash
# Check Play Store whatsnew files do not exceed Google's 500-character per-language limit.
#
# Background: Google Play rejects an upload if any per-language whatsnew file exceeds 500
# characters. The release workflow learns this only at upload time (after a 5-minute build),
# leaves a tombstone tag, and requires a re-release on a new tag. This script catches the
# regression locally and in pre-submit CI via validate.sh.
#
# Exits 0 if all files pass, non-zero with a diagnostic on the first violation.

set -euo pipefail

cd "$(dirname "$0")/.."

# EVERY directory a release lane uploads release notes from. The phone lane was
# the only one checked until 2026-07-27, which meant the Wear lane — which sends
# its own notes from wear-whatsnew/ — had no guard at all and would have hit the
# same tombstone-tag failure the phone lane was protected from. Add a line here
# when a new lane starts shipping notes.
WHATSNEW_DIRS=(
  "MobileGarage/distribution/whatsnew"
  "MobileGarage/distribution/wear-whatsnew"
)
MAX_BYTES=500
FAIL=0

for dir in "${WHATSNEW_DIRS[@]}"; do
  if [ ! -d "$dir" ]; then
    echo "check-whatsnew-length: directory $dir not found"
    exit 1
  fi
  found=0
  for file in "$dir"/whatsnew-*; do
    [ -f "$file" ] || continue
    found=1
    bytes=$(wc -c < "$file" | tr -d ' ')
    name="$(basename "$dir")/$(basename "$file")"
    if [ "$bytes" -gt "$MAX_BYTES" ]; then
      echo "FAIL: $name is $bytes bytes (max $MAX_BYTES)"
      echo "  Google Play rejects whatsnew files over $MAX_BYTES bytes per language."
      echo "  Fix: trim $file or replace older entries (CHANGELOG.md is the permanent history)."
      FAIL=1
    else
      echo "OK:   $name ($bytes / $MAX_BYTES bytes)"
    fi
  done
  # An empty directory would silently check nothing, which reads as a pass.
  if [ "$found" -eq 0 ]; then
    echo "FAIL: $dir contains no whatsnew-* files"
    FAIL=1
  fi
done

exit $FAIL
