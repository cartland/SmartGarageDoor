#!/bin/bash
set -e

# Generate Android reference screenshots sequentially to avoid OOM.
# Each test file runs in its own Gradle invocation.

echo "Cleaning reference screenshots..."
REFERENCE_DIR="MobileGarage/android-screenshot-tests/src/screenshotTestDebug/reference"
if [ -d "$REFERENCE_DIR" ]; then
    rm -rf "$REFERENCE_DIR"
fi
mkdir -p "$REFERENCE_DIR"

for file in MobileGarage/android-screenshot-tests/src/screenshotTest/kotlin/com/chriscartland/garage/screenshottests/*.kt; do
    filename=$(basename "$file" .kt)
    classname="com.chriscartland.garage.screenshottests.${filename}Kt"

    echo "----------------------------------------------------------------"
    echo "Running screenshot generation for $classname"
    echo "----------------------------------------------------------------"

    MobileGarage/gradlew -p MobileGarage --no-configuration-cache :android-screenshot-tests:updateDebugScreenshotTest --tests "$classname" -PretainedReferenceScreenshots
done

echo "Generating screenshot gallery..."
./scripts/generate-android-screenshot-gallery.sh

echo "Generating screenshot collections..."
./scripts/generate-screenshot-collections.sh

echo ""
echo "Framing curated screenshots for README..."
if python3 -c "import PIL" 2>/dev/null; then
    python3 ./scripts/frame-screenshot.py \
        --batch ./scripts/framed-screenshots.txt \
        "$REFERENCE_DIR" \
        MobileGarage/screenshots/framed \
        || echo "WARN: framing step had non-fatal failures"

    echo ""
    echo "Generating Play Store screenshots (committed under screenshots/store/)..."
    # Composes the framed shots + tablet reference renders just produced above.
    # Copying the curated subset into distribution/playstore/ is the only manual
    # step - see the play-store-assets skill.
    python3 ./scripts/generate-store-screenshots.py \
        || echo "WARN: store screenshot generation had non-fatal failures"
else
    echo "WARN: Pillow not installed (pip install Pillow); skipping framing + store steps"
fi

echo ""
echo "Running screenshot health check..."
./scripts/check-screenshot-health.sh

echo "All screenshot tests completed."
