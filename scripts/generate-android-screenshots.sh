#!/bin/bash
set -e

# Generate Android reference screenshots sequentially to avoid OOM.
# Each test file runs in its own Gradle invocation.

echo "Cleaning reference screenshots..."
REFERENCE_DIR="AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference"
if [ -d "$REFERENCE_DIR" ]; then
    rm -rf "$REFERENCE_DIR"
fi
mkdir -p "$REFERENCE_DIR"

for file in AndroidGarage/android-screenshot-tests/src/screenshotTest/kotlin/com/chriscartland/garage/screenshottests/*.kt; do
    filename=$(basename "$file" .kt)
    classname="com.chriscartland.garage.screenshottests.${filename}Kt"

    echo "----------------------------------------------------------------"
    echo "Running screenshot generation for $classname"
    echo "----------------------------------------------------------------"

    AndroidGarage/gradlew -p AndroidGarage --no-configuration-cache :android-screenshot-tests:updateDebugScreenshotTest --tests "$classname" -PretainedReferenceScreenshots
done

echo "Generating screenshot gallery..."
./scripts/generate-android-screenshot-gallery.sh

echo "Generating screenshot collections..."
./scripts/generate-screenshot-collections.sh

echo "All screenshot tests completed."
