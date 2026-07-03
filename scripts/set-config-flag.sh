#!/usr/bin/env bash
#
# set-config-flag.sh
#
# Safely read + flip a server feature flag via the `configFlags` Cloud Function.
#
# This is the SAFE alternative to editing Firestore `configCurrent/current` by
# hand or via the whole-doc `serverConfigUpdate` endpoint (which can clobber
# buildTimestamp / secrets / allowlists). The endpoint only ever writes ONE
# allowlisted boolean and preserves every other field. See
# docs/FIREBASE_CONFIG_AUTHORITY.md.
#
# Auth: your Firebase ID token, from the app's "Copy auth token" affordance
# (Android or iOS, signed in with an account in `configFlagAdminAllowedEmails`).
# ID tokens expire in ~1 hour — copy a fresh one each session.
#
# Usage:
#   scripts/set-config-flag.sh --list
#   scripts/set-config-flag.sh <flag> <true|false>
#
# Editable flags:
#   resolvedOnCloseEnabled
#   warningReplaceTagEnabled
#   resolvedNotificationPayloadEnabled
#   snoozeNotificationsEnabled
#   remoteButtonEnabled
#
# Options:
#   --token TOKEN   Firebase ID token (else $GARAGE_ID_TOKEN, else prompt)
#   --project ID    Firebase project id (default: $FCM_PROJECT_ID or "escape-echo")
#   --url URL       full endpoint URL (default: the project's configFlags function)
#   -h, --help      show this help
#
# Examples:
#   scripts/set-config-flag.sh --list
#   scripts/set-config-flag.sh warningReplaceTagEnabled true
#   GARAGE_ID_TOKEN="$(pbpaste)" scripts/set-config-flag.sh resolvedNotificationPayloadEnabled true
#
set -euo pipefail

PROJECT_ID="${FCM_PROJECT_ID:-escape-echo}"
TOKEN="${GARAGE_ID_TOKEN:-}"
URL=""
POSITIONAL=()

# Client-side mirror of the server's EDITABLE_CONFIG_FLAGS (fast feedback; the
# server is the source of truth and re-checks). Keep in sync with
# FirebaseServer/src/functions/http/SetConfigFlag.ts.
EDITABLE_FLAGS=(
    resolvedOnCloseEnabled
    warningReplaceTagEnabled
    resolvedNotificationPayloadEnabled
    snoozeNotificationsEnabled
    remoteButtonEnabled
)

usage() {
    awk 'NR==1 { next } /^#/ { sub(/^#[[:space:]]?/, ""); print; next } { exit }' "$0"
}

is_editable() {
    local candidate=$1 f
    for f in "${EDITABLE_FLAGS[@]}"; do
        [ "$f" = "$candidate" ] && return 0
    done
    return 1
}

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --list)    POSITIONAL+=("--list"); shift ;;
        --token)   TOKEN="${2:?--token needs a value}"; shift 2 ;;
        --project) PROJECT_ID="${2:?--project needs a value}"; shift 2 ;;
        --url)     URL="${2:?--url needs a value}"; shift 2 ;;
        --*) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
        *) POSITIONAL+=("$1"); shift ;;
    esac
done

if [ -z "$URL" ]; then
    URL="https://us-central1-${PROJECT_ID}.cloudfunctions.net/configFlags"
fi

# Determine mode from positionals: none/--list = list; "<flag> <true|false>" = set.
MODE="list"
FLAG=""
VALUE=""
if [ "${#POSITIONAL[@]}" -eq 0 ] || [ "${POSITIONAL[0]:-}" = "--list" ]; then
    MODE="list"
elif [ "${#POSITIONAL[@]}" -eq 2 ]; then
    MODE="set"
    FLAG="${POSITIONAL[0]}"
    VALUE="${POSITIONAL[1]}"
else
    echo "Error: expected '--list' or '<flag> <true|false>'." >&2
    echo >&2
    usage >&2
    exit 1
fi

if [ "$MODE" = "set" ]; then
    if ! is_editable "$FLAG"; then
        echo "Error: '$FLAG' is not an editable flag. Allowed:" >&2
        printf '  %s\n' "${EDITABLE_FLAGS[@]}" >&2
        exit 1
    fi
    case "$VALUE" in
        true|false) ;;
        *) echo "Error: value must be 'true' or 'false' (got: '$VALUE')." >&2; exit 1 ;;
    esac
fi

if [ -z "$TOKEN" ]; then
    echo "Paste your Firebase ID token (from the app's 'Copy auth token'):" >&2
    read -r TOKEN
fi
if [ -z "$TOKEN" ]; then
    echo "Error: no token provided." >&2
    exit 1
fi

pretty() {
    if command -v jq >/dev/null 2>&1; then jq .; else cat; fi
}

if [ "$MODE" = "list" ]; then
    echo "GET $URL" >&2
    curl -sS -X GET "$URL" -H "X-AuthTokenGoogle: ${TOKEN}" | pretty
else
    echo "POST $URL  ->  $FLAG = $VALUE" >&2
    curl -sS -X POST "$URL" \
        -H "X-AuthTokenGoogle: ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"key\":\"${FLAG}\",\"value\":${VALUE}}" | pretty
fi
