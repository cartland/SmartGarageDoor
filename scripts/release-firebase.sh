#!/usr/bin/env bash
set -euo pipefail

# Firebase Server release script — mirrors scripts/release-android.sh.
# Creates a server/N tag that triggers .github/workflows/firebase-deploy.yml.
#
# Usage:
#   ./scripts/release-firebase.sh              # Interactive (terminal only)
#   ./scripts/release-firebase.sh --check      # Print latest + next tag
#   ./scripts/release-firebase.sh --confirm-tag server/N  # Non-interactive
#   ./scripts/release-firebase.sh --dry-run    # Preview without releasing

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
BOLD='\033[1m'
RESET='\033[0m'

# Find the latest server tag number.
latest_tag_number() {
    git tag -l 'server/*' \
        | sed 's|server/||' \
        | sort -n \
        | tail -1
}

LATEST_NUM=$(latest_tag_number)
LATEST_NUM=${LATEST_NUM:-0}
NEXT_NUM=$((LATEST_NUM + 1))
LATEST_TAG="server/$LATEST_NUM"
NEXT_TAG="server/$NEXT_NUM"
BRANCH=$(git branch --show-current)
SHORT_SHA=$(git rev-parse --short HEAD)
FULL_SHA=$(git rev-parse HEAD)

# --- --check mode ---
if [[ "${1:-}" == "--check" ]]; then
    echo "Latest tag: $LATEST_TAG"
    echo "Next tag:   $NEXT_TAG"
    echo "Branch:     $BRANCH"
    echo "Commit:     $SHORT_SHA"
    exit 0
fi

# --- Validations ---
echo -e "${BOLD}=== Firebase Server Release ===${RESET}"

# Must be on main.
if [[ "$BRANCH" != "main" ]]; then
    echo -e "${RED}ERROR: Must be on 'main' branch (currently on '$BRANCH').${RESET}"
    exit 1
fi

# Must have clean working tree (allow untracked files).
if ! git diff --quiet HEAD 2>/dev/null; then
    echo -e "${RED}ERROR: Working tree has uncommitted changes.${RESET}"
    exit 1
fi

# Check Firebase CI passed on HEAD.
echo "Checking CI status on HEAD..."
FIREBASE_CI_CONCLUSION=$(gh api "repos/$(gh repo view --json nameWithOwner -q .nameWithOwner)/commits/$FULL_SHA/check-runs" \
    --jq '[.check_runs[] | select(.name == "Run Unit Tests" and .check_suite.conclusion != null)] | last | .conclusion' 2>/dev/null || echo "")

if [[ "$FIREBASE_CI_CONCLUSION" == "success" ]]; then
    echo -e "${GREEN}CI passed on HEAD.${RESET}"
elif [[ -z "$FIREBASE_CI_CONCLUSION" ]]; then
    echo -e "WARNING: Could not verify Firebase CI status. Proceeding anyway (Firebase CI may not run on all commits)."
else
    echo -e "${RED}ERROR: Firebase CI concluded '$FIREBASE_CI_CONCLUSION'. Fix CI before releasing.${RESET}"
    exit 1
fi

echo ""
echo "=== Release Details ==="
echo "  Branch:     $BRANCH"
echo "  Latest tag: $LATEST_TAG"
echo "  New tag:    $NEXT_TAG"
echo "  Commit:     $SHORT_SHA ($FULL_SHA)"
echo ""

# --- --dry-run mode ---
if [[ "${1:-}" == "--dry-run" ]]; then
    echo "(Dry run — no tag created)"
    exit 0
fi

# --- --confirm-tag mode ---
if [[ "${1:-}" == "--confirm-tag" ]]; then
    CONFIRM="${2:-}"
    if [[ "$CONFIRM" != "$NEXT_TAG" ]]; then
        echo -e "${RED}ERROR: --confirm-tag '$CONFIRM' does not match computed tag '$NEXT_TAG'.${RESET}"
        echo "The tag number is computed as latest + 1. You cannot override it."
        exit 1
    fi
    # Proceed to create tag below.
elif [[ -t 0 ]]; then
    # Interactive mode.
    read -p "Create and push tag $NEXT_TAG? [y/N] " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 1
    fi
else
    echo -e "${RED}ERROR: Non-interactive mode requires --confirm-tag $NEXT_TAG${RESET}"
    exit 1
fi

# --- Create and push tag ---
echo "Creating tag $NEXT_TAG..."
git tag "$NEXT_TAG"

echo "Pushing tag to origin..."
git push origin "$NEXT_TAG"

echo ""
echo -e "${GREEN}=== Release Initiated ===${RESET}"
echo ""
echo "Tag $NEXT_TAG pushed to origin."
echo "Monitor: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/actions"
