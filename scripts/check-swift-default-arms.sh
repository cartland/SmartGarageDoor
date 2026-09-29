#!/usr/bin/env bash
set -euo pipefail
# Flag `default:` arms in Swift `switch` statements under iosApp/Features.
#
# WHY: a shared Kotlin enum or sealed type is a cross-platform parity guarantee
# only while BOTH compilers enforce exhaustiveness (CLAUDE.md § "Verify
# cross-platform exhaustiveness empirically"). Android has no `else` on a
# sealed `when` by ADR-010/011. On iOS a `default:` arm quietly turns the
# check off: adding a case on the Kotlin side then compiles clean on iOS and
# renders whatever the arm happened to return — which is exactly how a new
# door state would come out grey and wordless on the phone with no test
# noticing (docs/CROSS_SURFACE_UX_STRATEGY.md § 3.8, item 4.2).
#
# SCOPE OF THE CLAIM — read before trusting this: shell has no type
# information, so this cannot tell a switch over a shared enum (the bug) from
# one over an Int or a platform type that can grow (legitimate). What it
# enforces is therefore a FENCE: iosApp/Features may not gain a `default:`.
# A genuine need earns a line in the exemptions file, with its reason in a
# comment, rather than a `default:` in the clear. `@unknown default:` is NOT
# flagged — it keeps the known cases exhaustive and only absorbs cases the
# compiler cannot see yet.
#
# Scope: MobileGarage/iosApp/Features/**. `Core/` and `GarageControl/` hold
# plumbing that switches over platform types; they are not the surface the
# strategy is guarding.
#
# Run by validate-ios.sh and ios-ci.yml. Tracked files only (git grep).
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

EXEMPTIONS="MobileGarage/ios-swift-default-exemptions.txt"

# A `default:` label at the start of a line (after indentation). POSIX ERE, so
# no `\b` — see check-ios-localizable-text.sh for why that would match nothing.
PATTERN='^[[:space:]]*default:'

# Scope sanity: if no switch arms exist at all, the glob or the regex dialect
# has changed under us and a clean result would be meaningless. Fail loudly.
broad_hits=$(git grep -cE '^[[:space:]]*(case |switch )' -- 'MobileGarage/iosApp/Features/**/*.swift' | wc -l | tr -d ' ')
if [ "$broad_hits" -eq 0 ]; then
    echo "FAIL: found no switch/case lines at all in iosApp/Features." >&2
    echo "The path glob or the regex dialect changed; this check cannot be trusted." >&2
    exit 1
fi

all_hits=$(git grep -nE "$PATTERN" -- 'MobileGarage/iosApp/Features/**/*.swift' || true)

if [ -f "$EXEMPTIONS" ]; then
    exempt_paths=$(grep -vE '^\s*(#|$)' "$EXEMPTIONS" || true)
else
    exempt_paths=""
fi

violations=""
offending_paths=""
while IFS= read -r line; do
    [ -z "$line" ] && continue
    path="${line%%:*}"
    offending_paths="$offending_paths$path"$'\n'
    if printf '%s\n' "$exempt_paths" | grep -qxF "$path"; then
        continue
    fi
    violations="$violations$line"$'\n'
done <<< "$all_hits"

failed=0
if [ -n "${violations//[$'\n' ]/}" ]; then
    echo "FAIL: 'default:' arm in an iosApp/Features switch outside the exemption list." >&2
    echo "" >&2
    echo "A default: on a switch over a shared Kotlin enum or sealed type turns the compiler's" >&2
    echo "exhaustiveness check off — a case added on the Kotlin side no longer fails the iOS" >&2
    echo "build. Name every case instead. If the switch is over a genuinely open type, add the" >&2
    echo "file to $EXEMPTIONS with the reason." >&2
    echo "" >&2
    printf '%s' "$violations" >&2
    failed=1
fi

# Stale entries: a file that no longer has a default: must leave the list, or
# the list quietly re-permits a regression in an already-fixed file.
if [ -n "$exempt_paths" ]; then
    stale=""
    while IFS= read -r path; do
        [ -z "$path" ] && continue
        if ! printf '%s' "$offending_paths" | grep -qxF "$path"; then
            stale="$stale  $path"$'\n'
        fi
    done <<< "$exempt_paths"
    if [ -n "${stale//[$'\n' ]/}" ]; then
        echo "FAIL: stale entries in $EXEMPTIONS — these files no longer have a default: arm." >&2
        echo "Remove them so the list keeps shrinking:" >&2
        printf '%s' "$stale" >&2
        failed=1
    fi
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi
echo "PASS: no 'default:' arms in iosApp/Features switches outside the exemption list."
