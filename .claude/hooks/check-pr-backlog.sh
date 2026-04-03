#!/bin/bash
# Stop hook: Check open PR count and guide next action.
# - 10+ PRs: block — must focus on merging
# - 5-9 PRs: warn — remind to merge, don't block
# - <5 PRs: silent

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
[ -z "$REPO_ROOT" ] && exit 0

cd "$REPO_ROOT" || exit 0

# Count open PRs authored by any user (not dependabot)
OPEN_PRS=$(gh pr list --state open --json number,author --jq '[.[] | select(.author.login != "dependabot[bot]" and .author.login != "dependabot")] | length' 2>/dev/null)
[ -z "$OPEN_PRS" ] && exit 0

PR_LIST=$(gh pr list --state open --json number,title --jq '.[] | "#\(.number) \(.title)"' 2>/dev/null | head -10)

if [ "$OPEN_PRS" -ge 10 ]; then
  jq -n --arg count "$OPEN_PRS" --arg prs "$PR_LIST" '{
    "decision": "block",
    "reason": ("There are " + $count + " open PRs. Focus on merging existing PRs before creating new ones:\n" + $prs)
  }'
elif [ "$OPEN_PRS" -ge 5 ]; then
  jq -n --arg count "$OPEN_PRS" --arg prs "$PR_LIST" '{
    "systemMessage": ($count + " open PRs — consider merging some before creating more:\n" + $prs)
  }'
fi

exit 0
