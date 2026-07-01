#!/usr/bin/env bash
set -euo pipefail

# Release the iOS app by creating and pushing an ios/N tag.
# This triggers release-ios.yml to archive + upload to TestFlight (Internal).
#
# Mirrors scripts/release-android.sh — same flags, same --check copy-paste
# workflow, same override design. Differences:
#   - Tag is ios/N where N is the build number (CURRENT_PROJECT_VERSION); the
#     workflow passes N to the archive, so the tag is the source of truth for it.
#   - The user-facing version (MARKETING_VERSION, X.Y.Z) is read from
#     AndroidGarage/iosApp/project.yml and is the key for the CHANGELOG gate.
#   - The CHANGELOG is AndroidGarage/iosApp/CHANGELOG.md.
#   - The validation marker is written by scripts/validate-ios.sh (macOS-only).
#
# IMPORTANT: only uploads to TestFlight Internal, never the App Store.
#
# USAGE
#
# Start with --check. It prints the exact command(s) to run next, with real
# values (SHAs, tag names) filled in. Copy-paste, don't re-type from memory.
#
#   ./scripts/release-ios.sh --check
#
# Normal release (validation marker matches HEAD):
#   ./scripts/release-ios.sh --confirm-tag ios/N
#
# Emergency release (validation has not passed for HEAD):
#   ./scripts/release-ios.sh \
#       --confirm-tag ios/N \
#       --confirm-unvalidated-release <40-char-sha>
#
# Rollback release (detached HEAD on an older tag):
#   git checkout ios/M
#   ./scripts/release-ios.sh --check
#   ./scripts/release-ios.sh \
#       --confirm-tag ios/N \
#       --confirm-hash <40-char-sha-of-target> \
#       --confirm-rollback-from <40-char-sha-of-previous-latest>
#
# DESIGN PRINCIPLE
#
# Every override asks you to state a value from reality (a SHA, a tag). The
# script fails if the value does not match. Correct usage is easy (read from
# --check); accidental usage is hard (you'd have to type the right value for
# the wrong situation).

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

MODE="interactive"
CONFIRM_TAG=""
CONFIRM_HASH=""
CONFIRM_ROLLBACK_FROM=""
CONFIRM_UNVALIDATED_RELEASE=""
CONFIRM_NO_CHANGELOG=""
DRY_RUN=false

show_help() {
    cat <<'EOF'
Usage: ./scripts/release-ios.sh [OPTIONS]

Recommended workflow: run with --check first. It prints the exact command to
run next, with SHAs filled in. Copy-paste that command.

Modes:
  (no args)                 Interactive mode — prompts for confirmation
  --check                   Print state + recommended next command, then exit
  --confirm-tag <tag>       Non-interactive release. Tag must match computed next tag.
  --dry-run                 Show what would happen without creating tags

Override flags (all require a specific value from --check output):
  --confirm-hash <sha>              Release a specific commit. SHA must match exactly.
  --confirm-rollback-from <sha>     Required for rollback releases. Must match the
                                    SHA the latest tag currently points to.
  --confirm-unvalidated-release <sha>
                                    Release without a validation marker match.
                                    SHA must equal the target commit.
  --confirm-no-changelog <sha>      Release without a CHANGELOG entry for the
                                    current MARKETING_VERSION. SHA must equal the
                                    target commit. Add the entry retroactively.

Help:
  -h, --help                Show this help

Examples:
  ./scripts/release-ios.sh --check
  ./scripts/release-ios.sh --confirm-tag ios/2
EOF
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --check) MODE="check"; shift ;;
        --confirm-tag) MODE="confirm"; CONFIRM_TAG="$2"; shift 2 ;;
        --confirm-hash) CONFIRM_HASH="$2"; shift 2 ;;
        --confirm-rollback-from) CONFIRM_ROLLBACK_FROM="$2"; shift 2 ;;
        --confirm-unvalidated-release) CONFIRM_UNVALIDATED_RELEASE="$2"; shift 2 ;;
        --confirm-no-changelog) CONFIRM_NO_CHANGELOG="$2"; shift 2 ;;
        --dry-run) DRY_RUN=true; shift ;;
        -h|--help) show_help; exit 0 ;;
        *) echo -e "${RED}Error: Unknown option: $1${RESET}"; show_help; exit 1 ;;
    esac
done

git fetch origin --tags --quiet

# --- App Store Connect build-number authority (optional, graceful) ---
# App Store Connect is the real source of truth for the CFBundleVersion, and the
# workflow numbers each build by the tag N. If ASC credentials are available
# locally, compute the next tag as (latest ASC build + 1) so the ios/N tag always
# equals the next FREE build number — even if a build was uploaded out-of-band
# (which is what put ios/1 at CFBundleVersion 2). Load creds from
# scripts/.asc-credentials.local (gitignored; see scripts/asc-credentials.local.example)
# or the environment. If unavailable, fall back to git tags only and say so; the CI
# pre-flight in release-ios.yml enforces the same check regardless.
ASC_CREDS_FILE="$REPO_ROOT/scripts/.asc-credentials.local"
# shellcheck disable=SC1090
[ -f "$ASC_CREDS_FILE" ] && . "$ASC_CREDS_FILE"
ASC_LATEST=""
if command -v ruby >/dev/null 2>&1 && [ -n "${ASC_KEY_ID:-}" ] && [ -n "${ASC_ISSUER_ID:-}" ] && [ -n "${ASC_KEY_PATH:-}" ]; then
    ASC_ERR="$(mktemp)"
    if ASC_LATEST=$(ASC_BUNDLE_ID="${ASC_BUNDLE_ID:-com.chriscartland.garage}" \
            ruby "$REPO_ROOT/scripts/asc-latest-build.rb" 2>"$ASC_ERR"); then
        ASC_NOTE="App Store Connect latest build: $ASC_LATEST (authoritative for the build number)"
    else
        ASC_NOTE="App Store Connect check FAILED — $(head -1 "$ASC_ERR" 2>/dev/null); using git tags only"
        ASC_LATEST=""
    fi
    rm -f "$ASC_ERR"
else
    ASC_NOTE="App Store Connect check skipped (no local ASC creds — see scripts/asc-credentials.local.example); using git tags only"
fi

# Highest ios/N git tag (0 if none).
GIT_HIGHEST=$(git tag -l 'ios/[0-9]*' | sed 's|ios/||' | grep -E '^[0-9]+$' | sort -n | tail -1)
GIT_HIGHEST=${GIT_HIGHEST:-0}

# Next build number = strictly greater than BOTH the highest git tag AND the
# latest App Store Connect build (when known), so the tag can never collide.
if [ -n "$ASC_LATEST" ] && [ "$ASC_LATEST" -gt "$GIT_HIGHEST" ]; then
    BASE=$ASC_LATEST
else
    BASE=$GIT_HIGHEST
fi
NEXT_VERSION=$((BASE + 1))
NEW_TAG="ios/$NEXT_VERSION"

if [ "$GIT_HIGHEST" -eq 0 ]; then
    LATEST_TAG="(none)"
    LATEST_TAG_SHA="(none)"
else
    LATEST_TAG="ios/$GIT_HIGHEST"
    LATEST_TAG_SHA=$(git rev-parse "$LATEST_TAG" 2>/dev/null || echo "(missing)")
fi

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
    CURRENT_BRANCH="(detached)"
fi

# Validation marker (written by scripts/validate-ios.sh on success).
#   matches : marker exists and matches TARGET_COMMIT
#   stale   : marker exists but matches some other commit
#   missing : marker file does not exist
VALIDATION_FILE="$REPO_ROOT/.claude/.ios-validation-passed"
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

# MARKETING_VERSION from project.yml — the X.Y.Z the CHANGELOG heading uses.
PROJECT_SPEC="$REPO_ROOT/AndroidGarage/iosApp/project.yml"
VERSION_NAME=""
if [ -f "$PROJECT_SPEC" ]; then
    VERSION_NAME=$(awk -F'"' '/^[[:space:]]*MARKETING_VERSION[[:space:]]*:/ { print $2; exit }' "$PROJECT_SPEC")
fi

# Escape regex special chars so values like `0.1.0` match literally.
VERSION_NAME_RE=""
if [ -n "$VERSION_NAME" ]; then
    # shellcheck disable=SC2001
    VERSION_NAME_RE=$(echo "$VERSION_NAME" | sed 's/[][\\.+*?(){}^$|]/\\&/g')
fi

# CHANGELOG state for the MARKETING_VERSION we're about to release.
#   present  : `## X.Y.Z` heading exists with a non-empty body
#   empty    : heading exists but body is whitespace-only
#   missing  : no heading for this version
#   no_file  : AndroidGarage/iosApp/CHANGELOG.md does not exist
#   no_ver   : MARKETING_VERSION could not be parsed (hard failure)
# Fenced code blocks are ignored; the heading must be `^## X.Y.Z` followed by
# end-of-line or whitespace so `## 0.1` does not match `0.1.0`.
CHANGELOG_FILE="$REPO_ROOT/AndroidGarage/iosApp/CHANGELOG.md"
CHANGELOG_STATE="no_file"
CHANGELOG_BODY=""
if [ -z "$VERSION_NAME" ]; then
    CHANGELOG_STATE="no_ver"
elif [ -f "$CHANGELOG_FILE" ]; then
    CHANGELOG_BODY=$(awk -v ver="$VERSION_NAME_RE" '
        BEGIN { in_fence = 0; found = 0 }
        /^```/ { in_fence = !in_fence; next }
        in_fence { next }
        $0 ~ ("^## "ver"([ ]|$)") { found = 1; next }
        found && /^## / { exit }
        found { print }
    ' "$CHANGELOG_FILE")
    HEADING_FOUND=$(awk -v ver="$VERSION_NAME_RE" '
        BEGIN { in_fence = 0 }
        /^```/ { in_fence = !in_fence; next }
        in_fence { next }
        $0 ~ ("^## "ver"([ ]|$)") { print "yes"; exit }
    ' "$CHANGELOG_FILE")
    if [ -n "$(echo "$CHANGELOG_BODY" | tr -d '[:space:]')" ]; then
        CHANGELOG_STATE="present"
    elif [ "$HEADING_FOUND" = "yes" ]; then
        CHANGELOG_STATE="empty"
    else
        CHANGELOG_STATE="missing"
    fi
fi

# Rollback detection: target commit is already tagged (not the latest).
EXISTING_TAGS_ON_TARGET=$(git tag -l 'ios/[0-9]*' --points-at "$TARGET_COMMIT" 2>/dev/null | sort -t/ -k2 -n || true)
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
    echo "Next tag:     $NEW_TAG  (build number $NEXT_VERSION)"
    echo "Build source: $ASC_NOTE"
    echo "HEAD:         $TARGET_COMMIT"
    echo "Branch:       $CURRENT_BRANCH"

    case "$VALIDATION_STATE" in
        matches) echo -e "Validation:   ${GREEN}PASSED${RESET} on $VALIDATED_COMMIT" ;;
        stale)   echo -e "Validation:   ${YELLOW}STALE${RESET} (marker is for $VALIDATED_COMMIT, not HEAD)" ;;
        missing) echo -e "Validation:   ${YELLOW}MISSING${RESET} — run ./scripts/validate-ios.sh first" ;;
    esac

    if [ -n "$VERSION_NAME" ]; then
        echo "MarketingVer: $VERSION_NAME"
    else
        echo -e "MarketingVer: ${YELLOW}NOT FOUND${RESET} (could not parse MARKETING_VERSION in project.yml)"
    fi

    case "$CHANGELOG_STATE" in
        present) echo -e "Changelog:    ${GREEN}PRESENT${RESET} (entry for $VERSION_NAME in iosApp/CHANGELOG.md)" ;;
        empty)   echo -e "Changelog:    ${YELLOW}EMPTY${RESET} (heading for $VERSION_NAME has no body)" ;;
        missing) echo -e "Changelog:    ${YELLOW}MISSING${RESET} (no heading for $VERSION_NAME in iosApp/CHANGELOG.md)" ;;
        no_file) echo -e "Changelog:    ${YELLOW}NO FILE${RESET} (iosApp/CHANGELOG.md does not exist)" ;;
        no_ver)  echo -e "Changelog:    ${YELLOW}SKIPPED${RESET} (MARKETING_VERSION not found)" ;;
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
        echo "  ./scripts/release-ios.sh \\"
        echo "      --confirm-tag $NEW_TAG \\"
        echo "      --confirm-hash $TARGET_COMMIT \\"
        echo "      --confirm-rollback-from $LATEST_TAG_SHA"
        echo ""
        EXTRAS=()
        if [ "$VALIDATION_STATE" != "matches" ]; then
            EXTRAS+=("--confirm-unvalidated-release $TARGET_COMMIT")
        fi
        if [ "$CHANGELOG_STATE" != "present" ] && [ "$CHANGELOG_STATE" != "no_ver" ]; then
            EXTRAS+=("--confirm-no-changelog $TARGET_COMMIT")
        fi
        if [ ${#EXTRAS[@]} -gt 0 ]; then
            echo "Append the following for the conditions flagged above:"
            for extra in "${EXTRAS[@]}"; do echo "      $extra"; done
            echo ""
        fi
    elif [ "$CHANGELOG_STATE" = "no_ver" ]; then
        echo -e "${RED}Cannot release: MARKETING_VERSION not found in project.yml.${RESET}"
        echo ""
        echo "Expected a line like:    MARKETING_VERSION: \"X.Y.Z\""
        echo "Then re-run: ./scripts/release-ios.sh --check"
        echo ""
    elif [ "$VALIDATION_STATE" != "matches" ]; then
        echo "Validation is not passing for HEAD. Two options:"
        echo ""
        echo "(1) Run validation, then re-run --check (recommended):"
        echo "    ./scripts/validate-ios.sh && ./scripts/release-ios.sh --check"
        echo ""
        echo "(2) Release WITHOUT validation (emergency only):"
        echo ""
        echo "    ./scripts/release-ios.sh \\"
        echo "        --confirm-tag $NEW_TAG \\"
        echo "        --confirm-unvalidated-release $TARGET_COMMIT"
        echo ""
    elif [ "$CHANGELOG_STATE" != "present" ]; then
        echo "No CHANGELOG entry for $VERSION_NAME. Two options:"
        echo ""
        echo "(1) Add an entry, commit, push, re-run --check (recommended):"
        echo ""
        echo "    Edit AndroidGarage/iosApp/CHANGELOG.md and add at the top:"
        echo ""
        echo "      ## $VERSION_NAME"
        echo "      - <one or more bullets describing user-facing changes>"
        echo ""
        echo "    Then: git commit + push, ./scripts/validate-ios.sh, ./scripts/release-ios.sh --check"
        echo ""
        echo "(2) Release WITHOUT a changelog entry (emergency only):"
        echo ""
        echo "    ./scripts/release-ios.sh \\"
        echo "        --confirm-tag $NEW_TAG \\"
        echo "        --confirm-no-changelog $TARGET_COMMIT"
        echo ""
    else
        echo "Copy and run this command to release:"
        echo ""
        echo "  ./scripts/release-ios.sh --confirm-tag $NEW_TAG"
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
        echo "  ./scripts/release-ios.sh --check"
        exit 1
    fi
fi

echo -e "${BOLD}=== iOS Release ===${RESET}"
echo ""

# === Gate: Clean working tree (only when tagging HEAD) ===
if [ -z "$CONFIRM_HASH" ]; then
    # Reconcile git's stat-cache before the fast dirty check — validate-ios.sh /
    # xcodegen touch tracked files (mtime) without changing content, which can make
    # diff-index report a false "dirty". --refresh re-stats and clears mtime-only
    # false positives; it never masks a real change. (Same trap as release-android.sh.)
    git update-index -q --refresh > /dev/null 2>&1 || true
    if ! git diff-index --quiet HEAD --; then
        echo -e "${RED}Error: Working tree has uncommitted changes.${RESET}"
        echo "Commit or stash changes before releasing."
        exit 1
    fi

    UNTRACKED=$(git ls-files --others --exclude-standard 2>/dev/null)
    if [ -n "$UNTRACKED" ]; then
        echo -e "${YELLOW}Warning: Untracked files detected:${RESET}"
        echo "$UNTRACKED" | head -10
        echo ""
        if [ "$MODE" = "interactive" ]; then
            read -p "Continue with untracked files? (y/N) " -n 1 -r
            echo
            [[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }
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
    echo "  ./scripts/release-ios.sh --check"
    exit 1
fi

if [ -n "$CONFIRM_HASH" ]; then
    echo -e "${YELLOW}Releasing specific commit: $TARGET_COMMIT_SHORT${RESET}"
    echo "  Resolved from: $CONFIRM_HASH"
    echo "  Full SHA:      $TARGET_COMMIT"
    echo ""
fi

# === Gate: Rollback confirmation ===
if [ -n "$CONFIRM_ROLLBACK_FROM" ]; then
    if [ "$CONFIRM_ROLLBACK_FROM" != "$LATEST_TAG_SHA" ]; then
        echo -e "${RED}Error: --confirm-rollback-from does not match the latest tag's commit.${RESET}"
        echo "  Expected (sha of $LATEST_TAG): $LATEST_TAG_SHA"
        echo "  Got:                           $CONFIRM_ROLLBACK_FROM"
        exit 1
    fi
    echo -e "${YELLOW}Rollback confirmed: $LATEST_TAG -> $NEW_TAG at $TARGET_COMMIT_SHORT${RESET}"
    echo ""
elif [ "$IS_ROLLBACK" = "true" ]; then
    echo -e "${RED}Error: Target commit is on tag $NEWEST_TAG_ON_TARGET, older than $LATEST_TAG.${RESET}"
    echo "  This looks like a rollback but --confirm-rollback-from was not provided."
    echo "  Run --check to see the correct command."
    exit 1
fi

# === Gate: Validation ===
if [ "$VALIDATION_STATE" = "matches" ]; then
    echo -e "${GREEN}Validation passed for this commit.${RESET}"
elif [ -n "$CONFIRM_UNVALIDATED_RELEASE" ]; then
    if [ "$CONFIRM_UNVALIDATED_RELEASE" != "$TARGET_COMMIT" ]; then
        echo -e "${RED}Error: --confirm-unvalidated-release SHA does not match target commit.${RESET}"
        echo "  Expected (target commit): $TARGET_COMMIT"
        echo "  Got:                      $CONFIRM_UNVALIDATED_RELEASE"
        exit 1
    fi
    echo -e "${YELLOW}WARNING: Releasing without validation (--confirm-unvalidated-release).${RESET}"
    echo ""
else
    if [ "$MODE" = "interactive" ]; then
        if [ "$VALIDATION_STATE" = "stale" ]; then
            echo -e "${YELLOW}Warning: validation marker is stale.${RESET}"
            echo "  Marker SHA: $VALIDATED_COMMIT"
            echo "  Target SHA: $TARGET_COMMIT"
        else
            echo -e "${YELLOW}Warning: no validation marker — run ./scripts/validate-ios.sh first.${RESET}"
        fi
        read -p "Continue without validation? (y/N) " -n 1 -r
        echo ""
        [[ $REPLY =~ ^[Yy]$ ]] || { echo "Aborted. Run ./scripts/validate-ios.sh first, or run --check for options."; exit 1; }
    else
        echo -e "${RED}Error: validation has not passed for this commit.${RESET}"
        echo "  Run --check to see the exact command for this state."
        exit 1
    fi
fi

# === Gate: CHANGELOG entry ===
if [ "$CHANGELOG_STATE" = "present" ]; then
    FIRST_LINE=$(echo "$CHANGELOG_BODY" | grep -m1 -E '[^[:space:]]' || echo "")
    echo -e "${GREEN}Changelog entry found for $VERSION_NAME.${RESET}"
    [ -n "$FIRST_LINE" ] && echo "  First line: $(echo "$FIRST_LINE" | head -c 120)"
elif [ "$CHANGELOG_STATE" = "no_ver" ]; then
    echo -e "${RED}Error: MARKETING_VERSION could not be parsed from project.yml.${RESET}"
    echo "  Expected a line like: MARKETING_VERSION: \"X.Y.Z\""
    echo "  Fix project.yml before releasing — do not use --confirm-no-changelog for this."
    exit 1
elif [ -n "$CONFIRM_NO_CHANGELOG" ]; then
    if [ "$CONFIRM_NO_CHANGELOG" != "$TARGET_COMMIT" ]; then
        echo -e "${RED}Error: --confirm-no-changelog SHA does not match target commit.${RESET}"
        echo "  Expected (target commit): $TARGET_COMMIT"
        echo "  Got:                      $CONFIRM_NO_CHANGELOG"
        exit 1
    fi
    echo -e "${YELLOW}WARNING: Releasing without CHANGELOG entry (--confirm-no-changelog).${RESET}"
    echo "  Add an entry to AndroidGarage/iosApp/CHANGELOG.md after the fact."
    echo ""
else
    case "$CHANGELOG_STATE" in
        missing) REASON="no heading for $VERSION_NAME in iosApp/CHANGELOG.md" ;;
        empty)   REASON="heading for $VERSION_NAME exists but body is empty" ;;
        no_file) REASON="AndroidGarage/iosApp/CHANGELOG.md does not exist" ;;
        *)       REASON="unknown" ;;
    esac
    echo -e "${RED}Error: CHANGELOG gate failed ($REASON).${RESET}"
    echo "  Run --check to see how to fix (write the entry, or emergency override)."
    exit 1
fi

# Existing tags on this commit (informational).
if [ -n "$EXISTING_TAGS_ON_TARGET" ]; then
    NEWEST_TAG_ON_COMMIT=$(echo "$EXISTING_TAGS_ON_TARGET" | tail -1)
    if [ "$NEWEST_TAG_ON_COMMIT" = "$LATEST_TAG" ]; then
        echo -e "${YELLOW}Note: This commit already has $LATEST_TAG.${RESET}"
        echo "  Creating $NEW_TAG on the same commit (build bump, no code change)."
    else
        echo -e "${YELLOW}Note: This commit previously released as: $(echo "$EXISTING_TAGS_ON_TARGET" | tr '\n' ' ')${RESET}"
    fi
fi

echo ""
echo "=== Release Details ==="
echo "  Target:      $TARGET_SOURCE"
echo "  Branch:      $CURRENT_BRANCH"
echo "  Latest tag:  $LATEST_TAG ($LATEST_TAG_SHA)"
echo "  New tag:     $NEW_TAG  (build number $NEXT_VERSION)"
echo "  Marketing:   $VERSION_NAME"
echo "  Build src:   $ASC_NOTE"
echo "  Commit:      $TARGET_COMMIT_SHORT ($TARGET_COMMIT)"
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
    echo "Would push tag to origin (triggers release-ios.yml → TestFlight)."
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
