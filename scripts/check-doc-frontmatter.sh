#!/usr/bin/env bash
# check-doc-frontmatter.sh
#
# Validates the YAML front-matter contract defined in docs/AGENTS.md
# against every in-scope markdown file in the repo.
#
# Contract:
#   ---
#   category: reference | plan | archive
#   status:   active | shipped | superseded
#   last_verified: YYYY-MM-DD       (required when category=reference + status=active)
#   superseded_by: path/to/doc.md   (required iff status=superseded; forbidden otherwise)
#   ---
#
# `last_verified` older than 90 days surfaces as a warning. Warnings
# never fail the build. Only contract violations (missing required
# fields, invalid enum values, missing front-matter block, mismatched
# superseded_by rules) cause exit 1.
#
# Skill files in .claude/skills/ have their own native Claude Code
# frontmatter and are excluded. ESP32 dirs (GarageFirmware_ESP32/,
# Arduino_ESP32/) are out of primary scope and excluded.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RED=$'\033[0;31m'
YELLOW=$'\033[0;33m'
RESET=$'\033[0m'

ERRORS=0
WARNINGS=0

err() {
    printf '%serror%s %s\n' "$RED" "$RESET" "$1" >&2
    ERRORS=$((ERRORS + 1))
}

warn() {
    printf '%swarn%s  %s\n' "$YELLOW" "$RESET" "$1"
    WARNINGS=$((WARNINGS + 1))
}

# In-scope directories. Exclusions are handled below.
SCOPE=(
    "$REPO_ROOT/AndroidGarage"
    "$REPO_ROOT/FirebaseServer/README.md"
    "$REPO_ROOT/FirebaseServer/CHANGELOG.md"
    "$REPO_ROOT/docs"
    "$REPO_ROOT/README.md"
    "$REPO_ROOT/CLAUDE.md"
)

# Directories / file patterns to skip (relative to REPO_ROOT for dir checks,
# anywhere for path-fragment matches).
should_skip() {
    local path="$1"
    case "$path" in
        */node_modules/*) return 0 ;;
        */build/*) return 0 ;;
        */.gradle/*) return 0 ;;
        */.claude/skills/*) return 0 ;;
        */.claude/worktrees/*) return 0 ;;
        */.claude/projects/*) return 0 ;;
        */android-screenshot-tests/*SCREENSHOT_GALLERY.md) return 0 ;;
        */android-screenshot-tests/collections/*) return 0 ;;
        */detekt.md) return 0 ;;
        */GarageFirmware_ESP32/*) return 0 ;;
        */Arduino_ESP32/*) return 0 ;;
        # PR-review files have their own historical frontmatter convention
        # (pr/state/date). Treated as a self-contained archive; not held
        # to the AGENTS.md contract.
        */pr-review/*) return 0 ;;
    esac
    return 1
}

# Today in epoch seconds (portable: macOS uses BSD date, Linux GNU date).
TODAY_EPOCH=$(date +%s)

date_to_epoch() {
    # Accepts YYYY-MM-DD; outputs epoch seconds. Returns non-zero on parse failure.
    local d="$1"
    if date -j -f "%Y-%m-%d" "$d" "+%s" 2>/dev/null; then
        return 0
    fi
    if date -d "$d" "+%s" 2>/dev/null; then
        return 0
    fi
    return 1
}

validate_file() {
    local file="$1"
    local rel="${file#$REPO_ROOT/}"

    # Read the file once with CR stripped so CRLF-terminated files validate
    # the same as LF (hit during a doc edit on 2026-04-25 — AndroidGarage/README.md
    # was the lone CRLF file in the repo and tripped the "---" comparison silently).
    # Use here-strings (<<<) downstream to avoid SIGPIPE under `set -o pipefail`.
    local content
    content=$(tr -d '\r' < "$file" 2>/dev/null || true)

    # First line must be the front-matter open delimiter.
    local first_line="${content%%$'\n'*}"
    if [ "$first_line" != "---" ]; then
        err "$rel:1: missing YAML front-matter (first line must be '---')"
        return
    fi

    # Find the closing delimiter line number (look in the first 30 lines).
    local close_line
    close_line=$(awk 'NR>1 && /^---$/ { print NR; exit }' <<<"$content")
    if [ -z "$close_line" ]; then
        err "$rel:1: front-matter block has no closing '---'"
        return
    fi

    # Extract the YAML lines (between line 2 and close_line-1).
    local yaml
    yaml=$(sed -n "2,$((close_line - 1))p" <<<"$content")

    local category status last_verified superseded_by
    category=$(awk -F': *' '$1=="category"{print $2; exit}' <<<"$yaml")
    status=$(awk -F': *' '$1=="status"{print $2; exit}' <<<"$yaml")
    last_verified=$(awk -F': *' '$1=="last_verified"{print $2; exit}' <<<"$yaml")
    superseded_by=$(awk -F': *' '$1=="superseded_by"{print $2; exit}' <<<"$yaml")

    # category required, enum.
    case "$category" in
        reference|plan|archive) ;;
        "") err "$rel: missing required field 'category'" ;;
        *)  err "$rel: invalid 'category' value '$category' (allowed: reference|plan|archive)" ;;
    esac

    # status required, enum.
    case "$status" in
        active|shipped|superseded) ;;
        "") err "$rel: missing required field 'status'" ;;
        *)  err "$rel: invalid 'status' value '$status' (allowed: active|shipped|superseded)" ;;
    esac

    # superseded_by required iff status=superseded.
    if [ "$status" = "superseded" ]; then
        if [ -z "$superseded_by" ]; then
            err "$rel: status=superseded requires 'superseded_by' field"
        else
            local target="$REPO_ROOT/$superseded_by"
            if [ ! -f "$target" ]; then
                err "$rel: superseded_by target '$superseded_by' does not exist"
            fi
        fi
    elif [ -n "$superseded_by" ]; then
        err "$rel: 'superseded_by' is forbidden unless status=superseded"
    fi

    # last_verified required when category=reference + status=active.
    if [ "$category" = "reference" ] && [ "$status" = "active" ]; then
        if [ -z "$last_verified" ]; then
            err "$rel: category=reference + status=active requires 'last_verified' (YYYY-MM-DD)"
        elif ! [[ "$last_verified" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
            err "$rel: 'last_verified' must be ISO date YYYY-MM-DD, got '$last_verified'"
        else
            local lv_epoch
            if lv_epoch=$(date_to_epoch "$last_verified"); then
                local age_days=$(( (TODAY_EPOCH - lv_epoch) / 86400 ))
                if [ "$age_days" -gt 90 ]; then
                    warn "$rel: last_verified is $age_days days old (>90 day soft threshold)"
                fi
            else
                err "$rel: could not parse last_verified date '$last_verified'"
            fi
        fi
    fi
}

# Walk the scoped paths.
for entry in "${SCOPE[@]}"; do
    if [ ! -e "$entry" ]; then
        continue
    fi
    if [ -f "$entry" ]; then
        case "$entry" in
            *.md) validate_file "$entry" ;;
        esac
    elif [ -d "$entry" ]; then
        while IFS= read -r -d '' file; do
            if should_skip "$file"; then
                continue
            fi
            validate_file "$file"
        done < <(find "$entry" -name '*.md' -type f -print0)
    fi
done

if [ "$ERRORS" -gt 0 ]; then
    printf '\n%s%d error(s)%s, %d warning(s).\n' "$RED" "$ERRORS" "$RESET" "$WARNINGS" >&2
    exit 1
fi

if [ "$WARNINGS" -gt 0 ]; then
    printf '\n%d warning(s).\n' "$WARNINGS"
fi

exit 0
