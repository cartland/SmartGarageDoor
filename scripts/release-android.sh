#!/usr/bin/env bash
set -euo pipefail

# Release Android app by creating and pushing an android/N tag.
# This triggers release-android.yml to build and deploy to Play Store INTERNAL track.
#
# IMPORTANT: We only publish to internal track, never production.
# Users promote from internal to production manually in Play Console.
#
# Usage:
#   ./scripts/release-android.sh              # Interactive mode (fails in CI/non-TTY)
#   ./scripts/release-android.sh --check      # Print latest and next tag, exit
#   ./scripts/release-android.sh --confirm-tag android/N  # Non-interactive release
#   ./scripts/release-android.sh --dry-run    # Show what would happen
#
# Safety:
#   - Only this script can push tags (Claude hooks block git tag commands)
#   - --confirm-tag must exactly match the computed next tag (cannot override)
#   - Requires clean working tree and CI passing on HEAD
#   - Requires main branch (or --confirm-hash for non-main)

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

# Parse flags
MODE="interactive"
CONFIRM_TAG=""
CONFIRM_HASH=""
DRY_RUN=false

show_help() {
    echo "Usage: ./scripts/release-android.sh [OPTIONS]"
    echo ""
    echo "Modes:"
    echo "  (no args)                  Interactive mode — prompts for confirmation"
    echo "  --check                    Print latest tag and next tag, then exit"
    echo "  --confirm-tag <tag>        Non-interactive release. Tag must match computed next tag."
    echo "  --dry-run                  Show what would happen without creating tags"
    echo ""
    echo "Options:"
    echo "  --confirm-hash <sha>       Required when releasing from non-main branch."
    echo "                             Must match HEAD's full SHA. This is a safety check"
    echo "                             to confirm you intend to release from this commit."
    echo "  -h, --help                 Show this help"
    echo ""
    echo "Examples:"
    echo "  ./scripts/release-android.sh --check"
    echo "  ./scripts/release-android.sh --confirm-tag android/2"
    echo "  ./scripts/release-android.sh --confirm-tag android/2 --confirm-hash \$(git rev-parse HEAD)"
    echo "  ./scripts/release-android.sh --dry-run"
    echo ""
    echo "Multiple tags on the same commit are allowed — tag numbers always increment."
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --check)
            MODE="check"
            shift
            ;;
        --confirm-tag)
            MODE="confirm"
            CONFIRM_TAG="$2"
            shift 2
            ;;
        --confirm-hash)
            CONFIRM_HASH="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Error: Unknown option: $1${RESET}"
            show_help
            exit 1
            ;;
    esac
done

# Fetch tags
git fetch origin --tags --quiet

# Compute latest and next tag
HIGHEST_VERSION=$(git tag -l 'android/[0-9]*' | \
    sed 's|android/||' | \
    grep -E '^[0-9]+$' || true)
HIGHEST_VERSION=$(echo "$HIGHEST_VERSION" | sort -n | tail -1)

if [ -z "$HIGHEST_VERSION" ]; then
    NEXT_VERSION=1
    LATEST_TAG="(none)"
else
    NEXT_VERSION=$((HIGHEST_VERSION + 1))
    LATEST_TAG="android/$HIGHEST_VERSION"
fi

NEW_TAG="android/$NEXT_VERSION"
CURRENT_COMMIT=$(git rev-parse HEAD)
CURRENT_COMMIT_SHORT=$(git rev-parse --short HEAD)
CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "(detached)")

# === --check mode ===
if [ "$MODE" = "check" ]; then
    echo "Latest tag: $LATEST_TAG"
    echo "Next tag:   $NEW_TAG"
    echo "Branch:     $CURRENT_BRANCH"
    echo "Commit:     $CURRENT_COMMIT_SHORT"
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        echo -e "${RED}WARNING: Uncommitted changes${RESET}"
    fi
    UNTRACKED_CHECK=$(git ls-files --others --exclude-standard 2>/dev/null)
    if [ -n "$UNTRACKED_CHECK" ]; then
        UNTRACKED_COUNT=$(echo "$UNTRACKED_CHECK" | wc -l | tr -d ' ')
        echo -e "${YELLOW}WARNING: $UNTRACKED_COUNT untracked file(s)${RESET}"
    fi
    exit 0
fi

# === Interactive mode guard ===
if [ "$MODE" = "interactive" ]; then
    if [ ! -t 0 ] || [ ! -t 1 ]; then
        echo -e "${RED}Error: Interactive mode requires a TTY.${RESET}"
        echo ""
        echo "This script must be run interactively from a terminal."
        echo "For CI or Claude, use:"
        echo "  ./scripts/release-android.sh --check          # See next tag"
        echo "  ./scripts/release-android.sh --confirm-tag $NEW_TAG  # Release"
        exit 1
    fi
fi

echo -e "${BOLD}=== Android Release ===${RESET}"
echo ""

# Check clean working tree (tracked files)
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}Error: Working tree has uncommitted changes.${RESET}"
    echo "Commit or stash changes before releasing."
    exit 1
fi

# Warn on untracked files (could affect build)
UNTRACKED=$(git ls-files --others --exclude-standard 2>/dev/null)
if [ -n "$UNTRACKED" ]; then
    echo -e "${YELLOW}Warning: Untracked files detected:${RESET}"
    echo "$UNTRACKED" | head -10
    UNTRACKED_COUNT=$(echo "$UNTRACKED" | wc -l | tr -d ' ')
    if [ "$UNTRACKED_COUNT" -gt 10 ]; then
        echo "  ... and $((UNTRACKED_COUNT - 10)) more"
    fi
    echo ""
    if [ "$MODE" = "interactive" ]; then
        read -p "Continue with untracked files? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Aborted. Remove or .gitignore untracked files before releasing."
            exit 1
        fi
    else
        echo -e "${YELLOW}Non-interactive: proceeding with untracked files.${RESET}"
    fi
fi

# Check CI status
echo "Checking CI status on HEAD..."
REPO=$(gh repo view --json nameWithOwner --jq '.nameWithOwner' 2>/dev/null || echo "")
if [ -n "$REPO" ]; then
    # Check the required CI checks (matches job names in ci.yml)
    TESTS_STATUS=$(gh api "repos/${REPO}/commits/${CURRENT_COMMIT}/check-runs" \
        --jq '[.check_runs[] | select(.name == "Unit Tests")] | last | .conclusion' 2>/dev/null || echo "")
    FORMAT_STATUS=$(gh api "repos/${REPO}/commits/${CURRENT_COMMIT}/check-runs" \
        --jq '[.check_runs[] | select(.name == "Formatting & Static Analysis")] | last | .conclusion' 2>/dev/null || echo "")
    BUILD_STATUS=$(gh api "repos/${REPO}/commits/${CURRENT_COMMIT}/check-runs" \
        --jq '[.check_runs[] | select(.name == "Build Debug APK")] | last | .conclusion' 2>/dev/null || echo "")

    if [ "$TESTS_STATUS" = "success" ] && [ "$FORMAT_STATUS" = "success" ] && [ "$BUILD_STATUS" = "success" ]; then
        echo -e "${GREEN}CI passed on HEAD.${RESET}"
    elif [ -z "$TESTS_STATUS" ] && [ -z "$FORMAT_STATUS" ] && [ -z "$BUILD_STATUS" ]; then
        echo -e "${YELLOW}Warning: No CI results found for HEAD.${RESET}"
        if [ "$MODE" = "interactive" ]; then
            read -p "Continue anyway? (y/N) " -n 1 -r
            echo ""
            [[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }
        else
            echo -e "${RED}Error: CI has not run on HEAD. Cannot release without CI.${RESET}"
            exit 1
        fi
    else
        echo -e "${RED}Error: CI did not pass on HEAD.${RESET}"
        echo "  Tests: $TESTS_STATUS"
        echo "  Formatting: $FORMAT_STATUS"
        echo "  Build: $BUILD_STATUS"
        exit 1
    fi
fi
echo ""

# Branch safety
if [ "$CURRENT_BRANCH" != "main" ]; then
    if [ -z "$CONFIRM_HASH" ]; then
        echo -e "${RED}Error: Not on main branch (on '$CURRENT_BRANCH').${RESET}"
        echo ""
        echo "Release from non-main requires --confirm-hash:"
        echo "  ./scripts/release-android.sh --confirm-hash $(git rev-parse HEAD)"
        exit 1
    elif [ "$CONFIRM_HASH" != "$CURRENT_COMMIT" ]; then
        echo -e "${RED}Error: --confirm-hash does not match HEAD.${RESET}"
        echo "  Provided: $CONFIRM_HASH"
        echo "  HEAD:     $CURRENT_COMMIT"
        exit 1
    fi
    echo -e "${YELLOW}Warning: Releasing from non-main branch '$CURRENT_BRANCH'.${RESET}"
    echo ""
fi

# Release details
echo "=== Release Details ==="
echo "  Branch:     $CURRENT_BRANCH"
echo "  Latest tag: $LATEST_TAG"
echo "  New tag:    $NEW_TAG"
echo "  Commit:     $CURRENT_COMMIT_SHORT ($CURRENT_COMMIT)"
echo ""

# Note existing tags on this commit (not blocking — tag numbers always increment)
EXISTING_TAGS=$(git tag --points-at HEAD | grep -E '^android/[0-9]+$' || true)
if [ -n "$EXISTING_TAGS" ]; then
    echo -e "${YELLOW}Note: This commit already has tag(s): $EXISTING_TAGS${RESET}"
    echo "  New tag $NEW_TAG will be created with the next number."
    echo ""
fi

# Confirmation
if [ "$MODE" = "confirm" ]; then
    if [ "$CONFIRM_TAG" != "$NEW_TAG" ]; then
        echo -e "${RED}Error: --confirm-tag does not match computed next tag.${RESET}"
        echo "  Provided: $CONFIRM_TAG"
        echo "  Expected: $NEW_TAG"
        echo ""
        echo "--confirm-tag is a safety check, not an override."
        echo "The next tag is always computed as highest existing + 1."
        exit 1
    fi
    echo "--confirm-tag matches, proceeding..."
elif [ "$MODE" = "interactive" ]; then
    echo -e "${YELLOW}This will create tag '$NEW_TAG' and push it to origin.${RESET}"
    read -p "Proceed with release? (y/N) " -n 1 -r
    echo ""
    [[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted."; exit 0; }
fi

# Dry run
if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}=== Dry Run ===${RESET}"
    echo "Would create tag $NEW_TAG on commit $CURRENT_COMMIT_SHORT"
    echo "Would push tag to origin"
    echo "No tags were created or pushed."
    exit 0
fi

# Create and push tag
echo ""
echo "Creating tag $NEW_TAG..."
git tag "$NEW_TAG"

echo "Pushing tag to origin..."
if git push origin "$NEW_TAG"; then
    echo ""
    echo -e "${GREEN}=== Release Initiated ===${RESET}"
    echo ""
    echo "Tag $NEW_TAG pushed to origin."
    echo "Monitor: https://github.com/cartland/SmartGarageDoor/actions"
else
    echo -e "${RED}Error: Failed to push tag.${RESET}"
    echo "Removing local tag..."
    git tag -d "$NEW_TAG"
    exit 1
fi
