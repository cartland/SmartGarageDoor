#!/bin/bash
# Stop hook: Check open PR count and guide next action.
# If 5+ PRs open, nudge Claude to focus on merging.
# Otherwise, suggest continuing with next task.

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
[ -z "$REPO_ROOT" ] && exit 0

cd "$REPO_ROOT" || exit 0

# Count open PRs authored by any user (not dependabot)
OPEN_PRS=$(gh pr list --state open --json number,author --jq '[.[] | select(.author.login != "dependabot[bot]" and .author.login != "dependabot")] | length' 2>/dev/null)
[ -z "$OPEN_PRS" ] && exit 0

if [ "$OPEN_PRS" -ge 5 ]; then
  PR_LIST=$(gh pr list --state open --json number,title --jq '.[] | "#\(.number) \(.title)"' 2>/dev/null | head -10)
  jq -n --arg count "$OPEN_PRS" --arg prs "$PR_LIST" '{
    "decision": "block",
    "reason": ("There are " + $count + " open PRs. Focus on merging existing PRs before creating new ones:\n" + $prs)
  }'
elif [ "$OPEN_PRS" -ge 1 ]; then
  PR_LIST=$(gh pr list --state open --json number,title --jq '.[] | "#\(.number) \(.title)"' 2>/dev/null | head -10)
  jq -n --arg count "$OPEN_PRS" --arg prs "$PR_LIST" '{
    "systemMessage": ($count + " open PR(s). Check if any are ready to merge:\n" + $prs)
  }'
fi

exit 0
