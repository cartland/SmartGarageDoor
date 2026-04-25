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

WHATSNEW_DIR="AndroidGarage/distribution/whatsnew"
MAX_BYTES=500
FAIL=0

if [ ! -d "$WHATSNEW_DIR" ]; then
  echo "check-whatsnew-length: directory $WHATSNEW_DIR not found"
  exit 1
fi

for file in "$WHATSNEW_DIR"/whatsnew-*; do
  [ -f "$file" ] || continue
  bytes=$(wc -c < "$file" | tr -d ' ')
  name=$(basename "$file")
  if [ "$bytes" -gt "$MAX_BYTES" ]; then
    echo "FAIL: $name is $bytes bytes (max $MAX_BYTES)"
    echo "  Google Play rejects whatsnew files over $MAX_BYTES bytes per language."
    echo "  Fix: trim $file or replace older entries (CHANGELOG.md is the permanent history)."
    FAIL=1
  else
    echo "OK:   $name ($bytes / $MAX_BYTES bytes)"
  fi
done

exit $FAIL
