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
if [[ -z "$LATEST_NUM" ]]; then
    LATEST_TAG="(none)"
    NEXT_NUM=1
else
    LATEST_TAG="server/$LATEST_NUM"
    NEXT_NUM=$((LATEST_NUM + 1))
fi
NEXT_TAG="server/$NEXT_NUM"
BRANCH=$(git branch --show-current)
SHORT_SHA=$(git rev-parse --short HEAD)
FULL_SHA=$(git rev-parse HEAD)

# Check for existing tags on this commit.
TAGS_ON_COMMIT=$(git tag -l 'server/[0-9]*' --points-at HEAD 2>/dev/null | sort -t/ -k2 -n)
check_existing_tags() {
    if [[ -n "$TAGS_ON_COMMIT" ]]; then
        NEWEST_TAG_ON_COMMIT=$(echo "$TAGS_ON_COMMIT" | tail -1)
        if [[ "$NEWEST_TAG_ON_COMMIT" == "$LATEST_TAG" ]]; then
            echo "WARNING: This commit already has $LATEST_TAG."
            echo "  Creating $NEXT_TAG on the same commit (version bump, no code change)."
        else
            echo "WARNING: This commit was previously released as: $TAGS_ON_COMMIT"
            echo "  Latest tag $LATEST_TAG is on a different commit."
            echo "  Creating $NEXT_TAG here (rollback/rollforward)."
        fi
    fi
}

# --- --check mode ---
if [[ "${1:-}" == "--check" ]]; then
    echo "Latest tag: $LATEST_TAG"
    echo "Next tag:   $NEXT_TAG"
    echo "Branch:     $BRANCH"
    echo "Commit:     $SHORT_SHA"
    if ! git diff --quiet HEAD 2>/dev/null; then
        echo "WARNING: Uncommitted changes"
    fi
    UNTRACKED_CHECK=$(git ls-files --others --exclude-standard 2>/dev/null)
    if [[ -n "$UNTRACKED_CHECK" ]]; then
        UNTRACKED_COUNT=$(echo "$UNTRACKED_CHECK" | wc -l | tr -d ' ')
        echo "WARNING: $UNTRACKED_COUNT untracked file(s)"
    fi
    check_existing_tags
    exit 0
fi

# --- Validations ---
echo -e "${BOLD}=== Firebase Server Release ===${RESET}"

# Must be on main.
if [[ "$BRANCH" != "main" ]]; then
    echo -e "${RED}ERROR: Must be on 'main' branch (currently on '$BRANCH').${RESET}"
    exit 1
fi

# Must have clean working tree (tracked files).
if ! git diff --quiet HEAD 2>/dev/null; then
    echo -e "${RED}ERROR: Working tree has uncommitted changes.${RESET}"
    exit 1
fi

# Warn on untracked files (could affect build).
UNTRACKED=$(git ls-files --others --exclude-standard 2>/dev/null)
if [[ -n "$UNTRACKED" ]]; then
    echo -e "WARNING: Untracked files detected:"
    echo "$UNTRACKED" | head -10
    UNTRACKED_COUNT=$(echo "$UNTRACKED" | wc -l | tr -d ' ')
    if [[ "$UNTRACKED_COUNT" -gt 10 ]]; then
        echo "  ... and $((UNTRACKED_COUNT - 10)) more"
    fi
    echo ""
    if [[ -t 0 ]]; then
        read -p "Continue with untracked files? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Aborted."
            exit 1
        fi
    else
        echo "Non-interactive: proceeding with untracked files."
    fi
fi

# Check for existing tags on this commit
check_existing_tags

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
