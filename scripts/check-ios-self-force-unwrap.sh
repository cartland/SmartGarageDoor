#!/usr/bin/env bash
set -euo pipefail

# Forbid `self!` in iOS app Swift sources.
#
# WHY: `[weak self]` + `self!` inside a Task closure is a launch-crash pattern.
# `StateObject(wrappedValue:)` evaluates its autoclosure on every view init but
# keeps only the first object; a discarded wrapper's observation Tasks run
# after the wrapper deallocates, and `self!` traps. Empirical: TestFlight
# build ios/7 crashed on EVERY launch on iOS 16.3.1 (iPhone X) at
# HomeViewModelWrapper.swift:98 — newer OS versions dodge the timing, so the
# default-simulator launch smoke stayed green. The safe pattern is
# `guard let stream = self?.<...> else { return }` + `self?.` per iteration
# (see SettingsViewModelWrapper / HomeViewModelWrapper).
#
# Run by validate-ios.sh and ios-ci.yml. Checks tracked files only (git grep),
# so generated/derived data is naturally excluded.

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

matches=$(git grep -n 'self!' -- 'MobileGarage/iosApp/**/*.swift' || true)

if [ -n "$matches" ]; then
    echo "FAIL: 'self!' found in iOS Swift sources — this force-unwrap crashed ios/7 at launch on iOS 16." >&2
    echo "Use: guard let stream = self?.<flow> else { return } + 'self?.' per iteration." >&2
    echo "" >&2
    echo "$matches" >&2
    exit 1
fi

echo "PASS: no 'self!' in MobileGarage/iosApp Swift sources."
