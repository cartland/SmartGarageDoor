#!/bin/bash
set -e

# Generate Android reference screenshots sequentially to avoid OOM.
# Each test file runs in its own Gradle invocation.

echo "Cleaning reference screenshots..."
AndroidGarage/gradlew -p AndroidGarage :android-screenshot-tests:cleanReferenceScreenshots

for file in AndroidGarage/android-screenshot-tests/src/screenshotTest/kotlin/com/chriscartland/garage/screenshottests/*.kt; do
    filename=$(basename "$file" .kt)
    classname="com.chriscartland.garage.screenshottests.${filename}Kt"

    echo "----------------------------------------------------------------"
    echo "Running screenshot generation for $classname"
    echo "----------------------------------------------------------------"

    AndroidGarage/gradlew -p AndroidGarage :android-screenshot-tests:updateDebugScreenshotTest --tests "$classname" -PretainedReferenceScreenshots
done

echo "All screenshot tests completed."
