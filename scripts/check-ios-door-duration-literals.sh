#!/usr/bin/env bash
set -euo pipefail
# Flag a literal slide duration in the iOS door view.
#
# WHY: the door's slide duration is a shared product decision —
# `DoorAnimation.ANIMATION_DURATION_SECONDS` in `:domain`, layer 1 ("Spec —
# Tier 1, fully provable") of MobileGarage/docs/UI_FIDELITY_TIERS.md § animation.
# The Swift door view reads it (`slideDuration`); a numeric literal handed to
# `.linear(duration:)` there would re-type the decision on one platform and let
# the two doors drift apart by a value nobody compares. This is the iOS twin of
# the Gradle task `checkNoLiteralDoorDurations` (buildSrc
# DoorDurationLiteralCheckTask), which covers the phone and Wear door views.
#
# SCOPE OF THE CLAIM: only the slide shape (`.linear(duration:`) is checked.
# Spring settles (`.spring(response:`) and eases are the Tier-3 native feel the
# doc leaves to each platform, and their numbers are taste, not the shared spec.
#
# Two scope-sanity rules so the check cannot pass vacuously: each listed file
# must exist and animate (`withAnimation(`), and must reference
# `DoorAnimation.shared` — the positive presence of the shared spec.
#
# Run by validate-ios.sh and ios-ci.yml. Tracked files only (git grep).
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

DOOR_VIEWS=(
    'MobileGarage/iosApp/Features/Home/GarageDoorCanvas.swift'
)

# POSIX ERE — no `\b` (see check-ios-localizable-text.sh for why that would
# match nothing and pass vacuously).
PATTERN='\.linear\(duration:[[:space:]]*[0-9]'

failed=0
for file in "${DOOR_VIEWS[@]}"; do
    if ! git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
        echo "FAIL: $file is not a tracked file — the iOS door view moved; update DOOR_VIEWS." >&2
        failed=1
        continue
    fi
    if ! git grep -qE 'withAnimation\(' -- "$file"; then
        echo "FAIL: $file no longer animates (no withAnimation) — is it still the door view?" >&2
        failed=1
    fi
    if ! git grep -qF 'DoorAnimation.shared' -- "$file"; then
        echo "FAIL: $file does not reference DoorAnimation.shared — the shared spec is the only source of the slide." >&2
        failed=1
    fi
    hits=$(git grep -nE "$PATTERN" -- "$file" || true)
    if [ -n "$hits" ]; then
        echo "FAIL: a literal slide duration in the iOS door view. Derive it from" >&2
        echo "DoorAnimation.shared.ANIMATION_DURATION_SECONDS (docs/UI_FIDELITY_TIERS.md § animation, layer 1)." >&2
        echo "" >&2
        echo "$hits" >&2
        failed=1
    fi
done

if [ "$failed" -ne 0 ]; then
    exit 1
fi
echo "PASS: the iOS door view derives its slide duration from DoorAnimation (${#DOOR_VIEWS[@]} file(s))."
