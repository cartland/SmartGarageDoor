#!/bin/bash
# Block Read on images whose long edge exceeds the limit.
#
# Why: sessions with many images reject any image >2000px on the long
# edge with InputValidationError, which kills the workflow mid-task.
# Scripts may legitimately generate oversized images (e.g. framed
# screenshots) that we still need to commit — so this only blocks Read,
# not Write/Edit/Bash. Resize before reading:
#   sips -Z 2000 path/to/image.png --out /tmp/preview.png

INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
[ "$TOOL" != "Read" ] && exit 0

FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
[ -z "$FILE" ] && exit 0

# Only check raster image extensions sips can measure.
case "$FILE" in
  *.png|*.PNG|*.jpg|*.JPG|*.jpeg|*.JPEG|*.gif|*.GIF|*.webp|*.WEBP|*.heic|*.HEIC) ;;
  *) exit 0 ;;
esac

[ -f "$FILE" ] || exit 0

LIMIT=2000

# sips outputs lines like "  pixelWidth: 1294" — parse both dims in one call.
DIMS=$(sips -g pixelWidth -g pixelHeight "$FILE" 2>/dev/null \
  | awk '/pixelWidth/{w=$2} /pixelHeight/{h=$2} END{if(w&&h) print w" "h}')
[ -z "$DIMS" ] && exit 0

W=$(echo "$DIMS" | cut -d' ' -f1)
H=$(echo "$DIMS" | cut -d' ' -f2)
LONG=$W
[ "$H" -gt "$W" ] && LONG=$H

if [ "$LONG" -gt "$LIMIT" ]; then
  REASON="BLOCKED: image is ${W}x${H}; long edge ${LONG}px exceeds ${LIMIT}px limit. Many-image sessions reject this with InputValidationError. Resize first: sips -Z ${LIMIT} '${FILE}' --out /tmp/preview.png  (then Read /tmp/preview.png)"
  jq -n --arg reason "$REASON" '{
    "hookSpecificOutput": {
      "hookEventName": "PreToolUse",
      "permissionDecision": "deny",
      "permissionDecisionReason": $reason
    }
  }'
  exit 0
fi

exit 0
