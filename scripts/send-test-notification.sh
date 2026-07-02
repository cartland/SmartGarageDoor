#!/usr/bin/env bash
#
# send-test-notification.sh
#
# Send a test FCM message to a Test Notification Sandbox topic.
#
# The diagnostic "Test Notification Sandbox" (Function List screen, allowlisted
# users only — see docs/TEST_NOTIFICATION_SANDBOX_PLAN.md) generates a personal
# topic of the form `testNotification-<id>`. Copy it from the app and pass it
# here. Sends only ever target that sandbox topic, never a production door topic.
#
# Three message modes let you reproduce the production door notification shapes:
#
#   --mode data          (default) a DATA-ONLY message. The app builds the
#                        notification itself from the data (title/body/tag) on
#                        its sandbox channel. This is what the resolved uses today.
#   --mode notification  an OS-RENDERED notification-payload (notification block +
#                        android.notification.tag). When the app is backgrounded
#                        the OS renders it with no app code — this reproduces the
#                        production open-door WARNING (a notification-payload).
#   --mode combined      a notification+data message carrying the same tag. This
#                        reproduces the relaxed-A "resolved" (see
#                        docs/RESOLVED_NOTIFICATION_NO_COMPROMISE.md §9): OS-renders
#                        in the background, app-built (rich) in the foreground.
#
# Device gate for the relaxed-A single-card design. Run these on a real device,
# with the app BACKGROUNDED (so the OS renders the notification-payload), across
# your target OEMs (at least Pixel + a Samsung/Xiaomi build):
#
#   1. Re-alert on same-tag replace (the A-prime gate — must NOT silently update):
#        send warning A, leave it undismissed, send warning B with the SAME tag,
#        confirm B still heads-up + sounds. If any device silently updates without
#        alerting, do NOT tag the production warning on that OEM (fall back to
#        Phase 2). Uses the production-faithful HIGH / PRIORITY_MAX defaults.
#          scripts/send-test-notification.sh <topic> --mode notification \
#            --tag garage_door --title "Garage door open" --body "Open for 16 minutes"
#          # ...wait, do not dismiss, then:
#          scripts/send-test-notification.sh <topic> --mode notification \
#            --tag garage_door --title "Garage door open" --body "Open for 22 minutes"
#
#   2. Cross-message single-card replace (both OS-rendered, app backgrounded):
#        send a notification warning, then a combined resolved with the SAME tag,
#        confirm they collapse to ONE card and the resolved does not re-buzz.
#          scripts/send-test-notification.sh <topic> --mode notification \
#            --tag garage_door --title "Garage door open" --body "Open for 16 minutes"
#          scripts/send-test-notification.sh <topic> --mode combined \
#            --tag garage_door --title "Resolved: garage door closed" \
#            --body "Was open for 18 minutes" --priority NORMAL \
#            --notification-priority PRIORITY_LOW
#
# Usage:
#   scripts/send-test-notification.sh <topic> [options]
#
# Required:
#   <topic>          a testNotification-<id> topic.
#                    How to get it: open the app signed in with an allowlisted
#                    account, go to the Function List screen, and tap
#                    "Copy test topic" under the Test Notification Sandbox section.
#
# Options:
#   --mode MODE      data | notification | combined   (default: data)
#   --title TEXT     notification title   (default: "Test notification")
#   --body TEXT      notification body    (default: empty)
#   --tag TAG        notification tag; same tag replaces in place (default: "test")
#   --priority P     android message priority: HIGH | NORMAL   (default: HIGH)
#   --notification-priority NP   android.notification.notification_priority:
#                    PRIORITY_MAX | PRIORITY_HIGH | PRIORITY_DEFAULT | PRIORITY_LOW |
#                    PRIORITY_MIN   (default: PRIORITY_MAX; used by notification/combined)
#   --project ID     Firebase project id  (default: $FCM_PROJECT_ID or "escape-echo")
#   -h, --help       show this help
#
# Auth:
#   Uses `gcloud auth print-access-token`. If it fails, run `gcloud auth login`
#   with an account that can send FCM messages for the project.
#
set -euo pipefail

PROJECT_ID="${FCM_PROJECT_ID:-escape-echo}"
MODE="data"
TITLE="Test notification"
BODY=""
TAG="test"
PRIORITY="HIGH"
NOTIFICATION_PRIORITY="PRIORITY_MAX"
TOPIC=""

usage() {
    # Print the leading comment block (every comment line after the shebang,
    # until the first non-comment line) as help. Robust to header edits.
    awk 'NR==1 { next } /^#/ { sub(/^#[[:space:]]?/, ""); print; next } { exit }' "$0"
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
        --mode)    MODE="${2:?--mode needs a value}"; shift 2 ;;
        --title)   TITLE="${2:?--title needs a value}"; shift 2 ;;
        --body)    BODY="${2:?--body needs a value}"; shift 2 ;;
        --tag)     TAG="${2:?--tag needs a value}"; shift 2 ;;
        --priority) PRIORITY="${2:?--priority needs a value}"; shift 2 ;;
        --notification-priority) NOTIFICATION_PRIORITY="${2:?--notification-priority needs a value}"; shift 2 ;;
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
    echo "Error: a <topic> is required." >&2
    echo "  Get it from the app: sign in with an allowlisted account -> Function List" >&2
    echo "  -> Test Notification Sandbox -> 'Copy test topic'. Pass it as the first arg." >&2
    echo >&2
    usage >&2
    exit 1
fi

case "$MODE" in
    data|notification|combined) ;;
    *) echo "Error: --mode must be data | notification | combined (got: '$MODE')." >&2; exit 1 ;;
esac

case "$PRIORITY" in
    HIGH|NORMAL) ;;
    *) echo "Error: --priority must be HIGH | NORMAL (got: '$PRIORITY')." >&2; exit 1 ;;
esac

case "$NOTIFICATION_PRIORITY" in
    PRIORITY_MAX|PRIORITY_HIGH|PRIORITY_DEFAULT|PRIORITY_LOW|PRIORITY_MIN) ;;
    *) echo "Error: --notification-priority must be one of PRIORITY_MAX|PRIORITY_HIGH|PRIORITY_DEFAULT|PRIORITY_LOW|PRIORITY_MIN (got: '$NOTIFICATION_PRIORITY')." >&2; exit 1 ;;
esac

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

ESC_TOPIC="$(json_escape "$TOPIC")"
ESC_TITLE="$(json_escape "$TITLE")"
ESC_BODY="$(json_escape "$BODY")"
ESC_TAG="$(json_escape "$TAG")"

# Reusable JSON fragments.
DATA_BLOCK="$(printf '"data":{"title":"%s","body":"%s","tag":"%s"}' "$ESC_TITLE" "$ESC_BODY" "$ESC_TAG")"
NOTIFICATION_BLOCK="$(printf '"notification":{"title":"%s","body":"%s"}' "$ESC_TITLE" "$ESC_BODY")"
# android.notification.tag is the OS drawer replace-key for an OS-rendered message.
ANDROID_OS_RENDER="$(printf '"android":{"priority":"%s","notification":{"notification_priority":"%s","tag":"%s"}}' "$PRIORITY" "$NOTIFICATION_PRIORITY" "$ESC_TAG")"
ANDROID_DATA_ONLY="$(printf '"android":{"priority":"%s"}' "$PRIORITY")"

case "$MODE" in
    data)
        BODY_JSON="\"topic\":\"${ESC_TOPIC}\",${DATA_BLOCK},${ANDROID_DATA_ONLY}"
        ;;
    notification)
        BODY_JSON="\"topic\":\"${ESC_TOPIC}\",${NOTIFICATION_BLOCK},${ANDROID_OS_RENDER}"
        ;;
    combined)
        BODY_JSON="\"topic\":\"${ESC_TOPIC}\",${NOTIFICATION_BLOCK},${DATA_BLOCK},${ANDROID_OS_RENDER}"
        ;;
esac
PAYLOAD="{\"message\":{${BODY_JSON}}}"

echo "Sending to $TOPIC (project: $PROJECT_ID, mode: $MODE, tag: $TAG, priority: $PRIORITY/$NOTIFICATION_PRIORITY)"
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
