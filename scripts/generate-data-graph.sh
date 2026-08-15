#!/usr/bin/env bash
#
# Regenerate MobileGarage/docs/DATA_GRAPH.md from the sources — or, with
# --check, verify it without writing.
#
# The graph is extracted and rendered by DataGraphExtractionKonsistTest
# (docs/DATA_GRAPH_PLAN.md §6). The test ALWAYS writes the freshly
# generated rendering to androidApp/build/reports/data-graph/ and fails
# when the committed file differs, so:
#   --check    = run the pin (what CI does on every PR via unit tests)
#   (default)  = run the pin; on drift, copy the generated artifact over
#                the committed file and re-run to verify.
#
# --rerun-tasks is load-bearing: the committed file is read at test
# RUNTIME, so Gradle's up-to-date check cannot see doc edits
# (CLAUDE.md § Konsist).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GENERATED="$ROOT/MobileGarage/androidApp/build/reports/data-graph/DATA_GRAPH.md"
COMMITTED="$ROOT/MobileGarage/docs/DATA_GRAPH.md"

run_pin() {
    "$ROOT/MobileGarage/gradlew" -p "$ROOT/MobileGarage" :androidApp:testDebugUnitTest \
        --tests 'com.chriscartland.garage.konsist.DataGraphExtractionKonsistTest' --rerun-tasks
}

if [ "${1:-}" = "--check" ]; then
    run_pin
    echo "DATA_GRAPH.md matches the sources."
    exit 0
fi

if run_pin; then
    echo "DATA_GRAPH.md already matches the sources."
    exit 0
fi

if [ ! -f "$GENERATED" ]; then
    echo "Test failed without producing $GENERATED — a non-drift failure; see the Gradle output above." >&2
    exit 1
fi

cp "$GENERATED" "$COMMITTED"
echo "Regenerated $COMMITTED — re-running the pin to verify..."
run_pin
echo "DATA_GRAPH.md regenerated and verified."
