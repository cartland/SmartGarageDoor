#!/usr/bin/env bash
#
# Generates Markdown files from YAML screenshot collection declarations.
#
# For each .yaml file in android-screenshot-tests/collections/, this script:
# 1. Deletes the corresponding .md file (clean slate)
# 2. Reads the YAML to extract title, test_class, and screenshot entries
# 3. Finds matching reference PNGs (by stable test name prefix)
# 4. Generates a Markdown file with metadata, descriptions, and inline images
#
# Usage: ./scripts/generate-screenshot-collections.sh
#
# Requires: No external YAML parser — uses grep/sed since the format is simple.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
COLLECTIONS_DIR="$REPO_ROOT/AndroidGarage/android-screenshot-tests/collections"
REFERENCE_DIR="$REPO_ROOT/AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference/com/chriscartland/garage/screenshottests"

if [ ! -d "$COLLECTIONS_DIR" ]; then
    echo "No collections directory found at $COLLECTIONS_DIR"
    exit 0
fi

# Delete all generated .md files in collections/
find "$COLLECTIONS_DIR" -name "*.md" -delete

yaml_count=0
error_count=0

for yaml_file in "$COLLECTIONS_DIR"/*.yaml; do
    [ -f "$yaml_file" ] || continue
    yaml_count=$((yaml_count + 1))

    yaml_basename="$(basename "$yaml_file" .yaml)"
    md_file="$COLLECTIONS_DIR/$yaml_basename.md"
    yaml_relpath="android-screenshot-tests/collections/$(basename "$yaml_file")"

    # Parse title
    title="$(grep '^title:' "$yaml_file" | sed 's/^title: *"\{0,1\}\(.*\)"\{0,1\}$/\1/' | sed 's/"$//')"

    # Parse test_class
    test_class="$(grep '^test_class:' "$yaml_file" | sed 's/^test_class: *//')"

    if [ -z "$title" ] || [ -z "$test_class" ]; then
        echo "ERROR: $yaml_file missing title or test_class"
        error_count=$((error_count + 1))
        continue
    fi

    class_dir="$REFERENCE_DIR/$test_class"
    if [ ! -d "$class_dir" ]; then
        echo "WARNING: Reference directory not found: $class_dir"
    fi

    # Extract screenshot entries (test + description pairs)
    # Simple line-by-line parser for the flat YAML structure
    tests=()
    descriptions=()
    while IFS= read -r line; do
        if [[ "$line" =~ ^[[:space:]]*-[[:space:]]*test:[[:space:]]*(.*) ]]; then
            tests+=("${BASH_REMATCH[1]}")
        elif [[ "$line" =~ ^[[:space:]]*description:[[:space:]]*\"(.*)\" ]]; then
            descriptions+=("${BASH_REMATCH[1]}")
        elif [[ "$line" =~ ^[[:space:]]*description:[[:space:]]*(.*) ]]; then
            descriptions+=("${BASH_REMATCH[1]}")
        fi
    done < "$yaml_file"

    if [ "${#tests[@]}" -ne "${#descriptions[@]}" ]; then
        echo "ERROR: $yaml_file has ${#tests[@]} tests but ${#descriptions[@]} descriptions"
        error_count=$((error_count + 1))
        continue
    fi

    # Find matching PNG for each test name
    image_paths=()
    missing=0
    for test_name in "${tests[@]}"; do
        # Glob for {test_name}_*_0.png (the hash varies)
        matches=("$class_dir"/${test_name}_*_0.png)
        if [ -f "${matches[0]}" ]; then
            # Store path relative to collections/ dir for Markdown links
            rel_path="$(python3 -c "import os.path; print(os.path.relpath('${matches[0]}', '$COLLECTIONS_DIR'))")"
            image_paths+=("$rel_path")
        else
            echo "WARNING: No image found for $test_name in $class_dir"
            image_paths+=("")
            missing=$((missing + 1))
        fi
    done

    # Generate Markdown
    {
        echo "<!-- GENERATED FILE — DO NOT EDIT -->"
        echo "<!-- Source: $yaml_relpath -->"
        echo ""
        echo "# $title"
        echo ""

        # Numbered description list
        for i in "${!descriptions[@]}"; do
            echo "$((i + 1)). ${descriptions[$i]}"
        done
        echo ""

        # Images in a single paragraph so they flow/wrap naturally
        img_line=""
        for i in "${!image_paths[@]}"; do
            path="${image_paths[$i]}"
            desc="${descriptions[$i]}"
            if [ -n "$path" ]; then
                img_line+="<img src=\"$path\" alt=\"$desc\" width=\"200\" /> "
            fi
        done
        echo "$img_line"
        echo ""
    } > "$md_file"

    echo "Generated $md_file (${#tests[@]} images, $missing missing)"
done

if [ "$error_count" -gt 0 ]; then
    echo ""
    echo "ERRORS: $error_count collection(s) failed"
    exit 1
fi

echo ""
echo "Screenshot collections generated: $yaml_count collection(s)"
