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
#   ./scripts/release-android.sh --confirm-tag android/N  # Non-interactive release from main HEAD
#   ./scripts/release-android.sh --confirm-tag android/N --confirm-hash <ref>  # Any commit
#   ./scripts/release-android.sh --confirm-tag android/N --skip-validation     # Skip validation check
#   ./scripts/release-android.sh --dry-run    # Show what would happen
#
# Default gates (all enforced):
#   1. Must be on main branch         — override with --confirm-hash <ref>
#   2. Must have passed validate.sh   — override with --skip-validation
#   3. --confirm-tag must match next   — no override (always required in non-interactive)
#
# The release workflow (release-android.yml) does NOT re-check these gates.
# It trusts the tag and builds whatever commit the tag points to.

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
SKIP_VALIDATION=false
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
    echo "Override flags:"
    echo "  --confirm-hash <ref>       Release a specific commit (SHA, tag, branch)."
    echo "                             Bypasses the main-branch requirement."
    echo "  --skip-validation          Skip the validate.sh check. Use when you need to"
    echo "                             release urgently without local validation."
    echo "  -h, --help                 Show this help"
    echo ""
    echo "Examples:"
    echo "  ./scripts/release-android.sh --check"
    echo "  ./scripts/release-android.sh --confirm-tag android/141"
    echo "  ./scripts/release-android.sh --confirm-tag android/141 --confirm-hash android/139"
    echo "  ./scripts/release-android.sh --confirm-tag android/141 --skip-validation"
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
        --skip-validation)
            SKIP_VALIDATION=true
            shift
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

# Resolve target commit
if [ -n "$CONFIRM_HASH" ]; then
    TARGET_COMMIT=$(git rev-parse --verify "$CONFIRM_HASH" 2>/dev/null) || {
        echo -e "${RED}Error: --confirm-hash '$CONFIRM_HASH' is not a valid git ref.${RESET}"
        exit 1
    }
    TARGET_COMMIT_SHORT=$(git rev-parse --short "$TARGET_COMMIT")
    TARGET_SOURCE="--confirm-hash $CONFIRM_HASH → $TARGET_COMMIT_SHORT"
else
    TARGET_COMMIT=$(git rev-parse HEAD)
    TARGET_COMMIT_SHORT=$(git rev-parse --short HEAD)
    TARGET_SOURCE="HEAD ($TARGET_COMMIT_SHORT)"
fi

CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "(detached)")

# Check for existing android tags on target commit
TAGS_ON_COMMIT=$(git tag -l 'android/[0-9]*' --points-at "$TARGET_COMMIT" 2>/dev/null | sort -t/ -k2 -n || true)
check_existing_tags() {
    if [ -n "$TAGS_ON_COMMIT" ]; then
        NEWEST_TAG_ON_COMMIT=$(echo "$TAGS_ON_COMMIT" | tail -1)
        if [ "$NEWEST_TAG_ON_COMMIT" = "$LATEST_TAG" ]; then
            echo -e "${YELLOW}WARNING: This commit already has $LATEST_TAG.${RESET}"
            echo "  Creating $NEW_TAG on the same commit (version bump, no code change)."
        else
            echo -e "${YELLOW}WARNING: This commit was previously released as: $(echo "$TAGS_ON_COMMIT" | tr '\n' ' ')${RESET}"
            echo "  Latest tag $LATEST_TAG is on a different commit."
            echo "  Creating $NEW_TAG here (rollback/rollforward)."
        fi
    fi
}

# Check if validate.sh passed on the target commit
VALIDATION_FILE="$REPO_ROOT/.claude/.validation-passed"
check_validation() {
    if [ ! -f "$VALIDATION_FILE" ]; then
        return 1
    fi
    VALIDATED_COMMIT=$(cat "$VALIDATION_FILE" 2>/dev/null | tr -d '[:space:]')
    [ "$VALIDATED_COMMIT" = "$TARGET_COMMIT" ]
}

# === --check mode ===
if [ "$MODE" = "check" ]; then
    echo "Latest tag: $LATEST_TAG"
    echo "Next tag:   $NEW_TAG"
    echo "Branch:     $CURRENT_BRANCH"
    echo "Target:     $TARGET_SOURCE"
    if [ -z "$CONFIRM_HASH" ] && ! git diff-index --quiet HEAD -- 2>/dev/null; then
        echo -e "${RED}WARNING: Uncommitted changes${RESET}"
    fi
    if check_validation; then
        echo -e "${GREEN}Validation: passed${RESET}"
    else
        echo -e "${YELLOW}Validation: not passed for this commit${RESET}"
    fi
    check_existing_tags
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

# === Gate 1: Clean working tree (only when tagging HEAD) ===
if [ -z "$CONFIRM_HASH" ]; then
    if ! git diff-index --quiet HEAD --; then
        echo -e "${RED}Error: Working tree has uncommitted changes.${RESET}"
        echo "Commit or stash changes before releasing."
        exit 1
    fi

    # Warn on untracked files
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
fi

# === Gate 2: Must be on main — override with --confirm-hash ===
if [ -z "$CONFIRM_HASH" ] && [ "$CURRENT_BRANCH" != "main" ]; then
    echo -e "${RED}Error: Not on main branch (on '$CURRENT_BRANCH').${RESET}"
    echo ""
    echo "To release from a specific commit, use --confirm-hash:"
    echo "  ./scripts/release-android.sh --confirm-tag $NEW_TAG --confirm-hash $(git rev-parse HEAD)"
    exit 1
fi

if [ -n "$CONFIRM_HASH" ]; then
    echo -e "${YELLOW}Releasing specific commit: $TARGET_COMMIT_SHORT${RESET}"
    echo "  Resolved from: $CONFIRM_HASH"
    echo "  Full SHA:      $TARGET_COMMIT"
    echo ""
fi

# === Gate 3: Must have passed validate.sh — override with --skip-validation ===
if [ "$SKIP_VALIDATION" = true ]; then
    echo -e "${YELLOW}Warning: Validation check skipped (--skip-validation).${RESET}"
    echo ""
elif check_validation; then
    echo -e "${GREEN}Validation passed for this commit.${RESET}"
else
    echo -e "${YELLOW}Warning: validate.sh has not passed on commit $TARGET_COMMIT_SHORT.${RESET}"
    if [ "$MODE" = "interactive" ]; then
        read -p "Continue without validation? (y/N) " -n 1 -r
        echo ""
        [[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted. Run ./scripts/validate.sh first."; exit 1; }
    else
        echo -e "${RED}Error: validate.sh has not passed on this commit.${RESET}"
        echo "Run ./scripts/validate.sh first, or use --skip-validation to override."
        exit 1
    fi
fi

check_existing_tags

# Release details
echo ""
echo "=== Release Details ==="
echo "  Target:     $TARGET_SOURCE"
echo "  Branch:     $CURRENT_BRANCH"
echo "  Latest tag: $LATEST_TAG"
echo "  New tag:    $NEW_TAG"
echo "  Commit:     $TARGET_COMMIT_SHORT ($TARGET_COMMIT)"
echo ""

# Note existing tags on this commit
EXISTING_TAGS=$(git tag --points-at "$TARGET_COMMIT" | grep -E '^android/[0-9]+$' || true)
if [ -n "$EXISTING_TAGS" ]; then
    echo -e "${YELLOW}Note: This commit already has tag(s): $(echo "$EXISTING_TAGS" | tr '\n' ' ')${RESET}"
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
    echo "Would create tag $NEW_TAG on commit $TARGET_COMMIT_SHORT"
    echo "Would push tag to origin"
    echo "No tags were created or pushed."
    exit 0
fi

# Create and push tag
echo ""
echo "Creating tag $NEW_TAG on $TARGET_COMMIT_SHORT..."
git tag "$NEW_TAG" "$TARGET_COMMIT"

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
