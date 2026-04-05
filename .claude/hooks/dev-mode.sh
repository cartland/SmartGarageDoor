#!/bin/bash
# Stop hook: Developer mode — keep creating PRs aligned with project docs.
#
# Toggle on:  touch .claude/.dev-mode
# Toggle off: rm .claude/.dev-mode
#
# Yields when all open PRs are waiting on CI and backlog is full (5+).
# In that case, there's nothing productive to do — let Claude stop.

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
[ -z "$REPO_ROOT" ] && exit 0

MARKER="$REPO_ROOT/.claude/.dev-mode"
[ ! -f "$MARKER" ] && exit 0

# Read stop reason — only block on end_turn, not on tool_use or errors
INPUT=$(cat)
STOP_REASON=$(echo "$INPUT" | jq -r '.stop_reason // "end_turn"')
[ "$STOP_REASON" != "end_turn" ] && exit 0

cd "$REPO_ROOT" || exit 0

# Count open PRs (excluding dependabot)
OPEN_PRS=$(gh pr list --state open --json number,author --jq '[.[] | select(.author.login != "dependabot[bot]" and .author.login != "dependabot")] | length' 2>/dev/null)
OPEN_PRS="${OPEN_PRS:-0}"

# If backlog is full, check if any PRs can be acted on
if [ "$OPEN_PRS" -ge 10 ]; then
  # Check if any PR has passed CI (mergeable and not blocked)
  MERGEABLE=$(gh pr list --state open --json number,mergeStateStatus --jq '[.[] | select(.mergeStateStatus == "CLEAN" or .mergeStateStatus == "HAS_HOOKS" or .mergeStateStatus == "UNSTABLE")] | length' 2>/dev/null)
  MERGEABLE="${MERGEABLE:-0}"
  if [ "$MERGEABLE" -eq 0 ]; then
    # All PRs waiting on CI, nothing to do — let Claude stop
    echo '{"systemMessage":"Dev mode: 5+ PRs open, all waiting on CI. Pausing until PRs merge. Re-enable with `touch .claude/.dev-mode` when ready."}'
    exit 0
  fi
fi

# Before blocking, check for conflicting PRs and enable auto-merge.
DIRTY_PRS=""
for PR_JSON in $(gh pr list --state open --json number,author,mergeStateStatus --jq '.[] | select(.author.login != "dependabot[bot]" and .author.login != "dependabot") | @json' 2>/dev/null); do
  PR_NUM=$(echo "$PR_JSON" | jq -r '.number')
  PR_STATUS=$(echo "$PR_JSON" | jq -r '.mergeStateStatus')
  if [ "$PR_STATUS" = "DIRTY" ]; then
    DIRTY_PRS="${DIRTY_PRS}#${PR_NUM} "
  fi
  gh pr merge "$PR_NUM" --auto --squash --delete-branch 2>/dev/null
done

CONFLICT_MSG=""
if [ -n "$DIRTY_PRS" ]; then
  CONFLICT_MSG="\n\n⚠️ CONFLICTING PRs: ${DIRTY_PRS}\nRebase these on origin/main, resolve conflicts, force-push, then enable auto-merge before stopping."
fi

jq -n --arg conflict "$CONFLICT_MSG" '{
  "decision": "block",
  "reason": ("Dev mode is active (.claude/.dev-mode). Continue making progress:\n\n1. Check open PRs — if any passed CI, merge them (--squash --delete-branch)\n2. Read docs/TESTING.md and docs/MIGRATION.md for next action items\n3. Pick the highest-priority item not yet done\n4. Create PRs on separate branches that do not conflict with each other\n5. Do not wait for CI — create the next PR on a new branch while CI runs\n6. Run ./scripts/validate.sh before pushing code changes\n7. Keep PRs small and focused (one concern per PR)" + $conflict + "\n\nTo stop: tell the user to run `rm .claude/.dev-mode`")
}'
