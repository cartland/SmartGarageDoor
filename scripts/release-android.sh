#!/usr/bin/env bash
set -euo pipefail

# Release Android app by creating and pushing an android/N tag.
# This triggers release-android.yml to build and deploy to Play Store INTERNAL track.
#
# IMPORTANT: We only publish to internal track, never production.
# Users promote from internal to production manually in Play Console.
#
# USAGE
#
# Start with --check. It prints the exact command(s) you should run next,
# with real values (SHAs, tag names) already filled in. Copy-paste, don't
# re-type from memory; seeing the concrete values is the whole point.
#
#   ./scripts/release-android.sh --check
#
# Normal release (validation marker matches HEAD):
#   ./scripts/release-android.sh --confirm-tag android/N
#
# Emergency release (validation has not passed for HEAD):
#   ./scripts/release-android.sh \
#       --confirm-tag android/N \
#       --confirm-unvalidated-release <40-char-sha>
#
# Rollback release (detached HEAD on an older tag):
#   git checkout android/M            # older tag you want to re-release
#   ./scripts/release-android.sh --check  # prints the rollback command
#   ./scripts/release-android.sh \
#       --confirm-tag android/N \
#       --confirm-hash <40-char-sha-of-target> \
#       --confirm-rollback-from <40-char-sha-of-previous-latest>
#
# DESIGN PRINCIPLE
#
# Every override asks you to state a value from reality (a SHA, a tag).
# The script fails if the value does not match. This makes correct usage
# easy (read from --check output) and accidental usage hard (you would
# have to type the right value for the wrong situation).

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

# Parse flags.
MODE="interactive"
CONFIRM_TAG=""
CONFIRM_HASH=""
CONFIRM_ROLLBACK_FROM=""
CONFIRM_UNVALIDATED_RELEASE=""
SKIP_VALIDATION=false  # legacy; prefer --confirm-unvalidated-release
DRY_RUN=false

show_help() {
    cat <<'EOF'
Usage: ./scripts/release-android.sh [OPTIONS]

Recommended workflow: run with --check first. It prints the exact
command to run next, with SHAs filled in. Copy-paste that command.

Modes:
  (no args)                 Interactive mode — prompts for confirmation
  --check                   Print state + recommended next command, then exit
  --confirm-tag <tag>       Non-interactive release. Tag must match computed next tag.
  --dry-run                 Show what would happen without creating tags

Override flags (all require a specific value from --check output):
  --confirm-hash <sha>              Release a specific commit. SHA must match
                                    exactly (recommend 40-char full SHA).
  --confirm-rollback-from <sha>     Required for rollback releases. Must match
                                    the SHA the latest tag currently points to.
  --confirm-unvalidated-release <sha>
                                    Release without validation marker match.
                                    SHA must equal the target commit.

Deprecated:
  --skip-validation         Legacy boolean skip; prefer --confirm-unvalidated-release.
                            Still accepted for backward compat.

Help:
  -h, --help                Show this help

Examples:
  ./scripts/release-android.sh --check
  ./scripts/release-android.sh --confirm-tag android/141
EOF
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
        --confirm-rollback-from)
            CONFIRM_ROLLBACK_FROM="$2"
            shift 2
            ;;
        --confirm-unvalidated-release)
            CONFIRM_UNVALIDATED_RELEASE="$2"
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

# Fetch tags.
git fetch origin --tags --quiet

# Compute latest tag number and its SHA.
HIGHEST_VERSION=$(git tag -l 'android/[0-9]*' | \
    sed 's|android/||' | \
    grep -E '^[0-9]+$' || true)
HIGHEST_VERSION=$(echo "$HIGHEST_VERSION" | sort -n | tail -1)

if [ -z "$HIGHEST_VERSION" ]; then
    NEXT_VERSION=1
    LATEST_TAG="(none)"
    LATEST_TAG_SHA="(none)"
else
    NEXT_VERSION=$((HIGHEST_VERSION + 1))
    LATEST_TAG="android/$HIGHEST_VERSION"
    LATEST_TAG_SHA=$(git rev-parse "$LATEST_TAG" 2>/dev/null || echo "(missing)")
fi

NEW_TAG="android/$NEXT_VERSION"

# Resolve target commit.
if [ -n "$CONFIRM_HASH" ]; then
    TARGET_COMMIT=$(git rev-parse --verify "$CONFIRM_HASH" 2>/dev/null) || {
        echo -e "${RED}Error: --confirm-hash '$CONFIRM_HASH' is not a valid git ref.${RESET}"
        exit 1
    }
    TARGET_COMMIT_SHORT=$(git rev-parse --short "$TARGET_COMMIT")
    TARGET_SOURCE="--confirm-hash $CONFIRM_HASH -> $TARGET_COMMIT_SHORT"
else
    TARGET_COMMIT=$(git rev-parse HEAD)
    TARGET_COMMIT_SHORT=$(git rev-parse --short HEAD)
    TARGET_SOURCE="HEAD ($TARGET_COMMIT_SHORT)"
fi

CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "")
if [ -z "$CURRENT_BRANCH" ]; then
    IS_DETACHED="true"
    CURRENT_BRANCH="(detached)"
else
    IS_DETACHED="false"
fi

# Validation marker state.
#   matches : marker exists and matches TARGET_COMMIT
#   stale   : marker exists but matches some other commit
#   missing : marker file does not exist
VALIDATION_FILE="$REPO_ROOT/.claude/.validation-passed"
VALIDATION_STATE="missing"
VALIDATED_COMMIT=""
if [ -f "$VALIDATION_FILE" ]; then
    VALIDATED_COMMIT=$(cat "$VALIDATION_FILE" 2>/dev/null | tr -d '[:space:]')
    if [ "$VALIDATED_COMMIT" = "$TARGET_COMMIT" ]; then
        VALIDATION_STATE="matches"
    else
        VALIDATION_STATE="stale"
    fi
fi

# Rollback detection: target commit is already tagged (not the latest),
# usually because the user ran `git checkout android/M` on an older tag.
EXISTING_TAGS_ON_TARGET=$(git tag -l 'android/[0-9]*' --points-at "$TARGET_COMMIT" 2>/dev/null | sort -t/ -k2 -n || true)
IS_ROLLBACK="false"
if [ -n "$EXISTING_TAGS_ON_TARGET" ]; then
    NEWEST_TAG_ON_TARGET=$(echo "$EXISTING_TAGS_ON_TARGET" | tail -1)
    if [ "$NEWEST_TAG_ON_TARGET" != "$LATEST_TAG" ]; then
        IS_ROLLBACK="true"
    fi
fi

# === --check mode ===
if [ "$MODE" = "check" ]; then
    echo "Latest tag:   $LATEST_TAG (sha: $LATEST_TAG_SHA)"
    echo "Next tag:     $NEW_TAG"
    echo "HEAD:         $TARGET_COMMIT"
    echo "Branch:       $CURRENT_BRANCH"

    case "$VALIDATION_STATE" in
        matches)
            echo -e "Validation:   ${GREEN}PASSED${RESET} on $VALIDATED_COMMIT"
            ;;
        stale)
            echo -e "Validation:   ${YELLOW}STALE${RESET} (marker is for $VALIDATED_COMMIT, not HEAD)"
            ;;
        missing)
            echo -e "Validation:   ${YELLOW}MISSING${RESET} — run ./scripts/validate.sh first"
            ;;
    esac

    if [ -n "$EXISTING_TAGS_ON_TARGET" ]; then
        echo "Target tags:  $(echo "$EXISTING_TAGS_ON_TARGET" | tr '\n' ' ')"
    fi

    echo ""

    if [ "$IS_ROLLBACK" = "true" ]; then
        echo -e "${BOLD}Detected ROLLBACK${RESET} (HEAD is on $NEWEST_TAG_ON_TARGET, older than $LATEST_TAG)."
        echo ""
        echo "Copy and run this command to proceed:"
        echo ""
        echo "  ./scripts/release-android.sh \\"
        echo "      --confirm-tag $NEW_TAG \\"
        echo "      --confirm-hash $TARGET_COMMIT \\"
        echo "      --confirm-rollback-from $LATEST_TAG_SHA"
        echo ""
        if [ "$VALIDATION_STATE" != "matches" ]; then
            echo "If you also need to skip validation (expected for old rollback targets), append:"
            echo "      --confirm-unvalidated-release $TARGET_COMMIT"
            echo ""
        fi
    elif [ "$VALIDATION_STATE" = "matches" ]; then
        echo "Copy and run this command to release:"
        echo ""
        echo "  ./scripts/release-android.sh --confirm-tag $NEW_TAG"
        echo ""
    else
        echo "Validation is not passing for HEAD. Two options:"
        echo ""
        echo "(1) Run validation, then re-run --check (recommended):"
        echo "    ./scripts/validate.sh && ./scripts/release-android.sh --check"
        echo ""
        echo "(2) Release WITHOUT validation (emergency only):"
        echo ""
        echo "    ./scripts/release-android.sh \\"
        echo "        --confirm-tag $NEW_TAG \\"
        echo "        --confirm-unvalidated-release $TARGET_COMMIT"
        echo ""
    fi

    exit 0
fi

# === Interactive mode guard ===
if [ "$MODE" = "interactive" ]; then
    if [ ! -t 0 ] || [ ! -t 1 ]; then
        echo -e "${RED}Error: Interactive mode requires a TTY.${RESET}"
        echo ""
        echo "Run --check to see the exact command for this state:"
        echo "  ./scripts/release-android.sh --check"
        exit 1
    fi
fi

echo -e "${BOLD}=== Android Release ===${RESET}"
echo ""

# === Gate: Clean working tree (only when tagging HEAD) ===
if [ -z "$CONFIRM_HASH" ]; then
    if ! git diff-index --quiet HEAD --; then
        echo -e "${RED}Error: Working tree has uncommitted changes.${RESET}"
        echo "Commit or stash changes before releasing."
        exit 1
    fi

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

# === Gate: Must be on main (when tagging HEAD) — override via --confirm-hash ===
if [ -z "$CONFIRM_HASH" ] && [ "$CURRENT_BRANCH" != "main" ]; then
    echo -e "${RED}Error: Not on main branch (on '$CURRENT_BRANCH').${RESET}"
    echo ""
    echo "Run --check to see the exact command for this state:"
    echo "  ./scripts/release-android.sh --check"
    exit 1
fi

if [ -n "$CONFIRM_HASH" ]; then
    echo -e "${YELLOW}Releasing specific commit: $TARGET_COMMIT_SHORT${RESET}"
    echo "  Resolved from: $CONFIRM_HASH"
    echo "  Full SHA:      $TARGET_COMMIT"
    echo ""
fi

# === Gate: Rollback confirmation ===
# If --confirm-rollback-from is provided, validate it matches the latest tag's
# current commit. If the target looks like a rollback but this flag is not
# provided, require it (prevents accidental "rollback" by forgetting to
# re-checkout main).
if [ -n "$CONFIRM_ROLLBACK_FROM" ]; then
    if [ "$CONFIRM_ROLLBACK_FROM" != "$LATEST_TAG_SHA" ]; then
        echo -e "${RED}Error: --confirm-rollback-from does not match the latest tag's commit.${RESET}"
        echo "  Expected (sha of $LATEST_TAG): $LATEST_TAG_SHA"
        echo "  Got:                           $CONFIRM_ROLLBACK_FROM"
        echo ""
        echo "Run --check to see the correct value."
        exit 1
    fi
    echo -e "${YELLOW}Rollback confirmed: $LATEST_TAG -> $NEW_TAG at $TARGET_COMMIT_SHORT${RESET}"
    echo ""
elif [ "$IS_ROLLBACK" = "true" ]; then
    echo -e "${RED}Error: Target commit is on tag $NEWEST_TAG_ON_TARGET, older than $LATEST_TAG.${RESET}"
    echo "  This looks like a rollback but --confirm-rollback-from was not provided."
    echo ""
    echo "Run --check to see the correct command."
    exit 1
fi

# === Gate: Validation ===
# Three acceptable paths:
#   A. Validation marker matches target commit (normal)
#   B. --confirm-unvalidated-release matches target commit (emergency)
#   C. --skip-validation (legacy; deprecated)
if [ "$VALIDATION_STATE" = "matches" ]; then
    echo -e "${GREEN}Validation passed for this commit.${RESET}"
elif [ -n "$CONFIRM_UNVALIDATED_RELEASE" ]; then
    if [ "$CONFIRM_UNVALIDATED_RELEASE" != "$TARGET_COMMIT" ]; then
        echo -e "${RED}Error: --confirm-unvalidated-release SHA does not match target commit.${RESET}"
        echo "  Expected (target commit): $TARGET_COMMIT"
        echo "  Got:                      $CONFIRM_UNVALIDATED_RELEASE"
        echo ""
        echo "Run --check to see the correct value."
        exit 1
    fi
    echo -e "${YELLOW}WARNING: Releasing without validation (--confirm-unvalidated-release).${RESET}"
    echo "  This is intended for emergencies (hotfixes, rollbacks of old tags)."
    echo ""
elif [ "$SKIP_VALIDATION" = true ]; then
    echo -e "${YELLOW}WARNING: --skip-validation is deprecated.${RESET}"
    echo "  Prefer --confirm-unvalidated-release <sha>. See --help."
    echo -e "${YELLOW}WARNING: Validation check skipped.${RESET}"
    echo ""
else
    if [ "$MODE" = "interactive" ]; then
        if [ "$VALIDATION_STATE" = "stale" ]; then
            echo -e "${YELLOW}Warning: validation marker is stale.${RESET}"
            echo "  Marker SHA:    $VALIDATED_COMMIT"
            echo "  Target SHA:    $TARGET_COMMIT"
        else
            echo -e "${YELLOW}Warning: no validation marker — run ./scripts/validate.sh first.${RESET}"
        fi
        read -p "Continue without validation? (y/N) " -n 1 -r
        echo ""
        [[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted. Run ./scripts/validate.sh first, or run --check for options."; exit 1; }
    else
        echo -e "${RED}Error: validation has not passed for this commit.${RESET}"
        echo ""
        echo "Run --check to see the exact command for this state:"
        echo "  ./scripts/release-android.sh --check"
        exit 1
    fi
fi

# Existing tags on this commit (informational).
if [ -n "$EXISTING_TAGS_ON_TARGET" ]; then
    NEWEST_TAG_ON_COMMIT=$(echo "$EXISTING_TAGS_ON_TARGET" | tail -1)
    if [ "$NEWEST_TAG_ON_COMMIT" = "$LATEST_TAG" ]; then
        echo -e "${YELLOW}Note: This commit already has $LATEST_TAG.${RESET}"
        echo "  Creating $NEW_TAG on the same commit (version bump, no code change)."
    else
        echo -e "${YELLOW}Note: This commit previously released as: $(echo "$EXISTING_TAGS_ON_TARGET" | tr '\n' ' ')${RESET}"
    fi
fi

# Release details.
echo ""
echo "=== Release Details ==="
echo "  Target:     $TARGET_SOURCE"
echo "  Branch:     $CURRENT_BRANCH"
echo "  Latest tag: $LATEST_TAG ($LATEST_TAG_SHA)"
echo "  New tag:    $NEW_TAG"
echo "  Commit:     $TARGET_COMMIT_SHORT ($TARGET_COMMIT)"
echo ""

# Confirmation.
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

# Dry run.
if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}=== Dry Run ===${RESET}"
    echo "Would create tag $NEW_TAG on commit $TARGET_COMMIT_SHORT"
    echo "Would push tag to origin"
    echo "No tags were created or pushed."
    exit 0
fi

# Create and push tag.
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
