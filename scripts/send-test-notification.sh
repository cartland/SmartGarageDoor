#!/usr/bin/env bash
#
# send-test-notification.sh
#
# Send a test FCM data message to a Test Notification Sandbox topic.
#
# The diagnostic "Test Notification Sandbox" (Function List screen, allowlisted
# users only — see docs/TEST_NOTIFICATION_SANDBOX_PLAN.md) generates a personal
# topic of the form `testNotification-<id>`. Copy it from the app and pass it
# here. The app renders the message as an app-built notification on its own
# channel.
#
# Reuse the SAME --tag across two sends to test inline replace: send a "warning",
# then a "resolved" with the same --tag, and the second replaces the first in
# place (the mechanic the open-door "Resolved" feature will use).
#
# Usage:
#   scripts/send-test-notification.sh <topic> [options]
#
# Required:
#   <topic>          a testNotification-* topic copied from the app
#
# Options:
#   --title TEXT     notification title   (default: "Test notification")
#   --body TEXT      notification body    (default: empty)
#   --tag TAG        notification tag; same tag replaces in place (default: "test")
#   --project ID     Firebase project id  (default: $FCM_PROJECT_ID or "escape-echo")
#   -h, --help       show this help
#
# Auth:
#   Uses `gcloud auth print-access-token`. If it fails, run `gcloud auth login`
#   with an account that can send FCM messages for the project.
#
# Examples:
#   # a "warning"
#   scripts/send-test-notification.sh testNotification-7684a7abdd069826 \
#     --title "Garage door open" --body "Open for 12 minutes" --tag door-1
#
#   # the "resolved" — same --tag, so it replaces the warning in place
#   scripts/send-test-notification.sh testNotification-7684a7abdd069826 \
#     --title "Resolved: garage door closed" \
#     --body "It was open for 14 minutes (2:00-2:14 PM)." --tag door-1
#
set -euo pipefail

PROJECT_ID="${FCM_PROJECT_ID:-escape-echo}"
TITLE="Test notification"
BODY=""
TAG="test"
TOPIC=""

usage() {
    # Print the leading comment block (lines starting with '#') as help.
    sed -n '3,46p' "$0" | sed 's/^#\{0,1\} \{0,1\}//'
}

# Escape a string for embedding inside a JSON double-quoted value. Covers the
# characters that occur in notification copy (backslash, quote, newline, tab, CR).
json_escape() {
    local s=$1
    s=${s//\\/\\\\}
    s=${s//\"/\\\"}
    s=${s//$'\n'/\\n}
    s=${s//$'\t'/\\t}
    s=${s//$'\r'/\\r}
    printf '%s' "$s"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --title)   TITLE="${2:?--title needs a value}"; shift 2 ;;
        --body)    BODY="${2:?--body needs a value}"; shift 2 ;;
        --tag)     TAG="${2:?--tag needs a value}"; shift 2 ;;
        --project) PROJECT_ID="${2:?--project needs a value}"; shift 2 ;;
        --*) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
        *)
            if [ -z "$TOPIC" ]; then
                TOPIC="$1"; shift
            else
                echo "Unexpected argument: $1" >&2; exit 1
            fi
            ;;
    esac
done

if [ -z "$TOPIC" ]; then
    echo "Error: <topic> is required." >&2
    echo >&2
    usage >&2
    exit 1
fi

# Safety: only ever send to a sandbox topic. The app subscribes the device to
# testNotification-* topics only; refusing other prefixes here means a typo can't
# accidentally target a production topic (door_open-* / buttonHealth-*).
case "$TOPIC" in
    testNotification-*) ;;
    *)
        echo "Error: topic must start with 'testNotification-' (got: '$TOPIC')." >&2
        exit 1
        ;;
esac

if ! command -v gcloud >/dev/null 2>&1; then
    echo "Error: gcloud not found. Install the Google Cloud SDK and run 'gcloud auth login'." >&2
    exit 1
fi

TOKEN="$(gcloud auth print-access-token 2>/dev/null || true)"
if [ -z "$TOKEN" ]; then
    echo "Error: could not get an access token. Run 'gcloud auth login'." >&2
    exit 1
fi

PAYLOAD="$(printf \
    '{"message":{"topic":"%s","data":{"title":"%s","body":"%s","tag":"%s"},"android":{"priority":"HIGH"}}}' \
    "$(json_escape "$TOPIC")" \
    "$(json_escape "$TITLE")" \
    "$(json_escape "$BODY")" \
    "$(json_escape "$TAG")")"

echo "Sending to $TOPIC (project: $PROJECT_ID, tag: $TAG)"
RESPONSE="$(curl -s -X POST \
    "https://fcm.googleapis.com/v1/projects/${PROJECT_ID}/messages:send" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD")"

echo "$RESPONSE"

# Success: {"name":"projects/<id>/messages/<n>"}. Anything else is an error.
if printf '%s' "$RESPONSE" | grep -q '"name"'; then
    echo "OK: FCM accepted the message (delivery is best-effort)."
else
    echo "Error: FCM did not accept the message (see the response above)." >&2
    exit 1
fi
