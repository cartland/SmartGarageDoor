#!/bin/bash
# Git workflow guardrails. Blocks dangerous operations and enforces conventions.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
[ -z "$COMMAND" ] && exit 0

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"

deny() {
  jq -n --arg reason "$1" '{
    "hookSpecificOutput": {
      "hookEventName": "PreToolUse",
      "permissionDecision": "deny",
      "permissionDecisionReason": $reason
    }
  }'
  exit 0
}

# Strip heredoc bodies and quoted strings to avoid false positives on PR body text.
STRIPPED=$(echo "$COMMAND" | sed '/<<.*EOF/,/^EOF/d' | sed -E "s/'[^']*'//g; s/\"[^\"]*\"//g")

# --- Block absolute paths to gradlew (use repo-relative paths) ---
if echo "$STRIPPED" | grep -qE '(^|[;&|]\s*)/[^ ]*gradlew\b'; then
  deny "BLOCKED: Use repo-relative path to gradlew (e.g., AndroidGarage/gradlew -p AndroidGarage), not an absolute path."
fi

# --- Block cd (run everything from repo root) ---
# Match cd as a command (start of line, or after && || ; |), not as part of a word like "block-cd"
if echo "$STRIPPED" | grep -qE '(^|[;&|]\s*)cd\s'; then
  deny "BLOCKED: cd is not allowed. Run commands from the repository root using absolute paths or -p flags (e.g., ./gradlew -p AndroidGarage)."
fi

# --- Block git -C (bare repository attack vector) ---
if echo "$STRIPPED" | grep -qE '\bgit\s+-C\b'; then
  deny "BLOCKED: git -C is not allowed. Run git from the repository root."
fi

# --- Block push to main ---
if echo "$STRIPPED" | grep -qE '\bgit\s+push\b'; then
  if echo "$STRIPPED" | grep -qE '\bgit\s+push\b.*\b(main|master)\b'; then
    deny "BLOCKED: Never push directly to main. Create a branch and open a PR."
  fi
  if [ -n "$REPO_ROOT" ]; then
    CURRENT_BRANCH=$(git branch --show-current 2>/dev/null)
    if [ "$CURRENT_BRANCH" = "main" ] || [ "$CURRENT_BRANCH" = "master" ]; then
      deny "BLOCKED: You are on '$CURRENT_BRANCH'. Switch to a feature branch before pushing."
    fi
  fi

  # Block --force, allow --force-with-lease on feature branches
  if echo "$STRIPPED" | grep -qE '\bgit\s+push\b.*--force\b' && ! echo "$STRIPPED" | grep -qF -- '--force-with-lease'; then
    deny "BLOCKED: Use --force-with-lease instead of --force for safety."
  fi

  # Block push to a branch whose PR has auto-merge enabled (race condition risk)
  if [ -n "$REPO_ROOT" ]; then
    PUSH_BRANCH=$(git branch --show-current 2>/dev/null)
    if [ -n "$PUSH_BRANCH" ] && [ "$PUSH_BRANCH" != "main" ] && [ "$PUSH_BRANCH" != "master" ]; then
      PR_AUTO_MERGE=$(gh pr list --head "$PUSH_BRANCH" --json number,autoMergeRequest --jq '.[0] | select(.autoMergeRequest != null) | .number' 2>/dev/null || true)
      if [ -n "$PR_AUTO_MERGE" ]; then
        deny "BLOCKED: PR #$PR_AUTO_MERGE has auto-merge enabled. Pushing now risks the merge executing before your commits arrive, silently losing them. Disable auto-merge first: gh pr merge --disable-auto $PR_AUTO_MERGE"
      fi
    fi
  fi

  # Warn if validate.sh hasn't been run
  if [ -n "$REPO_ROOT" ]; then
    MARKER="$REPO_ROOT/.claude/.validation-passed"
    VALIDATED_HASH=$(cat "$MARKER" 2>/dev/null)
    CURRENT_HEAD=$(git rev-parse HEAD 2>/dev/null)
    if [ "$VALIDATED_HASH" != "$CURRENT_HEAD" ]; then
      # Skip warning for docs-only changes
      CHANGED_FILES=$(git diff --name-only origin/main...HEAD 2>/dev/null)
      DOCS_ONLY=true
      for f in $CHANGED_FILES; do
        case "$f" in
          *.md|.claude/*|.beads/*|.github/*) ;;
          *) DOCS_ONLY=false; break ;;
        esac
      done
      if [ "$DOCS_ONLY" = "false" ]; then
        echo "WARNING: Consider running ./scripts/validate.sh before pushing." >&2
      fi
    fi
  fi
fi

# --- Block tag creation (use release scripts instead) ---
if echo "$STRIPPED" | grep -qE '\bgit\s+tag\b'; then
  if ! echo "$STRIPPED" | grep -qE '\bgit\s+tag\b.*(-l\b|--list\b)'; then
    deny "BLOCKED: Never create or push tags directly. Use ./scripts/release-android.sh or ./scripts/release-firebase.sh for releases. Use git tag -l to list tags."
  fi
fi

# --- Enforce squash merge ---
if echo "$STRIPPED" | grep -qE '\bgh\s+pr\s+merge\b'; then
  if ! echo "$STRIPPED" | grep -qF -- '--squash'; then
    deny "BLOCKED: Always use --squash --delete-branch when merging PRs."
  fi
fi

# --- Warn on Room database changes ---
if echo "$STRIPPED" | grep -qE '\bgit\s+(add|commit)\b'; then
  # Check if any staged files are Room entities, DAOs, or the database class
  STAGED=$(git diff --cached --name-only 2>/dev/null)
  ROOM_CHANGED=false
  for f in $STAGED; do
    case "$f" in
      *Entity.kt|*Dao.kt|*AppDatabase.kt|*Migration*.kt)
        ROOM_CHANGED=true
        break
        ;;
    esac
  done
  if [ "$ROOM_CHANGED" = "true" ]; then
    echo "WARNING: Room database files are being committed." >&2
    echo "  - If the schema changed, increment the version in @Database" >&2
    echo "  - Run ./scripts/validate.sh (includes Room schema drift check)" >&2
    echo "  - Commit the updated schema JSON from androidApp/schemas/" >&2
  fi
fi

# --- Warn when changes should trigger instrumented tests ---
if echo "$STRIPPED" | grep -qE '\bgit\s+push\b'; then
  if [ -n "$REPO_ROOT" ]; then
    CHANGED_FILES=$(git diff --name-only origin/main...HEAD 2>/dev/null)
    NEEDS_INSTRUMENTED=false
    for f in $CHANGED_FILES; do
      case "$f" in
        *Entity.kt|*Dao.kt|*AppDatabase.kt|*AppComponent.kt|*MainActivity.kt|*Main.kt|*Navigation*.kt|*LocalDoorDataSource.kt|*DoorRepository*.kt)
          NEEDS_INSTRUMENTED=true
          break
          ;;
      esac
    done
    if [ "$NEEDS_INSTRUMENTED" = "true" ]; then
      echo "WARNING: Changes touch Room/DI/navigation code." >&2
      echo "  Consider running instrumented tests: ./scripts/run-instrumented-tests.sh" >&2
    fi
  fi
fi

# --- Block destructive git commands ---
if echo "$STRIPPED" | grep -qE '\bgit\s+reset\s+--hard\b'; then
  deny "BLOCKED: git reset --hard discards commits. Use git stash or a backup branch."
fi
if echo "$STRIPPED" | grep -qE '\bgit\s+clean\b.*-[a-zA-Z]*f'; then
  deny "BLOCKED: git clean -f permanently deletes untracked files."
fi
if echo "$STRIPPED" | grep -qE '\bgit\s+(checkout|restore)\s+\.\s*$'; then
  deny "BLOCKED: Discarding all changes is destructive. Use git stash or target specific files."
fi

exit 0
