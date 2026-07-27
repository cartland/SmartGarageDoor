#!/bin/bash
set -euo pipefail

# Generate the Wear OS screenshot gallery + Play Store staging assets.
#
# Strategy (docs/WEAR_OS.md § Screenshots): ONE fixture
# (ScreenshotStagesActivity, debug-only, canned states — no auth, no
# network, no path to the real door), ONE script (this file), ONE committed
# gallery (MobileGarage/screenshots/store/wear/ + README.md) that doubles
# as the Play Store staging set. The curated subset that is live in the
# store is copied by hand into MobileGarage/distribution/playstore/wear/ —
# no generator writes into distribution/ (repo rule).
#
# Rendering is a real Wear emulator (AVD wear_capture, 454x454 round,
# API 34 Wear OS image) — NOT Layoutlib — so TimeText, the round mask, and
# animations render for real. Determinism: with the emulator clock re-pinned
# to 10:10 before EVERY capture (best effort; needs adb root, which the
# non-Play system image allows), captures are byte-identical across runs —
# verified empirically by running twice back to back, 12 of 13 stages — so a
# regen diff means a real visual change.
#
# The exceptions are the stages whose door is MID-TRAVEL — `moving` and
# `inferred`, both of which render DoorPosition.OPENING. The door takes 12s to
# travel and the settle is 4s, so the frame caught depends on render latency and
# those PNGs can differ by a few hundred bytes on any regen (they do not always
# differ, which is why an earlier note wrongly blamed `moving` alone).
# Mid-travel IS the state being illustrated, so this is inherent rather than
# fixable by waiting longer (waiting it out would just render `open`). Treat a
# diff confined to those two as noise.
#
# Even the `holding` stage is stable: animateFloatAsState initializes AT its target
# on first composition, so the fixture's isHolding=true renders the ring
# already full (mid-sweep is not capturable from a static fixture; the
# full ring deterministically illustrates "ring filled -> press fires").
#
# Usage: ./scripts/generate-wear-screenshots.sh
# Run on demand: whenever a PR visibly changes the hero screen, and before
# store-asset updates. Deliberately NOT in CI (emulator boot is slow and
# flaky; the repo posture is regenerate-don't-assert with the PR diff as
# the review surface).
#
# Requires the Android SDK with the emulator package and
# system-images;android-34;android-wear (install cmdline-tools 13114758+
# first; older cmdline-tools cannot parse the SDK's XML v4 metadata).

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$REPO_ROOT/MobileGarage/screenshots/store/wear"
SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK_DIR/platform-tools/adb"
EMULATOR_BIN="$SDK_DIR/emulator/emulator"
AVDMANAGER="$SDK_DIR/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

AVD_NAME="wear_capture"
AVD_DEVICE="wearos_large_round"
EMULATOR_PORT=5610
SERIAL="emulator-$EMULATOR_PORT"
PACKAGE="com.chriscartland.garage.debug"
FIXTURE_ACTIVITY="$PACKAGE/com.chriscartland.garage.wear.debug.ScreenshotStagesActivity"
BOOT_TIMEOUT_SECONDS=180

# Stage list mirrors ScreenshotStagesActivity.
STAGES=(
    connecting closed inferred holding submitted moving open signed_out sign_in_error
    # Voice demo (simulated). voice_armed captures its countdown ring already
    # FULL: the settle below (4s) outlasts the cancel window (3s), so the
    # animation has finished by capture time. Deterministic, which is what the
    # fixture needs — mid-sweep would depend on emulator render latency.
    voice_ready voice_listening voice_hearing voice_armed voice_sent voice_refused
)
# Post-foreground settle: lets the system splash ("Starting…") dissolve and
# the first real frame land. The foreground wait below handles slow cold
# starts; this only covers render/splash latency after the activity resumes.
DEFAULT_SETTLE_SECONDS=4
FOREGROUND_TIMEOUT_SECONDS=30

fail() {
    echo "ERROR: $1" >&2
    exit 1
}

[ -x "$ADB" ] || fail "adb not found at $ADB (set ANDROID_HOME?)"
[ -x "$EMULATOR_BIN" ] || fail "emulator not found at $EMULATOR_BIN"

case "$(uname -m)" in
    arm64|aarch64) SYSTEM_IMAGE="system-images;android-34;android-wear;arm64-v8a" ;;
    *) SYSTEM_IMAGE="system-images;android-34;android-wear;x86_64" ;;
esac

# --- AVD: create if missing (recipe is self-contained on purpose) ---
if ! "$EMULATOR_BIN" -list-avds | grep -qx "$AVD_NAME"; then
    [ -x "$AVDMANAGER" ] || fail "AVD '$AVD_NAME' missing and avdmanager not found at $AVDMANAGER"
    echo "AVD '$AVD_NAME' not found — creating it..."
    if ! "$SDKMANAGER" --list_installed 2>/dev/null | grep -q "android-34;android-wear"; then
        echo "Installing $SYSTEM_IMAGE (this may take a while)..."
        yes | "$SDKMANAGER" "$SYSTEM_IMAGE"
    fi
    echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d "$AVD_DEVICE"
fi

# --- Boot, or reuse a running instance of this AVD on ANY port ---
# The emulator refuses to boot the same AVD twice, so a leftover instance
# from a previous session (possibly on a different port) must be reused,
# not raced. `adb emu avd name` maps serial -> AVD name.
find_running_avd_serial() {
    "$ADB" devices | awk '/^emulator-/{print $1}' | while IFS= read -r candidate; do
        if [ "$("$ADB" -s "$candidate" emu avd name 2>/dev/null | head -1 | tr -d '\r')" = "$AVD_NAME" ]; then
            echo "$candidate"
        fi
    done | head -1
}

BOOTED_BY_SCRIPT=0
EXISTING_SERIAL="$(find_running_avd_serial)"
if [ -n "$EXISTING_SERIAL" ]; then
    SERIAL="$EXISTING_SERIAL"
    echo "Reusing already-running $AVD_NAME emulator ($SERIAL)."
else
    echo "Booting $AVD_NAME headless on port $EMULATOR_PORT..."
    EMU_LOG="$(mktemp -t wear-emulator-log)"
    "$EMULATOR_BIN" -avd "$AVD_NAME" -port "$EMULATOR_PORT" \
        -no-window -no-audio -no-boot-anim -no-snapshot >"$EMU_LOG" 2>&1 &
    BOOTED_BY_SCRIPT=1
    waited=0
    until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 5
        waited=$((waited + 5))
        if [ "$waited" -ge "$BOOT_TIMEOUT_SECONDS" ]; then
            echo "--- emulator output (last 20 lines) ---" >&2
            tail -20 "$EMU_LOG" >&2 || true
            fail "emulator did not boot within ${BOOT_TIMEOUT_SECONDS}s"
        fi
    done
    echo "Emulator booted (${waited}s)."
fi

# The emulator is deliberately left running on success: a warm instance is
# both faster and far less flaky for the next regen (cold boots race the
# charging overlay and slow first launches). Kill it manually when done:
#   adb -s <serial> emu kill
cleanup_on_failure() {
    if [ "$BOOTED_BY_SCRIPT" = "1" ]; then
        "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    fi
}
trap 'if [ $? -ne 0 ]; then cleanup_on_failure; fi' EXIT

# --- Build + install the debug APK (fixture ships only in debug) ---
echo "Building :wearApp:assembleDebug..."
"$REPO_ROOT/MobileGarage/gradlew" -p "$REPO_ROOT/MobileGarage" :wearApp:assembleDebug

APK="$(find "$REPO_ROOT/MobileGarage/wearApp/build/outputs/apk/debug" -name '*.apk' | head -1)"
[ -n "$APK" ] || fail "no debug APK found after build"
echo "Installing $(basename "$APK")..."
"$ADB" -s "$SERIAL" install -r -t "$APK" >/dev/null

# --- Pin the clock so TimeText is stable across regens (best effort) ---
# 10:10 is the classic watch marketing time. Needs adb root (non-Play
# image). If it fails, captures still proceed — TimeText just churns.
CLOCK_PINNED=0

# Re-pinned before EVERY capture, not just once up front: a full run is
# comfortably longer than a minute, so a single pin lets the clock roll over
# mid-run and TimeText renders 10:11 on the later stages. That is a real diff
# on regen, which defeats the whole point of pinning. Pinning to :00 seconds
# means the post-pin settle cannot push it into the next minute either.
pin_clock() {
    [ "$CLOCK_PINNED" -eq 1 ] || return 0
    "$ADB" -s "$SERIAL" shell date 010110102026.00 >/dev/null 2>&1 || true
}

if "$ADB" -s "$SERIAL" root >/dev/null 2>&1; then
    "$ADB" -s "$SERIAL" wait-for-device
    "$ADB" -s "$SERIAL" shell settings put global auto_time 0 >/dev/null 2>&1 || true
    if "$ADB" -s "$SERIAL" shell date 010110102026.00 >/dev/null 2>&1; then
        CLOCK_PINNED=1
        echo "Clock pinned to 10:10."
    else
        echo "WARN: clock pin failed — TimeText will show real time (PNGs churn on regen)."
    fi
else
    echo "WARN: adb root unavailable — TimeText will show real time (PNGs churn on regen)."
fi

# --- Capture every stage ---
# A fixed sleep is NOT enough right after a fresh boot, and neither is a
# resumed-ACTIVITY check: both the system launch splash ("Starting…") and the
# charging overlay are WINDOWS drawn over a technically-resumed activity. The
# reliable signal is window FOCUS — `dumpsys window` names the fixture's
# activity class in mCurrentFocus only once its real window is frontmost
# (the splash window is named "Splash Screen <package>", the charging screen
# is a SysUI window). Re-issuing the launch climbs back over any overlay.
FIXTURE_CLASS="com.chriscartland.garage.wear.debug.ScreenshotStagesActivity"

wait_for_fixture_focus() {
    stage_arg="$1"
    fg_waited=0
    while [ "$fg_waited" -lt "$FOREGROUND_TIMEOUT_SECONDS" ]; do
        if "$ADB" -s "$SERIAL" shell dumpsys window 2>/dev/null \
            | grep "mCurrentFocus" \
            | grep -q "$FIXTURE_CLASS"; then
            return 0
        fi
        sleep 1
        fg_waited=$((fg_waited + 1))
        if [ $((fg_waited % 3)) -eq 0 ]; then
            "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
            "$ADB" -s "$SERIAL" shell am start -n "$FIXTURE_ACTIVITY" -e stage "$stage_arg" >/dev/null
        fi
    done
    fail "fixture window did not gain focus for stage '$stage_arg'"
}

mkdir -p "$OUT_DIR"
for stage in "${STAGES[@]}"; do
    echo "Capturing stage: $stage"
    "$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"
    "$ADB" -s "$SERIAL" shell am start -n "$FIXTURE_ACTIVITY" -e stage "$stage" >/dev/null
    wait_for_fixture_focus "$stage"
    pin_clock
    sleep "$DEFAULT_SETTLE_SECONDS"
    "$ADB" -s "$SERIAL" exec-out screencap -p > "$OUT_DIR/wear-$stage.png"
done
"$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"

# --- Sanity: no zero-byte / suspiciously tiny captures ---
for stage in "${STAGES[@]}"; do
    png="$OUT_DIR/wear-$stage.png"
    size="$(wc -c < "$png" | tr -d ' ')"
    [ "$size" -gt 2000 ] || fail "capture $png is suspiciously small (${size} bytes)"
done

# --- Gallery README (this file IS the wear screenshot gallery) ---
GALLERY="$OUT_DIR/README.md"

# One row per STAGES entry, looked up here rather than hand-listed below.
# The table used to be a hardcoded block, which meant adding a stage captured
# the PNG but silently left it out of the gallery — exactly what happened when
# the four voice stages landed. Now an undescribed stage is a hard failure.
stage_description() {
    case "$1" in
        connecting) echo "Cold start, no data yet: \"Connecting…\", no warning badge" ;;
        closed) echo "Closed door (affirmative sensor), \"Hold to open\"" ;;
        inferred) echo "No affirmative sensor, so no prediction: \"Hold to press the remote\"" ;;
        holding) echo "Hold completing: full radial ring, the instant before the press fires" ;;
        submitted) echo "Press sent: ring completes in the sent colour, \"Waiting for the door\"" ;;
        moving) echo "Door sliding open, up arrow" ;;
        open) echo "Open door, \"Hold to close\"" ;;
        signed_out) echo "Signed out: Sign in button (no mic chip — the voice demo is signed-in only)" ;;
        sign_in_error) echo "Transient \"Sign-in failed\" caption" ;;
        voice_listening) echo "Voice demo listening, nothing said yet: pulse rings and the example prompt" ;;
        voice_hearing) echo "Voice demo mid-utterance: rings driven by mic level, prompt replaced by live text" ;;
        voice_ready) echo "Voice demo at rest: \"Simulated\" marker, \"Tap to speak\", demo door Closed" ;;
        voice_armed) echo "Voice demo counting down: the action named conditionally, \"Would open the door\"" ;;
        voice_sent) echo "Voice demo punchline: \"Nothing was sent\"; only the demo door reacts" ;;
        voice_refused) echo "Voice demo gate refusing a command the demo door has outgrown" ;;
        *) return 1 ;;
    esac
}

# Validate before the redirect below, so a missing description fails loudly
# instead of being written into the gallery file.
for stage in "${STAGES[@]}"; do
    stage_description "$stage" >/dev/null \
        || fail "no gallery description for stage '$stage' — add one in stage_description()"
done

{
    echo "# Wear screenshots (generated — latest)"
    echo
    echo "Auto-generated by \`./scripts/generate-wear-screenshots.sh\`; do not hand-edit."
    echo "Captured from the debug fixture \`ScreenshotStagesActivity\` on the"
    echo "\`$AVD_NAME\` emulator ($AVD_DEVICE, 454×454, API 34 Wear OS image) with the"
    echo "clock pinned to 10:10. This directory doubles as Play Store staging; the"
    echo "curated live subset is copied by hand to \`../../../distribution/playstore/wear/\`."
    echo
    echo "Captures are byte-stable across regens **except the mid-travel stages**"
    echo "(\`moving\` and \`inferred\`, both DoorPosition.OPENING), which are caught"
    echo "part-way through 12s of door animation against a 4s settle and can differ"
    echo "by a frame. A diff in any other stage means a real visual change."
    echo
    echo "| Stage | Capture | Shows |"
    echo "|---|---|---|"
    for stage in "${STAGES[@]}"; do
        echo "| $stage | <img src=\"wear-$stage.png\" width=\"180\" alt=\"${stage//_/ }\"> | $(stage_description "$stage") |"
    done
} > "$GALLERY"

echo ""
echo "Done. Gallery: $GALLERY"
echo "Captured ${#STAGES[@]} stages into $OUT_DIR"
echo "Manual step for store updates: copy the curated subset to MobileGarage/distribution/playstore/wear/"
