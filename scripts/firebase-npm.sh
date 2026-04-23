#!/usr/bin/env bash
set -euo pipefail

# Wrapper: runs any `npm` command in FirebaseServer with the Node version
# pinned in FirebaseServer/.nvmrc. Handles nvm sourcing and switching so
# callers don't have to.
#
# Usage:
#   ./scripts/firebase-npm.sh run tests
#   ./scripts/firebase-npm.sh run build
#   ./scripts/firebase-npm.sh install
#   ./scripts/firebase-npm.sh audit

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NVMRC="$REPO_ROOT/FirebaseServer/.nvmrc"

if [[ ! -f "$NVMRC" ]]; then
    echo "ERROR: $NVMRC not found" >&2
    exit 1
fi

NODE_VERSION=$(tr -d '[:space:]' < "$NVMRC")

: "${NVM_DIR:=$HOME/.nvm}"
if [[ -s "$NVM_DIR/nvm.sh" ]]; then
    # shellcheck disable=SC1091
    . "$NVM_DIR/nvm.sh" >/dev/null
    nvm use "$NODE_VERSION" >/dev/null
else
    echo "WARNING: nvm not found at $NVM_DIR — using system Node $(node --version 2>/dev/null || echo 'missing')." >&2
fi

exec npm --prefix "$REPO_ROOT/FirebaseServer" "$@"
