#!/bin/bash
# Generate a Markdown gallery of all reference screenshots.

OUTPUT_FILE="AndroidGarage/android-screenshot-tests/SCREENSHOT_GALLERY.md"
REFERENCE_DIR="AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference"

echo "<!-- GENERATED FILE - DO NOT EDIT -->" > "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "# Screenshot Gallery" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "Generated on $(date)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "## Table of Contents" >> "$OUTPUT_FILE"

CONTENT_FILE=$(mktemp)
current_class=""

find "$REFERENCE_DIR" -name "*.png" 2>/dev/null | sort | while read -r filepath; do
    relative_path="${filepath#AndroidGarage/android-screenshot-tests/}"
    parent_dir=$(dirname "$filepath")
    class_name=$(basename "$parent_dir")
    filename=$(basename "$filepath")
    test_name="${filename%.*}"

    if [ "$class_name" != "$current_class" ]; then
        current_class="$class_name"
        echo "" >> "$CONTENT_FILE"
        echo "## $class_name" >> "$CONTENT_FILE"
        echo "- [$class_name](#$(echo "$class_name" | tr '[:upper:]' '[:lower:]'))" >> "$OUTPUT_FILE"
    fi

    echo "" >> "$CONTENT_FILE"
    echo "### $test_name" >> "$CONTENT_FILE"
    echo "<img src=\"$relative_path\" width=\"300\" />" >> "$CONTENT_FILE"
done

echo "" >> "$OUTPUT_FILE"
cat "$CONTENT_FILE" >> "$OUTPUT_FILE"
rm "$CONTENT_FILE"

echo "Screenshot gallery generated at $OUTPUT_FILE"
