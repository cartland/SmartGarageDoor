#!/bin/bash
# Stop hook: Developer mode — keep creating PRs aligned with project docs.
#
# Toggle on:  touch .claude/.dev-mode
# Toggle off: rm .claude/.dev-mode

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
[ -z "$REPO_ROOT" ] && exit 0

MARKER="$REPO_ROOT/.claude/.dev-mode"
[ ! -f "$MARKER" ] && exit 0

# Read stop reason — only block on end_turn, not on tool_use or errors
INPUT=$(cat)
STOP_REASON=$(echo "$INPUT" | jq -r '.stop_reason // "end_turn"')
[ "$STOP_REASON" != "end_turn" ] && exit 0

jq -n '{
  "decision": "block",
  "reason": "Dev mode is active (.claude/.dev-mode). Continue making progress:\n\n1. Check open PRs — merge any that have passed CI\n2. Read docs/TESTING.md and docs/MIGRATION.md for next action items\n3. Pick the highest-priority item not yet done and create a PR for it\n4. Run ./scripts/validate.sh before pushing\n5. Keep PRs small and focused (one concern per PR)\n\nTo stop: tell the user to run `rm .claude/.dev-mode`"
}'
