#!/bin/bash
# Warn when using command substitution $(...) in Bash commands.
# These are harder to review and can have unexpected results.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
[ -z "$COMMAND" ] && exit 0

# Strip heredoc bodies and quoted strings used for commit messages etc.
STRIPPED=$(echo "$COMMAND" | sed '/<<.*EOF/,/^EOF/d')

# Check for $(...) command substitution outside of simple variable assignments.
# Warn-only: surface a non-blocking note and let the command proceed (do NOT
# return permissionDecision "ask" — that forced a confirmation prompt on every
# $(...), which was pure friction). Normal permission rules still apply.
if echo "$STRIPPED" | grep -qE '\$\('; then
  jq -n '{
    "systemMessage": "Note: command uses $(...) substitution (proceeding without confirmation)."
  }'
fi

exit 0
