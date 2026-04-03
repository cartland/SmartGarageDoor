#!/bin/bash
# Block gh commands that use --admin to bypass branch protection.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
[ -z "$COMMAND" ] && exit 0

# Strip quoted strings so we don't match --admin inside PR body text.
STRIPPED=$(echo "$COMMAND" | sed -E "s/'[^']*'//g; s/\"[^\"]*\"//g" | sed '/<<.*EOF/,/^EOF/d')

if echo "$STRIPPED" | grep -qE '\bgh\b.*--admin'; then
  jq -n '{
    "hookSpecificOutput": {
      "hookEventName": "PreToolUse",
      "permissionDecision": "deny",
      "permissionDecisionReason": "BLOCKED: --admin flag bypasses branch protection. Remove --admin and wait for CI to pass."
    }
  }'
  exit 0
fi

exit 0
