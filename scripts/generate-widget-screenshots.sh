#!/bin/bash
set -euo pipefail
# Generate the Android home-screen WIDGET gallery + the widget picker image.
#
# Strategy (CLAUDE.md § "The Android widget"): ONE fixture (WidgetStagesActivity,
# debug-only, canned readings — no auth, no network, no path to the door), ONE
# script (this file), ONE committed gallery (MobileGarage/screenshots/store/widget/
# + README.md). Glance is neither Compose nor Layoutlib — no @Preview can show
# the widget and no JVM test can see it drawn — so a real emulator render is the
# only way to look at it, exactly as for the Wear tile.
#
# The fixture composes the widget to RemoteViews at a LAUNCHER-SIZED cell for
# each declared size class (GarageWidgetLayout.SIZES are Glance's breakpoints,
# not display sizes — see WidgetStagesActivity), inflates the result, and
# copies exactly that frame plus a margin off the rendered window (PixelCopy,
# so the corner radius survives) into a PNG — no full-screen screencap, so no
# crop arithmetic, no system-bar offset and no dependence on the emulator's
# density.
# This script only launches the stage and pulls the file. Each stage is
# captured in BOTH themes (cmd uimode night no|yes) because the widget's
# colours are day/night providers.
#
# Determinism: the clock is pinned to 10:10 before every capture (needs adb
# root, which the google_apis image allows), so a since-line reads the same on
# every regen. A regen diff therefore means a real visual change.
#
# The `closed_4x1` light capture is ALSO copied to
# androidApp/src/main/res/drawable-nodpi/garage_door_widget_preview.png — the
# android:previewImage older launchers show in the widget picker. It is the same
# reading GarageDoorWidget.providePreview renders for Android 15+, so the picker
# is honest on every version and never hand-drawn.
#
# Usage: ./scripts/generate-widget-screenshots.sh
# Run on demand: whenever a PR visibly changes the widget. Deliberately NOT in
# CI (emulator boot is slow and flaky; regenerate-don't-assert, the PR diff is
# the review surface).
#
# Requires the Android SDK with the emulator package and
# system-images;android-34;google_apis (arm64-v8a on Apple silicon).

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$REPO_ROOT/MobileGarage/screenshots/store/widget"
PREVIEW_DRAWABLE="$REPO_ROOT/MobileGarage/androidApp/src/main/res/drawable-nodpi/garage_door_widget_preview.png"
SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK_DIR/platform-tools/adb"
EMULATOR_BIN="$SDK_DIR/emulator/emulator"
AVDMANAGER="$SDK_DIR/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
AVD_NAME="widget_capture"
AVD_DEVICE="pixel_7"
EMULATOR_PORT=5612
SERIAL="emulator-$EMULATOR_PORT"
PACKAGE="com.chriscartland.garage.debug"
FIXTURE_CLASS="com.chriscartland.garage.debug.WidgetStagesActivity"
FIXTURE_ACTIVITY="$PACKAGE/$FIXTURE_CLASS"
BOOT_TIMEOUT_SECONDS=240
STAGES=(closed_2x1 closed_4x1 open_4x1 stale_4x1 no_signal_4x1)
THEMES=(light dark)
# Where WidgetStagesActivity writes its PNG (getExternalFilesDir(null)).
DEVICE_CAPTURE_DIR="/sdcard/Android/data/$PACKAGE/files"
CAPTURE_TIMEOUT_SECONDS=30

fail() {
    echo "ERROR: $1" >&2
    exit 1
}

[ -x "$ADB" ] || fail "adb not found at $ADB (set ANDROID_HOME?)"
[ -x "$EMULATOR_BIN" ] || fail "emulator not found at $EMULATOR_BIN"

case "$(uname -m)" in
    arm64|aarch64) SYSTEM_IMAGE="system-images;android-34;google_apis;arm64-v8a" ;;
    *) SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64" ;;
esac

if ! "$EMULATOR_BIN" -list-avds | grep -qx "$AVD_NAME"; then
    [ -x "$AVDMANAGER" ] || fail "AVD '$AVD_NAME' missing and avdmanager not found at $AVDMANAGER"
    echo "AVD '$AVD_NAME' not found — creating it..."
    if ! "$SDKMANAGER" --list_installed 2>/dev/null | grep -q "android-34;google_apis;"; then
        echo "Installing $SYSTEM_IMAGE (this may take a while)..."
        yes | "$SDKMANAGER" "$SYSTEM_IMAGE"
    fi
    echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d "$AVD_DEVICE"
fi

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
    EMU_LOG="$(mktemp -t widget-emulator-log)"
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

cleanup_on_failure() {
    if [ "$BOOTED_BY_SCRIPT" = "1" ]; then
        "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    fi
}
trap 'if [ $? -ne 0 ]; then cleanup_on_failure; fi' EXIT

echo "Building :androidApp:assembleDebug..."
"$REPO_ROOT/MobileGarage/gradlew" -p "$REPO_ROOT/MobileGarage" :androidApp:assembleDebug
APK="$(find "$REPO_ROOT/MobileGarage/androidApp/build/outputs/apk/debug" -name '*.apk' | head -1)"
[ -n "$APK" ] || fail "no debug APK found after build"
echo "Installing $(basename "$APK")..."
"$ADB" -s "$SERIAL" install -r -t "$APK" >/dev/null

CLOCK_PINNED=0
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
        echo "WARN: clock pin failed — the since-line will show real time (PNGs churn on regen)."
    fi
else
    echo "WARN: adb root unavailable — the since-line will show real time (PNGs churn on regen)."
fi

# The fixture writes widget-<stage>.png once the inflated views have laid out
# and drawn; a screen that is asleep never lays anything out, so wake it first.
wait_for_capture() {
    device_png="$1"
    waited=0
    while [ "$waited" -lt "$CAPTURE_TIMEOUT_SECONDS" ]; do
        if "$ADB" -s "$SERIAL" shell "test -s '$device_png'" 2>/dev/null; then
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    fail "fixture did not write $device_png within ${CAPTURE_TIMEOUT_SECONDS}s"
}

mkdir -p "$OUT_DIR"
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
for theme in "${THEMES[@]}"; do
    if [ "$theme" = "dark" ]; then night=yes; else night=no; fi
    "$ADB" -s "$SERIAL" shell cmd uimode night "$night" >/dev/null
    for stage in "${STAGES[@]}"; do
        echo "Capturing stage: $stage ($theme)"
        device_png="$DEVICE_CAPTURE_DIR/widget-$stage.png"
        "$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"
        "$ADB" -s "$SERIAL" shell rm -f "$device_png"
        pin_clock
        "$ADB" -s "$SERIAL" shell am start -n "$FIXTURE_ACTIVITY" -e stage "$stage" >/dev/null
        wait_for_capture "$device_png"
        "$ADB" -s "$SERIAL" pull "$device_png" "$OUT_DIR/widget-$stage-$theme.png" >/dev/null
    done
done
"$ADB" -s "$SERIAL" shell cmd uimode night no >/dev/null
"$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"

# --- Sanity: no zero-byte / suspiciously tiny captures ---
for theme in "${THEMES[@]}"; do
    for stage in "${STAGES[@]}"; do
        png="$OUT_DIR/widget-$stage-$theme.png"
        size="$(wc -c < "$png" | tr -d ' ')"
        [ "$size" -gt 1000 ] || fail "capture $png is suspiciously small (${size} bytes)"
    done
done

# --- The picker image: the closed 4x1 capture, uncropped-margin and all ---
mkdir -p "$(dirname "$PREVIEW_DRAWABLE")"
cp "$OUT_DIR/widget-closed_4x1-light.png" "$PREVIEW_DRAWABLE"

# --- Gallery README (this file IS the widget screenshot gallery) ---
GALLERY="$OUT_DIR/README.md"
stage_description() {
    case "$1" in
        closed_2x1) echo "Two cells: the placement default. Headline over the since-line" ;;
        closed_4x1) echo "Four cells: the same words on one row — GarageWidgetLayout's decision for a width at or past 250 dp. Its light capture is also the widget picker's image" ;;
        open_4x1) echo "Open door, confirmed two minutes ago, open eight minutes. Review against stale_4x1" ;;
        stale_4x1) echo "Six hours since the garage last reported: the same open door, drained and dimmed, \"Not confirmed\" in place of a span" ;;
        no_signal_4x1) echo "Nothing known and nothing reachable: \"No signal\", no second line at all" ;;
        *) return 1 ;;
    esac
}
for stage in "${STAGES[@]}"; do
    stage_description "$stage" >/dev/null || fail "no gallery description for stage '$stage' — add one to stage_description()"
done
{
    echo "# Widget screenshots (generated — latest)"
    echo
    echo "Auto-generated by \`./scripts/generate-widget-screenshots.sh\`; do not hand-edit."
    echo "Captured from the debug fixture \`WidgetStagesActivity\` on the"
    echo "\`$AVD_NAME\` emulator ($AVD_DEVICE profile, API 34 Google APIs image) with the"
    echo "clock pinned to 10:10, in both themes. The fixture composes the widget to"
    echo "RemoteViews at a launcher-sized cell for each size class (2x1: 160x100 dp,"
    echo "4x1: 330x100 dp), inflates it, and copies exactly that frame plus a margin"
    echo "off the rendered window, so each capture is what a launcher shows there."
    echo "\`widget-closed_4x1-light.png\` is also copied to"
    echo "\`androidApp/src/main/res/drawable-nodpi/garage_door_widget_preview.png\`,"
    echo "the picker image for launchers older than Android 15."
    echo
    echo "| Stage | Light | Dark | Shows |"
    echo "|---|---|---|---|"
    for stage in "${STAGES[@]}"; do
        echo "| $stage | <img src=\"widget-$stage-light.png\" width=\"220\" alt=\"${stage//_/ } light\"> | <img src=\"widget-$stage-dark.png\" width=\"220\" alt=\"${stage//_/ } dark\"> | $(stage_description "$stage") |"
    done
} > "$GALLERY"

echo ""
echo "Done. Gallery: $GALLERY"
echo "Captured ${#STAGES[@]} stages x ${#THEMES[@]} themes into $OUT_DIR"
echo "Picker image refreshed: $PREVIEW_DRAWABLE"
