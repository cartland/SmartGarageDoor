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
# Node requirement: Node 20 (FirebaseServer/.nvmrc pins this).
# Node 24 breaks mocha tests via native namespace-import type-stripping,
# so the script verifies the active Node major version.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

pass() { echo -e "${GREEN}PASS${RESET} $1"; }
fail() { echo -e "${RED}FAIL${RESET} $1"; exit 1; }
step() { echo -e "\n${BOLD}--- $1 ---${RESET}"; }

# Node version check — mocha requires Node 20 here, not 22/24.
step "Node version"
if ! command -v node >/dev/null 2>&1; then
    fail "Node is not installed or not on PATH."
fi
NODE_VERSION=$(node --version)
NODE_MAJOR=$(echo "$NODE_VERSION" | sed -E 's/^v([0-9]+).*/\1/')
if [ "$NODE_MAJOR" != "20" ]; then
    echo -e "${YELLOW}Warning: Node $NODE_VERSION detected, but FirebaseServer/.nvmrc pins Node 20.${RESET}"
    echo "  Node 24 breaks mocha via native type-stripping of namespace imports."
    echo "  Run: nvm use 20   (or: nvm alias default 20)"
    echo ""
    fail "Node major version mismatch (got $NODE_MAJOR, expected 20)"
fi
pass "Node $NODE_VERSION"

# Build (includes lint + tsc)
step "Build (lint + tsc)"
npm --prefix "$REPO_ROOT/FirebaseServer" run build \
    && pass "npm run build" \
    || fail "npm run build"

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
