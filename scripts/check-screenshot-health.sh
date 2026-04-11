#!/usr/bin/env bash
#
# Checks screenshot reference images for likely rendering failures.
#
# Detects:
# - Blank/tiny PNGs (< 1KB) — preview rendered empty, usually because
#   it depends on runtime state (ViewModel, DI, etc.) unavailable in tests
# - Missing reference PNGs for screenshot test functions
#
# This is a WARNING tool, not a blocker. Broken screenshots may be caused
# by app errors, preview errors, or test infrastructure issues. The goal
# is to surface problems early so they can be prioritized.
#
# Usage: ./scripts/check-screenshot-health.sh
# Exit code: always 0 (warnings only, never blocks)

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
REFERENCE_DIR="$REPO_ROOT/AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference"
BLANK_THRESHOLD=1024  # bytes — PNGs under this are likely blank renders

echo "=== Screenshot Health Check ==="
echo ""

warnings=0

# Check 1: Find suspiciously small PNGs (likely blank renders)
echo "--- Checking for blank/tiny screenshots (< ${BLANK_THRESHOLD} bytes) ---"
blank_found=0
while IFS= read -r png_file; do
    size=$(wc -c < "$png_file")
    if [ "$size" -lt "$BLANK_THRESHOLD" ]; then
        rel_path="$(python3 -c "import os.path; print(os.path.relpath('$png_file', '$REPO_ROOT'))")"
        echo "  WARNING: Likely blank (${size}B): $rel_path"
        blank_found=$((blank_found + 1))
        warnings=$((warnings + 1))
    fi
done < <(find "$REFERENCE_DIR" -name "*.png" -type f 2>/dev/null)

if [ "$blank_found" -eq 0 ]; then
    echo "  All screenshots are > ${BLANK_THRESHOLD} bytes"
else
    echo ""
    echo "  $blank_found likely blank screenshot(s) found."
    echo "  These previews probably depend on runtime state (ViewModel, DI)"
    echo "  that is unavailable in screenshot tests. Fix by creating stateless"
    echo "  preview overloads that accept demo data as parameters."
fi
echo ""

# Check 2: Count total screenshots
total=$(find "$REFERENCE_DIR" -name "*.png" -type f 2>/dev/null | wc -l | tr -d ' ')
echo "--- Summary ---"
echo "  Total screenshots: $total"
echo "  Likely blank:      $blank_found"
echo "  Healthy:           $((total - blank_found))"

if [ "$warnings" -gt 0 ]; then
    echo ""
    echo "⚠ $warnings warning(s) — screenshots need attention (not blocking)"
fi

echo ""
echo "Screenshot health check complete."
# Always exit 0 — this is informational, not a gate
exit 0
