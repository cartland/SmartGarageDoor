#!/bin/bash
# Warn when using command substitution $(...) in Bash commands.
# These are harder to review and can have unexpected results.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
[ -z "$COMMAND" ] && exit 0

# Strip heredoc bodies and quoted strings used for commit messages etc.
STRIPPED=$(echo "$COMMAND" | sed '/<<.*EOF/,/^EOF/d')

# Check for $(...) command substitution outside of simple variable assignments
if echo "$STRIPPED" | grep -qE '\$\('; then
  jq -n '{
    "hookSpecificOutput": {
      "hookEventName": "PreToolUse",
      "permissionDecision": "ask",
      "permissionDecisionReason": "Contains command substitution $(...) — review before approving."
    }
  }'
fi

exit 0
