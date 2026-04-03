#!/bin/bash
# Warn when using shell loops (for/while). These require user permission
# approval which is slower than running each command individually.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
[ -z "$COMMAND" ] && exit 0

# Strip quoted strings to avoid false positives on string content.
STRIPPED=$(echo "$COMMAND" | sed -E "s/'[^']*'//g; s/\"[^\"]*\"//g" | sed '/<<.*EOF/,/^EOF/d')

if echo "$STRIPPED" | grep -qE '\b(for|while|until)\b'; then
  echo '{"systemMessage":"Shell loops (for/while) require user permission which is slower than running each command separately. Consider using multiple Bash tool calls instead."}'
fi

exit 0
