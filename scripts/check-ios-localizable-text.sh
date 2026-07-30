#!/usr/bin/env bash
set -euo pipefail

# Flag `Text(x)` / `Label(x, ...)` where x is a value rather than a literal.
#
# WHY: SwiftUI extracts a string into the String Catalog only where it can see a
# `LocalizedStringKey`. Once a literal has been flattened into a `String` — which
# is what the ViewModel wrappers do when they resolve typed shared state into
# display text — the compiler emits nothing and the string is silently
# unlocalizable. There is no warning; the app looks correct and the catalog just
# never learns the key exists. See MobileGarage/docs/IOS_LOCALIZATION.md.
#
# SCOPE OF THE CLAIM — read before trusting this: shell has no type information,
# so this cannot distinguish `Text(someString)` (the bug) from
# `Text(someLocalizedStringResource)` (correct). Both match. What this check
# therefore actually enforces is "do not introduce a value-typed Text()/Label()
# into a file that does not already have one" — a fence against spreading, not a
# verdict on any file's localization.
#
# The authoritative, compiler-derived measure of whether a string is localizable
# is the String Catalog: run ./scripts/sync-ios-string-catalog.sh and see whether
# the key appears. Use that to confirm a fix, never this check's silence.
#
# The escape hatch for genuinely non-localizable values (version numbers, build
# hashes, auth tokens, raw server text) is `Text(verbatim:)`, which states the
# intent at the call site and is never flagged.
#
# Scope: MobileGarage/iosApp/Features/**. `Core/` holds wire keys and Firebase
# plumbing, not user-visible copy.
#
# Run by validate-ios.sh and ios-ci.yml. Tracked files only (git grep).

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

EXEMPTIONS="MobileGarage/ios-localizable-text-exemptions.txt"

# A non-literal first argument: not a quote, not `verbatim:`, not the closing
# paren of a multi-line call. Requires the argument to start with a lowercase
# letter (a value) — `Text(Image(...))` and similar composed views are not
# strings and start uppercase.
#
# NOTE: `\b` is NOT usable here. `git grep -E` uses POSIX ERE, where `\b` is not
# a word boundary — a pattern containing it silently matches NOTHING and this
# check would pass vacuously forever. Use an explicit non-word-character class.
# (Verified: `git grep -cE '\b(Text|Label)\('` returns zero hits in a tree with
# dozens of them.)
WORD_START='(^|[^A-Za-z0-9_.])'
PATTERN="${WORD_START}(Text|Label)\([a-z_][A-Za-z0-9_.]*[,)]"

# Scope sanity: if the broad form matches nothing, the glob or the regex dialect
# has changed under us and every result below is meaningless. Fail loudly rather
# than reporting a clean tree.
broad_hits=$(git grep -cE "${WORD_START}(Text|Label)\(" -- 'MobileGarage/iosApp/Features/**/*.swift' | wc -l | tr -d ' ')
if [ "$broad_hits" -eq 0 ]; then
    echo "FAIL: found no Text()/Label() calls at all in iosApp/Features." >&2
    echo "The path glob or the regex dialect changed; this check cannot be trusted." >&2
    exit 1
fi

all_hits=$(git grep -nE "$PATTERN" -- 'MobileGarage/iosApp/Features/**/*.swift' || true)

# Drop `verbatim:` — the explicit "this is not translatable" marker.
all_hits=$(printf '%s\n' "$all_hits" | grep -v 'verbatim:' || true)

# Build the exempt-path list (strip comments and blanks).
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
    echo "FAIL: Text()/Label() called on a String-typed value outside the exemption list." >&2
    echo "" >&2
    echo "A String reaching Text() is invisible to the compiler's string extractor, so the" >&2
    echo "text can never be translated. Fix by changing the source's type to" >&2
    echo "LocalizedStringResource, or mark it Text(verbatim:) if it is genuinely not" >&2
    echo "translatable (version numbers, tokens, raw server text)." >&2
    echo "See MobileGarage/docs/IOS_LOCALIZATION.md." >&2
    echo "" >&2
    printf '%s' "$violations" >&2
    failed=1
fi

# Stale entries: a file that has been cleaned up must leave the list, or the
# list quietly re-permits a regression in an already-fixed file.
if [ -n "$exempt_paths" ]; then
    stale=""
    while IFS= read -r path; do
        [ -z "$path" ] && continue
        if ! printf '%s' "$offending_paths" | grep -qxF "$path"; then
            stale="$stale  $path"$'\n'
        fi
    done <<< "$exempt_paths"
    if [ -n "${stale//[$'\n' ]/}" ]; then
        echo "FAIL: stale entries in $EXEMPTIONS — these files no longer violate the rule." >&2
        echo "Remove them so the list keeps shrinking:" >&2
        printf '%s' "$stale" >&2
        failed=1
    fi
fi

[ "$failed" -eq 0 ] || exit 1

remaining=$(printf '%s\n' "$exempt_paths" | grep -cvE '^$' || true)
echo "PASS: no value-typed Text()/Label() outside the fence ($remaining file(s) listed)."
