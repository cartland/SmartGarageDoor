#!/usr/bin/env bash
set -euo pipefail

# Local validation script for FirebaseServer — mirrors
# scripts/validate.sh (Android), but scoped to Cloud Functions.
# Run before releasing server/N to catch issues early.
#
# Writes a marker file at .claude/.firebase-validation-passed
# containing the commit SHA that passed. scripts/release-firebase.sh
# checks this marker and refuses to release if it is missing or stale.
#
# Usage:
#   ./scripts/validate-firebase.sh
#
# Node requirement: version pinned in FirebaseServer/.nvmrc.
# If nvm is available, this script auto-switches to that version.
# If nvm is not available (e.g. CI), Node must already match.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NVMRC="$REPO_ROOT/FirebaseServer/.nvmrc"
EXPECTED_MAJOR=$(tr -d '[:space:]' < "$NVMRC" | sed -E 's/^v?([0-9]+).*/\1/')

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

pass() { echo -e "${GREEN}PASS${RESET} $1"; }
fail() { echo -e "${RED}FAIL${RESET} $1"; exit 1; }
step() { echo -e "\n${BOLD}--- $1 ---${RESET}"; }

# Node version setup — auto-switch via nvm if available, else verify match.
# Node's native TypeScript type-stripping (default on 22.18+ and 24) changes
# CJS/ESM interop for mocha and breaks `import * as admin from 'firebase-admin'`.
# The `tests` npm script sets NODE_OPTIONS='--no-experimental-strip-types'
# to defer to ts-node.
step "Node version"
: "${NVM_DIR:=$HOME/.nvm}"
if [[ -s "$NVM_DIR/nvm.sh" ]]; then
    # shellcheck disable=SC1091
    . "$NVM_DIR/nvm.sh" >/dev/null
    nvm use "$EXPECTED_MAJOR" >/dev/null || fail "nvm could not switch to Node $EXPECTED_MAJOR (run: nvm install $EXPECTED_MAJOR)"
fi
if ! command -v node >/dev/null 2>&1; then
    fail "Node is not installed or not on PATH."
fi
NODE_VERSION=$(node --version)
NODE_MAJOR=$(echo "$NODE_VERSION" | sed -E 's/^v([0-9]+).*/\1/')
if [[ "$NODE_MAJOR" != "$EXPECTED_MAJOR" ]]; then
    echo -e "${YELLOW}Node $NODE_VERSION active, but FirebaseServer/.nvmrc pins Node $EXPECTED_MAJOR.${RESET}"
    fail "Node major version mismatch (got $NODE_MAJOR, expected $EXPECTED_MAJOR)"
fi
pass "Node $NODE_VERSION"

# Build (includes lint + tsc)
step "Build (lint + tsc)"
npm --prefix "$REPO_ROOT/FirebaseServer" run build \
    && pass "npm run build" \
    || fail "npm run build"

# Mocha pre-conditions (single-quoted glob + strip-types pin).
# Both pitfalls are silent: a wrong glob silently skips nested test
# directories (PR #486 — 84 tests went unnoticed for months); a missing
# NODE_OPTIONS pin silently breaks `import * as admin from 'firebase-admin'`
# under Node 22.18+/24's default native strip-types path.
# CLAUDE.md cites both — these assertions make the cite enforceable.
step "Mocha glob is single-quoted"
if grep -qF "'test/**/*.ts'" "$REPO_ROOT/FirebaseServer/package.json"; then
    pass "tests script uses single-quoted glob"
else
    fail "FirebaseServer/package.json 'tests' script must use SINGLE-quoted glob 'test/**/*.ts'. Unquoted globs let sh expand them with no globstar, silently skipping nested directories."
fi

step "NODE_OPTIONS pins --no-experimental-strip-types"
if grep -qF -- "--no-experimental-strip-types" "$REPO_ROOT/FirebaseServer/package.json"; then
    pass "NODE_OPTIONS pinned"
else
    fail "FirebaseServer/package.json 'tests' script must pin NODE_OPTIONS='--no-experimental-strip-types'. Without it, Node 22.18+/24 native strip-types breaks 'import * as admin from firebase-admin' under mocha+ts-node."
fi

# Tests (mocha) — includes collection-name contract tests and
# verifyIdToken library-chain tests
step "Tests (mocha)"
npm --prefix "$REPO_ROOT/FirebaseServer" run tests \
    && pass "npm run tests" \
    || fail "npm run tests"

# Record successful validation so release-firebase.sh knows we validated.
git rev-parse HEAD > "$REPO_ROOT/.claude/.firebase-validation-passed"

echo -e "\n${GREEN}${BOLD}All Firebase checks passed.${RESET}"
echo "Marker: $REPO_ROOT/.claude/.firebase-validation-passed"
echo "Commit: $(git rev-parse HEAD)"
